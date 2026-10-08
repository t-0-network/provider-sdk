using System.Diagnostics.CodeAnalysis;

namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Interface for ECDSA signing operations.
/// </summary>
/// <remarks>
/// Obsolete: a client takes a <see cref="SignFn"/>, and <see cref="Signer"/> converts to it. This
/// interface stays so that code written against v1.2 compiles: <see cref="Signer"/> implements it,
/// the server registers the signer as it for dependency injection, and every client factory and
/// <c>SigningDelegatingHandler</c> has an obsolete overload taking it. Those overloads sign through
/// a <see cref="SignFn"/> made from <see cref="Sign"/>, with the same checks and errors.
/// </remarks>
[Obsolete(SignerAdapter.ObsoleteMessage)]
public interface ISigner
{
    /// <summary>
    /// Signs a 32-byte digest and returns the signature with public key.
    /// </summary>
    SignResult Sign(byte[] digest);

    /// <summary>
    /// Returns the uncompressed public key (65 bytes: 0x04 + x[32] + y[32]).
    /// </summary>
    byte[] GetPublicKey();

    /// <summary>
    /// Returns the public key as hex string (without 0x prefix).
    /// </summary>
    string GetPublicKeyHex();

    /// <summary>
    /// Returns the public key as hex string with 0x prefix.
    /// </summary>
    string GetPublicKeyHexPrefixed();
}

#pragma warning disable CS0618 // The adapter is what the obsolete ISigner overloads sign through.
internal static class SignerAdapter
{
    internal const string ObsoleteMessage = "Use SignFn, or Signer, which converts to it.";

    /// <summary>
    /// The <see cref="SignFn"/> a client signs with for <paramref name="signer"/>; null for null, so
    /// the client refuses it as it refuses a null <see cref="SignFn"/>. The signer is called for each
    /// request, and a null result is passed on as a missing signature, which the client refuses.
    /// </summary>
    [return: NotNullIfNotNull(nameof(signer))]
    internal static SignFn? ToSignFn(ISigner? signer) =>
        signer is null ? null : digest =>
        {
            var result = signer.Sign(digest);
            return result is null ? (null!, null!) : (result.Signature, result.PublicKey);
        };
}
#pragma warning restore CS0618
