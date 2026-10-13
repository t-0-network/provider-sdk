using Xunit;

// ConfigTests and SubmitPaymentTests both redirect the process-global Console
// streams and environment variables to observe the starter's own output and
// messages. The samples write to Console and expose no injection point, so the
// collections that read those globals run one at a time.
[assembly: CollectionBehavior(DisableTestParallelization = true)]
