namespace T0.ProviderSdk.Crypto;

/// <summary>
/// Signs a request: takes the 32-byte digest and returns the signature (64 bytes r‖s, or 65 bytes
/// r‖s‖v) and the 65-byte uncompressed public key it verifies against. A client checks both before
/// it sends anything, and a signer that throws fails the call with Internal
/// "signing the request failed: &lt;message&gt;". <see cref="Signer"/> converts to it, so
/// <c>Signer.FromHex(key)</c> can be passed wherever a <see cref="SignFn"/> is taken.
/// </summary>
public delegate (byte[] Signature, byte[] PublicKey) SignFn(byte[] digest);
