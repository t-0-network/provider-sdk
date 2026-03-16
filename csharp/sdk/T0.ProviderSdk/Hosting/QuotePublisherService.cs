using Microsoft.Extensions.Hosting;

namespace T0.ProviderSdk.Hosting;

/// <summary>
/// Base class for periodic quote publishing as an ASP.NET Core hosted service.
/// Subclass and override <see cref="PublishQuotesAsync"/> with your publishing logic.
/// </summary>
/// <param name="interval">How often to publish quotes (recommended: 5 seconds).</param>
public abstract class QuotePublisherService(TimeSpan interval) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        using var timer = new PeriodicTimer(interval);
        while (await timer.WaitForNextTickAsync(stoppingToken))
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
    }

    /// <summary>
    /// Implement this method to publish quotes to the T-0 Network.
    /// Called periodically at the configured interval.
    /// </summary>
    protected abstract Task PublishQuotesAsync(CancellationToken cancellationToken);

    /// <summary>
    /// Called when <see cref="PublishQuotesAsync"/> throws an exception.
    /// Override to customize error handling (default: writes to stderr).
    /// </summary>
    protected virtual void OnError(Exception ex)
        => Console.Error.WriteLine($"Error publishing quotes: {ex.Message}");
}
