# KYB API

The services in `tzero.v1.manage.kyc_sharing` are served by the KYB API, not by the T-0 Network. A client of these services needs the KYB API's base URL. The provider's public key must be registered with the KYB API. Every SDK builds the client with the same function it uses for the network client, and signs its requests the same way.

## Base URL

The KYB API has its own host, separate from the network's `https://api.t-0.network`:

| Environment | Base URL |
|---|---|
| Production | `https://app-api.t-0.network` |
| Sandbox | `https://app-api-sandbox.t-0.network` |

Every call of the three services goes to this host, file uploads and downloads included.

## Key

The KYB API checks each request against the provider's public key registered with the KYB API. The provider asks T-0 to register the key with the KYB API, separately from the network, before its first call. A request signed with a key the KYB API does not have gets `Unauthenticated` "request signed with unknown public key".

By default T-0 registers the provider's network key, so the provider signs network and KYB API requests with one private key. When the provider replaces its network key, the new key works on the KYB API once T-0 registers it there too.

T-0 does not require a separate key for the KYB API, but a provider can ask for one. The signature covers the body and the timestamp, not the URL ([S1](CROSS_SDK_RULES.md#signing)). So with one key, a request signed for the network also passes the KYB API's signature check within the ±60 s timestamp window, and the other way round. With a separate key, a request signed for the network gets `Unauthenticated` at the KYB API, and the other way round. For a separate key, the provider first generates a key pair with `t0-init keygen` ([`CLI.md`](CLI.md#keygen)). Second, it sends T-0 the public key. Third, it gives the private key only to its KYB API client. The KYB API keeps one key per provider, so the separate key replaces the network key there.

T-0's public docs list the KYB API's services under the Management API, and describe the default key under [Management API Key](https://docs.t-0.network/docs/integration-guidance/introduction/#management-api-key).

## Signing

The SDK client signs every request as it signs a network request, and sets `X-Public-Key`, `X-Signature` and `X-Signature-Timestamp` ([S1](CROSS_SDK_RULES.md#signing)). A stream is signed over its first message ([`STREAMING.md`](STREAMING.md)). The KYB API authenticates a request that carries `X-Public-Key` by its signature only, and ignores an `Authorization` header on it.

## HTTP method

The KYB API accepts a signed request only as POST, and answers any other method with `Unimplemented` "GET requests are not supported". The SDK clients send every call as POST.

## Services

The proto files are in [`proto/tzero/v1/manage/kyc_sharing/`](../proto/tzero/v1/manage/kyc_sharing/).

| Service | File | Used by |
|---|---|---|
| `KycApplicantRequesterService` | `applicant_requester.proto` | A Payout Requester. It creates, fills in, submits, withdraws and deletes applicants, and lists a provider's forms. |
| `KycApplicantProviderService` | `applicant_provider.proto` | A Payout Provider. It reads, approves, rejects and lists submitted applicants. |
| `KycFileService` | `file.proto` | Both. The requester uploads applicant files, and both sides download them. |

`common.proto` and `form.proto` hold the messages these services share. A Payout Provider sets up its forms in the T-0 dashboard. The SDK helpers for `KycFileService` are described in each SDK's README, in the section "KYC files".
