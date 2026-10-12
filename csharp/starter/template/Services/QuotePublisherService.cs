using Microsoft.Extensions.Hosting;

namespace MyProvider.Services;

/// <summary>
/// Base class for periodic quote publishing as an ASP.NET Core hosted service.
/// Subclass and override <see cref="PublishQuotesAsync"/> with your publishing logic.
/// </summary>
/// <param name="interval">How often to publish quotes (recommended: 5 seconds).</param>
public abstract class QuotePublisherService(TimeSpan interval) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        try
        {
            // The first publish waits for the server to bind. Then one runs on every tick.
            await Listening.Started.WaitAsync(stoppingToken);
            using var timer = new PeriodicTimer(interval);
            do
            {
                try
                {
                    await PublishQuotesAsync(stoppingToken);
                }
                catch (Exception ex)
                {
                    OnError(ex);
                }
            }
            while (await timer.WaitForNextTickAsync(stoppingToken));
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Stopping, or the server never started. Neither is a failure of this service.
        }
    }

    /// <summary>
    /// Implement this method to publish quotes to the T-0 Network.
    /// Called once the server has started, then at the configured interval.
    /// </summary>
    protected abstract Task PublishQuotesAsync(CancellationToken cancellationToken);

    /// <summary>
    /// Called when <see cref="PublishQuotesAsync"/> throws an exception.
    /// Override to customize error handling (default: writes to stderr).
    /// </summary>
    protected virtual void OnError(Exception ex)
        => Console.Error.WriteLine($"Error publishing quotes: {ex.Message}");
}
