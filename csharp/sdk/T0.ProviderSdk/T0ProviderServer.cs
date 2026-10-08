using Grpc.Core.Interceptors;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using T0.ProviderSdk.Common;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Provider;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;
using Grpc.Health.V1;

namespace T0.ProviderSdk;

/// <summary>
/// Builder for a T-0 provider server. Encapsulates ASP.NET Core setup,
/// gRPC registration, and signature verification middleware.
/// </summary>
public sealed class T0ProviderServer
{
    private readonly WebApplicationBuilder _builder;
    private readonly T0Config _config;
    private readonly List<Action<WebApplication>> _mapActions = [];
    private readonly List<string> _registeredFqns = [];
    private string? _sdkVersion;
    private long _maxBodySize = ProviderServerOptions.DefaultMaxBodySize;

    public T0ProviderServer(T0Config config, Signer signer, string[]? args = null)
    {
        if (config is null)
            throw new ArgumentNullException(null, Messages.ArgumentNull("config"));
        if (signer is null)
            throw new ArgumentNullException(null, Messages.SignerNull);
        if (config.Port is < 0 or > 65535)
            throw new ArgumentException(Messages.PortNotValid);
        // Fails here, before anything is built, for a missing or malformed key.
        SignatureVerificationMiddleware.ParseNetworkPublicKey(config.NetworkPublicKey);

        _config = config;
        _builder = WebApplication.CreateBuilder(args ?? []);
        // config.Port on every interface: "*" binds IPv6 and IPv4 (IPv4 alone where there is no
        // IPv6). It is an address, not a Kestrel endpoint, so an application's Kestrel:Endpoints
        // configuration replaces it instead of being bound next to it.
        _builder.WebHost.UseUrls($"http://*:{config.Port}");
        // Every endpoint speaks HTTP/2, the SDK's address (without TLS) and configured ones alike,
        // unless an endpoint sets its own Kestrel:Endpoints:<name>:Protocols. The body limit is the
        // SDK's (SignatureVerificationMiddleware), so Kestrel's own is off.
        _builder.WebHost.ConfigureKestrel(kestrel =>
        {
            kestrel.ConfigureEndpointDefaults(listen => listen.Protocols = HttpProtocols.Http2);
            kestrel.Limits.MaxRequestBodySize = null;
        });
        _builder.Services.AddGrpc(options =>
        {
            options.Interceptors.Add<ValidationInterceptor>();
        });
        _builder.Services.Configure<Grpc.AspNetCore.Server.GrpcServiceOptions>(
            options => options.MaxReceiveMessageSize = (int)Math.Min(int.MaxValue, _maxBodySize));
        _builder.Services.AddSingleton(signer);
#pragma warning disable CS0618 // Handlers written against v1.2 inject the signer as ISigner.
        _builder.Services.AddSingleton<ISigner>(signer);
#pragma warning restore CS0618
    }

    // For tests: the services the server is built with.
    internal IServiceCollection Services => _builder.Services;

    /// <summary>
    /// Maps a Payment ProviderService handler and registers its NetworkServiceClient for DI.
    /// </summary>
    public T0ProviderServer MapPaymentService<THandler>(
        PaymentApi.NetworkService.NetworkServiceClient networkClient)
        where THandler : PaymentApi.ProviderService.ProviderServiceBase
    {
        _registeredFqns.Add(PaymentApi.ProviderService.Descriptor.FullName);
        _builder.Services.AddSingleton(networkClient);
        _mapActions.Add(app => app.MapGrpcService<THandler>());
        return this;
    }

    /// <summary>
    /// Maps a PaymentIntent ProviderService handler and registers its NetworkServiceClient for DI.
    /// </summary>
    public T0ProviderServer MapPaymentIntentService<THandler>(
        PaymentIntentApi.NetworkService.NetworkServiceClient networkClient)
        where THandler : PaymentIntentApi.ProviderService.ProviderServiceBase
    {
        _registeredFqns.Add(PaymentIntentApi.ProviderService.Descriptor.FullName);
        _builder.Services.AddSingleton(networkClient);
        _mapActions.Add(app => app.MapGrpcService<THandler>());
        return this;
    }

    /// <summary>
    /// Registers a hosted service (e.g. QuotePublisher) that runs in the background.
    /// </summary>
    public T0ProviderServer AddHostedService<TService>() where TService : class, IHostedService
    {
        _builder.Services.AddHostedService<TService>();
        return this;
    }

    /// <summary>
    /// Sets the largest request body accepted, in bytes: the whole HTTP body of a unary call, its
    /// gRPC prefix included. <see cref="ProviderServerOptions.DefaultMaxBodySize"/> unless set; a
    /// value of 0 or less is ignored, as in Go. A larger body is refused with ResourceExhausted.
    /// </summary>
    public T0ProviderServer WithMaxBodySize(long bytes)
    {
        if (bytes > 0)
            _maxBodySize = bytes;
        return this;
    }

    // For tests: the body limit the server is built with.
    internal long MaxBodySize => _maxBodySize;

    /// <summary>
    /// Overrides the SDK version reported in health-check response headers.
    /// Wrapping SDKs use this to stamp their own version instead of the
    /// provider-sdk's built-in version. A null, empty or whitespace value is ignored.
    /// </summary>
    public T0ProviderServer WithSdkVersion(string version)
    {
        if (!string.IsNullOrWhiteSpace(version))
            _sdkVersion = version;
        return this;
    }

    /// <summary>
    /// Builds and runs the provider server. Blocks until <paramref name="cancellationToken"/>
    /// fires or the host shuts down.
    /// </summary>
    public async Task RunAsync(CancellationToken cancellationToken = default)
    {
        // Health is the only service this transport mounts on its own — see
        // docs/HEALTH_SERVICE.md. It sits behind the same
        // SignatureVerificationMiddleware, so the probe is signed like any other
        // call.
        var fqns = new List<string>(_registeredFqns)
        {
            Health.Descriptor.FullName,
        };
        _builder.Services.AddSingleton(new HealthServiceImpl(fqns, _sdkVersion));
        // Registered, so gRPC takes this instance instead of creating one per call.
        _builder.Services.AddSingleton(services => new ValidationInterceptor(
            services.GetRequiredService<ILogger<ValidationInterceptor>>(), _sdkVersion));

        var app = _builder.Build();

        app.UseMiddleware<SignatureVerificationMiddleware>(
            new ProviderServerOptions { NetworkPublicKeyHex = _config.NetworkPublicKey, MaxBodySize = _maxBodySize });

        foreach (var mapAction in _mapActions)
            mapAction(app);

        app.MapGrpcService<HealthServiceImpl>();

        await app.RunAsync(cancellationToken);
    }
}
