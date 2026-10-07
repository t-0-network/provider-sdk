using System.Diagnostics;
using System.Text.Json;
using System.Text.RegularExpressions;
using Grpc.Core;
using Grpc.Health.V1;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// The C# column of the shared client behavior: every case of client_cases in
/// cross_test/test_vectors.json, made by a <see cref="NetworkClient"/> over gRPC (the protocol the
/// C# client speaks) to <c>go_helper client-probe</c>, which checks each request it gets.
/// </summary>
public class ClientProbeTests
{
    [GoHelperFact]
    public async Task SharedClientCases()
    {
        var goHelper = GoHelper.Require();
        using var vectors = JsonDocument.Parse(await File.ReadAllTextAsync(GoHelper.VectorsPath));
        var signer = Signer.FromHex(vectors.RootElement.GetProperty("keys").GetProperty("private_key").GetString()!);
        var impostor = Signer.FromHex(vectors.RootElement.GetProperty("impostor_keys").GetProperty("private_key").GetString()!);

        using var probe = Process.Start(new ProcessStartInfo
        {
            FileName = goHelper,
            ArgumentList = { "client-probe", "--sdk", "csharp", "--vectors", GoHelper.VectorsPath },
            RedirectStandardOutput = true,
            RedirectStandardError = true,
        })!;
        // Its PASS and FAIL lines: "PASS <case>" or "FAIL <case>: <reason>".
        var log = new List<string>();
        var logReader = Task.Run(async () =>
        {
            while (await probe.StandardError.ReadLineAsync() is { } line)
                lock (log) log.Add(line);
        });
        string[] Logged()
        {
            lock (log) return [.. log];
        }
        var failures = new List<string>();
        try
        {
            var ready = await probe.StandardOutput.ReadLineAsync();
            Assert.True(ready?.StartsWith("READY ") == true, $"client-probe did not start: {ready}");
            var baseUrl = ready!["READY ".Length..];

            var cases = vectors.RootElement.GetProperty("client_cases");
            Assert.NotEmpty(cases.EnumerateArray());
            foreach (var c in cases.EnumerateArray())
            {
                var name = c.GetProperty("name").GetString()!;
                var expect = c.GetProperty("expect");
                var expected = expect.GetProperty("code").GetString();
                var expectedMessage = expect.TryGetProperty("message", out var message) ? message.GetString() : null;
                var options = new NetworkClientOptions { BaseUrl = $"{baseUrl}/{name}" };
                if (c.TryGetProperty("client_timeout_ms", out var clientTimeout))
                    options.Timeout = TimeSpan.FromMilliseconds(clientTimeout.GetInt64());
                DateTime? deadline = c.TryGetProperty("call_timeout_ms", out var callTimeout)
                    ? DateTime.UtcNow.AddMilliseconds(callTimeout.GetInt64())
                    : null;
                // C# has no hex-key form: the default and `signer` factory both take Signer.FromHex.
                SignFn sign = c.TryGetProperty("custom_signer", out var custom) ? CustomSigner(impostor, custom) : signer;
                var client = NetworkClient.Create(options, sign, invoker => new Health.HealthClient(invoker));

                var logged = Logged().Length;
                string code, detail;
                try
                {
                    var reply = await client.CheckAsync(new HealthCheckRequest(), deadline: deadline);
                    (code, detail) = reply.Status == HealthCheckResponse.Types.ServingStatus.Serving
                        ? ("ok", "")
                        : ("not serving", reply.Status.ToString());
                }
                catch (RpcException e)
                {
                    (code, detail) = (SnakeCase(e.StatusCode.ToString()), e.Status.Detail);
                }
                if (code != expected || expectedMessage is not null && detail != expectedMessage)
                    failures.Add($"{name}: expected {expected} \"{expectedMessage}\", got {code} \"{detail}\"");
                var caseFails = FailLines(Logged()[logged..], name);
                if (caseFails.Length > 0)
                    failures.Add($"{name}: client-probe logged:\n{string.Join("\n", caseFails)}");
            }
        }
        finally
        {
            probe.Kill();
            await probe.WaitForExitAsync();
        }
        await logReader;
        // A call that ends on its own deadline can end before the probe logs its request, so the whole log is
        // checked again once the probe has stopped.
        var fails = FailLines(Logged(), null);
        if (fails.Length > 0)
            failures.Add($"client-probe logged:\n{string.Join("\n", fails)}");
        Assert.True(failures.Count == 0,
            string.Join("\n", failures) + "\nclient-probe log:\n" + string.Join("\n", Logged()));
    }

    // The FAIL lines of case name ("FAIL <name>: <reason>"), or every FAIL line when name is null.
    private static string[] FailLines(IEnumerable<string> lines, string? name)
    {
        var prefix = name is null ? "FAIL " : $"FAIL {name}:";
        return lines.Where(line => line.StartsWith(prefix, StringComparison.Ordinal)).ToArray();
    }

    // Signs with the impostor key's Signer, then changes its output as the case says.
    private static SignFn CustomSigner(Signer impostor, JsonElement custom) => digest =>
    {
        if (custom.TryGetProperty("error", out var error))
            throw new InvalidOperationException(error.GetString());
        var result = impostor.Sign(digest);
        var signature = custom.TryGetProperty("signature", out var change) ? change.GetString() switch
        {
            "r_s" => result.Signature[..64],
            "v_plus_27" => [.. result.Signature[..64], (byte)(result.Signature[64] + 27)],
            "first_63_bytes" => result.Signature[..63],
            var other => throw new ArgumentException($"unknown signature change {other}"),
        } : result.Signature;
        var publicKey = custom.TryGetProperty("public_key", out var key)
            ? Convert.FromHexString(key.GetString()!)
            : result.PublicKey;
        return (signature, publicKey);
    };

    // StatusCode.DeadlineExceeded → "deadline_exceeded", as the shared file names codes.
    private static string SnakeCase(string name) =>
        Regex.Replace(name, "(?<!^)([A-Z])", "_$1").ToLowerInvariant();
}
