using Microsoft.Extensions.Hosting;

namespace MyProvider;

// Prints the listening line once the server has bound, and records that it started.
public class Listening(IHostApplicationLifetime lifetime) : IHostedService
{
    private static readonly TaskCompletionSource StartedSource =
        new(TaskCreationOptions.RunContinuationsAsynchronously);

    /// <summary>Set from Program before the host builds this service.</summary>
    public static int Port { get; set; }

    /// <summary>Completes once the server has bound. A failed bind never completes it.</summary>
    public static Task Started => StartedSource.Task;

    private CancellationTokenRegistration _started;

    public Task StartAsync(CancellationToken cancellationToken)
    {
        // ApplicationStarted fires after the bind.
        _started = lifetime.ApplicationStarted.Register(() =>
        {
            Console.WriteLine($"Step 1.1: Provider server initialized on port {Port}");
            StartedSource.TrySetResult();
        });
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken)
    {
        _started.Dispose();
        return Task.CompletedTask;
    }
}
