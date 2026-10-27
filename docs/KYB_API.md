# KYB API

The services in `tzero.v1.manage.kyc_sharing` are served by the KYB API, not by the T-0 Network. A client of these services needs the KYB API's base URL and a key pair registered with the KYB API. Every SDK builds the client with the same function it uses for the network client, and signs its requests the same way.

## Base URL

The KYB API has its own host, separate from the network's `https://api.t-0.network`:

| Environment | Base URL |
|---|---|
| Production | `https://app-api.t-0.network` |
| Sandbox | `https://app-api-sandbox.t-0.network` |

Every call of the three services goes to this host, file uploads and downloads included.

## Key

The KYB API checks each request against the provider's public key registered with the KYB API, so a provider uses a separate key pair for it. An admin registers that public key with `tzero.v1.admin.KybAdminService.UpdateProviderPublicKey`. The KYB API does not accept the network key, which is registered with `tzero.v1.admin.AdminService.UpdateProviderPublicKey`.

`t0-init keygen` prints a new key pair ([`cli/README.md`](../cli/README.md)).

## Signing

The SDK client signs every request as it signs a network request, and sets `X-Public-Key`, `X-Signature` and `X-Signature-Timestamp` ([S1](CROSS_SDK_RULES.md#signing)). A stream is signed over its first message ([`STREAMING.md`](STREAMING.md)). The KYB API authenticates a request that carries `X-Public-Key` by its signature only, and ignores an `Authorization` header on it.

## HTTP method

The KYB API accepts a signed request only as POST, and answers any other method with `InvalidArgument`. The SDK clients send every call as POST.

## Services

The proto files are in [`proto/tzero/v1/manage/kyc_sharing/`](../proto/tzero/v1/manage/kyc_sharing/).

| Service | File | Used by |
|---|---|---|
| `KycApplicantRequesterService` | `applicant_requester.proto` | A Payout Requester. It creates, fills in, submits, withdraws and deletes applicants, and lists a provider's forms. |
| `KycApplicantProviderService` | `applicant_provider.proto` | A Payout Provider. It reads, approves, rejects and lists submitted applicants. |
| `KycFileService` | `file.proto` | Both. The requester uploads applicant files, and both sides download them. |

`common.proto` and `form.proto` hold the messages these services share. A Payout Provider sets up its forms in the T-0 dashboard. The SDK helpers for `KycFileService` are described in each SDK's README, in the section "KYC files".
