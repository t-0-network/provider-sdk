using Grpc.Core.Interceptors;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Provider;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;
using SystemApi = T0.ProviderSdk.Api.Tzero.V1.System;

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

    public T0ProviderServer(T0Config config, Signer signer, string[]? args = null)
    {
        ArgumentNullException.ThrowIfNull(config);
        ArgumentNullException.ThrowIfNull(signer);

        _config = config;
        _builder = WebApplication.CreateBuilder(args ?? []);
        _builder.WebHost.UseUrls($"http://0.0.0.0:{config.Port}");
        _builder.Services.AddGrpc(options =>
        {
            options.Interceptors.Add<ValidationInterceptor>();
        });
        _builder.Services.AddSingleton<ISigner>(signer);
        _builder.Services.AddSingleton(signer);
    }

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
    /// Builds and runs the provider server. Blocks until <paramref name="cancellationToken"/>
    /// fires or the host shuts down.
    /// </summary>
    public async Task RunAsync(CancellationToken cancellationToken = default)
    {
        var fqns = new List<string>(_registeredFqns)
        {
            SystemApi.SystemService.Descriptor.FullName,
        };
        _builder.Services.AddSingleton(new SystemServiceImpl(fqns));

        var app = _builder.Build();

        app.UseMiddleware<SignatureVerificationMiddleware>(
            new ProviderServerOptions { NetworkPublicKeyHex = _config.NetworkPublicKey });

        foreach (var mapAction in _mapActions)
            mapAction(app);

        app.MapGrpcService<SystemServiceImpl>();

        await app.RunAsync(cancellationToken);
    }
}
