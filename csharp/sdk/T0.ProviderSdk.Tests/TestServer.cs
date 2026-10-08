using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;

namespace T0.ProviderSdk.Tests;

internal static class TestServer
{
    /// <summary>
    /// Runs server until body is done, starting body once readyPort accepts connections. A server
    /// that fails to start fails the test with its own error.
    /// </summary>
    public static async Task RunAsync(T0ProviderServer server, int readyPort, Func<Task> body)
    {
        using var stop = new CancellationTokenSource(TimeSpan.FromSeconds(30));
        var run = server.RunAsync(stop.Token);
        try
        {
            var ready = TestPorts.WaitForPortAsync(readyPort, TimeSpan.FromSeconds(10));
            if (await Task.WhenAny(ready, run) == run)
                await run;
            await ready;
            await body();
        }
        finally
        {
            stop.Cancel();
            try { await run; }
            catch (OperationCanceledException) { }
        }
    }

    /// <summary>
    /// HTTP/2 cleartext server on a free port, answering every request with handler.
    /// </summary>
    public static async Task<(WebApplication App, string BaseUrl)> StartHttp2Async(RequestDelegate handler)
    {
        var port = TestPorts.FindFreePort();
        var builder = WebApplication.CreateBuilder();
        builder.WebHost.ConfigureKestrel(options =>
            options.ListenLocalhost(port, listenOptions => listenOptions.Protocols = HttpProtocols.Http2));
        var app = builder.Build();
        app.Run(handler);

        try
        {
            await app.StartAsync();
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            return (app, $"http://127.0.0.1:{port}");
        }
        catch
        {
            await app.DisposeAsync();
            throw;
        }
    }
}
