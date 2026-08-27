using T0.ProviderSdk.Api.Tzero.V1.Payment;

namespace {{PROJECT_NAME_PASCAL}}.Services;

// TODO: Step 2.6 (optional) Complete manual AML checks.
// Pay-Out Provider role: if your PayOut handler returned ManualAmlCheck instead of
// Accepted (see Services/PaymentHandler.cs), run your AML check out-of-band and
// report the outcome with this call.
public static class CompleteManualAmlCheck
{
    public static async Task CompleteAsync(NetworkService.NetworkServiceClient client, ulong paymentId)
    {
        try
        {
            var response = await client.CompleteManualAmlCheckAsync(new CompleteManualAmlCheckRequest
            {
                PaymentId = paymentId,
                Approved = new CompleteManualAmlCheckRequest.Types.Approved()
                // If your check failed, report a rejection instead:
                // Rejected = new CompleteManualAmlCheckRequest.Types.Rejected { Reason = "AML check failed" }
            });

            switch (response.ResultCase)
            {
                case CompleteManualAmlCheckResponse.ResultOneofCase.Approved:
                    // Pay-in provider approved the updated quotes — proceed with the payout using
                    // the updated amounts, then report the outcome via FinalizePayout.
                    Console.WriteLine($"Manual AML check approved for payment {paymentId}: payOutAmount={response.Approved.PayOutAmount}, settlementAmount={response.Approved.SettlementAmount}, quoteId={response.Approved.PayOutQuoteId}, clientQuoteId={response.Approved.PayOutClientQuoteId}");
                    break;
                case CompleteManualAmlCheckResponse.ResultOneofCase.Rejected:
                    // Pay-in provider rejected the updated quotes — do NOT proceed with the payout.
                    Console.WriteLine($"Updated quotes rejected for payment {paymentId}, do not proceed with the payout");
                    break;
            }
        }
        catch (Grpc.Core.RpcException ex)
        {
            Console.WriteLine($"Error completing manual AML check: {ex.Status.StatusCode} - {ex.Message}");
        }
    }
}
