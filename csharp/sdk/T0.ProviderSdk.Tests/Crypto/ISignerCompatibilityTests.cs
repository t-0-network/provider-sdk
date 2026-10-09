using System.Collections.Concurrent;
using Grpc.Core;
using Grpc.Net.Client;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;
using T0.ProviderSdk.Provider;
using PaymentApi = T0.ProviderSdk.Api.Tzero.V1.Payment;
using PaymentIntentApi = T0.ProviderSdk.Api.Tzero.V1.PaymentIntent.Provider;

namespace T0.ProviderSdk.Tests.Crypto;

/// <summary>
/// Every client factory and <see cref="SigningDelegatingHandler"/> take a <see cref="Signer"/>, a
/// <see cref="SignFn"/> lambda and, for code written against v1.2, the obsolete <c>ISigner</c>; each
/// signs requests the SDK's verifier accepts. The test project makes the use of an obsolete member
/// an error, so the Signer and lambda calls here also show that they pick no ISigner overload.
/// </summary>
public class ISignerCompatibilityTests
{
    private const string Key = "6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string Unreachable = "http://127.0.0.1:1";

    [Fact]
    public async Task Signer_IsTakenByEveryFactory()
    {
        var signer = Signer.FromHex(Key);
        await using var server = await VerifyingServer.StartAsync(signer.GetPublicKeyHexPrefixed());

        await AssertEachCallSucceedsAsync(server, CallsWith(server.BaseUrl, signer));
    }

    [Fact]
    public async Task SignFnLambda_IsTakenByEveryFactory()
    {
        var signer = Signer.FromHex(Key);
        await using var server = await VerifyingServer.StartAsync(signer.GetPublicKeyHexPrefixed());

        await AssertEachCallSucceedsAsync(server, CallsWithLambda(server.BaseUrl, signer));
    }

#pragma warning disable CS0618 // The v1.2 ISigner path, kept obsolete.

    [Fact]
    public async Task ISignerVariable_IsTakenByEveryFactory()
    {
        ISigner signer = Signer.FromHex(Key);
        await using var server = await VerifyingServer.StartAsync(signer.GetPublicKeyHexPrefixed());

        await AssertEachCallSucceedsAsync(server, CallsWith(server.BaseUrl, signer));
    }

    [Fact]
    public async Task CustomISigner_IsTakenByEveryFactory()
    {
        var signer = new CustomSigner(Signer.FromHex(Key));
        await using var server = await VerifyingServer.StartAsync(signer.GetPublicKeyHexPrefixed());

        await AssertEachCallSucceedsAsync(server, CallsWith(server.BaseUrl, signer));
        Assert.True(signer.Calls >= 6);
    }

    // As for a SignFn: Internal, with the signer's error as the cause, and nothing is sent. A null
    // result (what an unconfigured mock returns) is a missing signature.
    [Fact]
    public async Task ISignerFailure_IsInternal_WithItsCause()
    {
        var cases = new (ISigner Signer, string Cause)[]
        {
            (new FailingSigner(() => throw new InvalidOperationException("signer is offline")), "signer is offline"),
            (new FailingSigner(() => null!), "signature must be 64 or 65 bytes"),
        };

        foreach (var (signer, cause) in cases)
        {
            foreach (var (factory, call) in CallsWith(Unreachable, signer))
            {
                var ex = await Assert.ThrowsAsync<RpcException>(call);
                Assert.Equal(
                    $"{factory}: {StatusCode.Internal} signing the request failed: {cause}",
                    $"{factory}: {ex.StatusCode} {ex.Status.Detail}");
            }
        }
    }

    [Fact]
    public void NullISigner_IsRefusedByEveryOverload()
    {
        ISigner? signer = null;
        var options = new NetworkClientOptions { BaseUrl = Unreachable };
        var creates = new (string Factory, Action Create)[]
        {
            ("Create", () => NetworkClient.Create(options, signer!, invoker => invoker)),
            ("CreateNetworkServiceClient(options)", () => NetworkClient.CreateNetworkServiceClient(options, signer!)),
            ("CreateNetworkServiceClient(baseUrl)", () => NetworkClient.CreateNetworkServiceClient(Unreachable, signer!)),
            ("CreatePaymentIntentNetworkServiceClient(options)",
                () => NetworkClient.CreatePaymentIntentNetworkServiceClient(options, signer!)),
            ("CreatePaymentIntentNetworkServiceClient(baseUrl)",
                () => NetworkClient.CreatePaymentIntentNetworkServiceClient(Unreachable, signer!)),
            ("SigningDelegatingHandler", () => new SigningDelegatingHandler(signer!)),
        };

        foreach (var (factory, create) in creates)
        {
            var ex = Assert.Throws<ArgumentNullException>(create);
            Assert.Equal($"{factory}: signer must not be null", $"{factory}: {ex.Message}");
        }
    }

    // A handler written against v1.2 injects the server's signer as ISigner.
    [Fact]
    public void Server_RegistersItsSigner_AsISigner()
    {
        var signer = Signer.FromHex(Key);
        var config = new T0Config { ProviderPrivateKey = "", NetworkPublicKey = signer.GetPublicKeyHexPrefixed() };
        var server = new T0ProviderServer(config, signer);

        using var services = server.Services.BuildServiceProvider();

        Assert.Same(signer, services.GetRequiredService<Signer>());
        Assert.Same(signer, services.GetRequiredService<ISigner>());
        Assert.Same(signer, ActivatorUtilities.CreateInstance<HandlerTakingISigner>(services).Signer);
    }

    private sealed class HandlerTakingISigner(ISigner signer) : PaymentApi.ProviderService.ProviderServiceBase
    {
        public ISigner Signer { get; } = signer;
    }

    private sealed class FailingSigner(Func<SignResult> sign) : ISigner
    {
        public SignResult Sign(byte[] digest) => sign();
        public byte[] GetPublicKey() => throw new NotSupportedException();
        public string GetPublicKeyHex() => throw new NotSupportedException();
        public string GetPublicKeyHexPrefixed() => throw new NotSupportedException();
    }

    private static IEnumerable<(string Factory, Func<Task> Call)> CallsWith(string baseUrl, ISigner signer)
    {
        var options = new NetworkClientOptions { BaseUrl = baseUrl };
        yield return ("Create", () => NetworkClient.Create(
            options, signer, invoker => new PaymentApi.NetworkService.NetworkServiceClient(invoker)).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(options)",
            () => NetworkClient.CreateNetworkServiceClient(options, signer).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreateNetworkServiceClient(baseUrl, signer).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(options)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(options, signer).ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(baseUrl, signer).ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("SigningDelegatingHandler", () => CallThroughAsync(baseUrl, new SigningDelegatingHandler(signer)));
    }

#pragma warning restore CS0618

    private static IEnumerable<(string Factory, Func<Task> Call)> CallsWith(string baseUrl, Signer signer)
    {
        var options = new NetworkClientOptions { BaseUrl = baseUrl };
        yield return ("Create", () => NetworkClient.Create(
            options, signer, invoker => new PaymentApi.NetworkService.NetworkServiceClient(invoker)).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(options)",
            () => NetworkClient.CreateNetworkServiceClient(options, signer).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreateNetworkServiceClient(baseUrl, signer).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(options)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(options, signer).ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(baseUrl, signer).ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("SigningDelegatingHandler", () => CallThroughAsync(baseUrl, new SigningDelegatingHandler(signer)));
    }

    private static IEnumerable<(string Factory, Func<Task> Call)> CallsWithLambda(string baseUrl, Signer signer)
    {
        (byte[], byte[]) Sign(byte[] digest)
        {
            var result = signer.Sign(digest);
            return (result.Signature, result.PublicKey);
        }

        var options = new NetworkClientOptions { BaseUrl = baseUrl };
        yield return ("Create", () => NetworkClient.Create(
            options, digest => Sign(digest), invoker => new PaymentApi.NetworkService.NetworkServiceClient(invoker))
            .UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(options)",
            () => NetworkClient.CreateNetworkServiceClient(options, digest => Sign(digest)).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreateNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreateNetworkServiceClient(baseUrl, digest => Sign(digest)).UpdateQuoteAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(options)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(options, digest => Sign(digest))
                .ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("CreatePaymentIntentNetworkServiceClient(baseUrl)",
            () => NetworkClient.CreatePaymentIntentNetworkServiceClient(baseUrl, digest => Sign(digest))
                .ConfirmPaymentAsync(new()).ResponseAsync);
        yield return ("SigningDelegatingHandler",
            () => CallThroughAsync(baseUrl, new SigningDelegatingHandler(digest => Sign(digest))));
    }

    private static async Task CallThroughAsync(string baseUrl, SigningDelegatingHandler handler)
    {
        handler.InnerHandler = new SocketsHttpHandler();
        using var channel = GrpcChannel.ForAddress(baseUrl, new GrpcChannelOptions
        {
            HttpClient = new HttpClient(handler),
            DisposeHttpClient = true,
        });
        await new PaymentApi.NetworkService.NetworkServiceClient(channel).UpdateQuoteAsync(new()).ResponseAsync;
    }

    private static async Task AssertEachCallSucceedsAsync(
        VerifyingServer server, IEnumerable<(string Factory, Func<Task> Call)> calls)
    {
        var factories = new List<string>();
        foreach (var (factory, call) in calls)
        {
            await call();
            factories.Add(factory);
        }

        Assert.Equal(6, factories.Count);
        Assert.Equal(factories.Count, server.Verified);
    }

    /// <summary>
    /// HTTP/2 cleartext server that verifies each request with the SDK's middleware and answers a
    /// verified unary call with an empty message.
    /// </summary>
    private sealed class VerifyingServer : IAsyncDisposable
    {
        private readonly WebApplication _app;
        private int _verified;

        private VerifyingServer(WebApplication app, string baseUrl)
        {
            _app = app;
            BaseUrl = baseUrl;
        }

        public string BaseUrl { get; }

        public int Verified => Volatile.Read(ref _verified);

        public static async Task<VerifyingServer> StartAsync(string publicKeyHex)
        {
            var port = TestPorts.FindFreePort();
            var builder = WebApplication.CreateBuilder();
            builder.WebHost.ConfigureKestrel(options =>
                options.ListenLocalhost(port, listenOptions => listenOptions.Protocols = HttpProtocols.Http2));
            var app = builder.Build();
            var server = new VerifyingServer(app, $"http://127.0.0.1:{port}");
            app.UseMiddleware<SignatureVerificationMiddleware>(
                new ProviderServerOptions { NetworkPublicKeyHex = publicKeyHex });
            app.Run(async context =>
            {
                Interlocked.Increment(ref server._verified);
                context.Response.ContentType = "application/grpc";
                await context.Response.Body.WriteAsync(new byte[5]);
                context.Response.AppendTrailer("grpc-status", "0");
            });

            try
            {
                await app.StartAsync();
                await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
                return server;
            }
            catch
            {
                await app.DisposeAsync();
                throw;
            }
        }

        public async ValueTask DisposeAsync()
        {
            await _app.StopAsync();
            await _app.DisposeAsync();
        }
    }
}

#pragma warning disable CS0618 // A custom signer as written against v1.2.
/// <summary>
/// A custom <c>ISigner</c> as written against v1.2, such as one in front of an HSM: it signs with
/// the key it wraps and returns r‖s without v, the 64-byte form a custom signer may return.
/// </summary>
internal sealed class CustomSigner(Signer key) : ISigner
{
    private int _calls;

    public int Calls => Volatile.Read(ref _calls);

    public SignResult Sign(byte[] digest)
    {
        Interlocked.Increment(ref _calls);
        var result = key.Sign(digest);
        return new SignResult(result.Signature[..64], result.PublicKey);
    }

    public byte[] GetPublicKey() => key.GetPublicKey();

    public string GetPublicKeyHex() => key.GetPublicKeyHex();

    public string GetPublicKeyHexPrefixed() => key.GetPublicKeyHexPrefixed();
}
#pragma warning restore CS0618
