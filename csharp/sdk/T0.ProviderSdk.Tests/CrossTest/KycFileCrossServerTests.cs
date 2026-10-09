using Grpc.Core;
using T0.ProviderSdk.Api.Tzero.V1.Manage.KycSharing;
using T0.ProviderSdk.Crypto;
using T0.ProviderSdk.KycSharing;
using T0.ProviderSdk.Network;

namespace T0.ProviderSdk.Tests.CrossTest;

/// <summary>
/// The KYC file helpers against <c>go_helper serve</c>. One process per test. gRPC only.
/// </summary>
public class KycFileCrossServerTests
{
    private const string PrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8";
    private const string PublicKey = "0x044fa1465c087aaf42e5ff707050b8f77d2ce92129c5f300686bdd3adfffe44567713bb7931632837c5268a832512e75599b6964f4484c9531c02e96d90384d9f0";
    private const string Shape = "download stream must be one metadata message followed by chunks";

    [GoHelperFact]
    public async Task UploadAndDownload()
    {
        await using var server = await Start();
        var files = Client(server.BaseUrl);
        var data = Patterned(2621440);
        long fileId = await KycFiles.UploadFileAsync(files, new UploadFileRequest.Types.Metadata
        {
            PayoutProviderId = 7,
            ClientId = "applicant-1",
            FileName = "passport.pdf",
            DeclaredContentType = "application/pdf",
            UploadId = "upload-1",
        }, data, Deadline());
        Assert.True(fileId >= 1);
        var (metadata, got) = await KycFiles.DownloadFileAsync(files, Download(fileId), Deadline());
        Assert.Equal("application/pdf", metadata.ContentType);
        Assert.Equal("passport.pdf", metadata.FileName);
        Assert.Equal(data, got);
    }

    [GoHelperFact]
    public async Task UnknownFileIsNotFound()
    {
        await using var server = await Start();
        var ex = await Assert.ThrowsAsync<RpcException>(() =>
            KycFiles.DownloadFileAsync(Client(server.BaseUrl), Download(42), Deadline()));
        Assert.Equal(StatusCode.NotFound, ex.StatusCode);
    }

    [GoHelperFact]
    public async Task DownloadShapeIsInvalidArgument()
    {
        await using var server = await Start();
        var ex = await Assert.ThrowsAsync<RpcException>(() =>
            KycFiles.DownloadFileAsync(Client(server.BaseUrl), Download(9223372036854775807), Deadline()));
        Assert.Equal(StatusCode.InvalidArgument, ex.StatusCode);
        Assert.Equal(Shape, ex.Status.Detail);
    }

    [GoHelperFact]
    public async Task DeniedUploadIsPermissionDenied()
    {
        await using var server = await Start();
        var ex = await Assert.ThrowsAsync<RpcException>(() => KycFiles.UploadFileAsync(
            Client(server.BaseUrl),
            new UploadFileRequest.Types.Metadata { PayoutProviderId = 7, ClientId = "kyc-file-permission-denied" },
            Patterned(8388608),
            Deadline()));
        Assert.Equal(StatusCode.PermissionDenied, ex.StatusCode);
    }

    private static KycFileService.KycFileServiceClient Client(string baseUrl) =>
        NetworkClient.Create(new NetworkClientOptions { BaseUrl = baseUrl }, Signer.FromHex(PrivateKey),
            invoker => new KycFileService.KycFileServiceClient(invoker));

    private static CallOptions Deadline() => new(deadline: DateTime.UtcNow.AddSeconds(30));

    private static DownloadFileRequest Download(long fileId) => new()
    {
        FileId = fileId,
        PayoutRequesterId = 3,
        PayoutProviderId = 7,
        ClientId = "applicant-1",
    };

    private static byte[] Patterned(int n)
    {
        var data = new byte[n];
        for (int i = 0; i < n; i++)
            data[i] = (byte)i;
        return data;
    }

    private static async Task<GoStream> Start()
    {
        var port = TestPorts.FindFreePort();
        var process = new System.Diagnostics.Process
        {
            StartInfo = new System.Diagnostics.ProcessStartInfo
            {
                FileName = GoHelper.Require(),
                ArgumentList = { "serve", port.ToString(), PublicKey },
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
            }
        };
        var server = new GoStream(process, $"http://127.0.0.1:{port}");
        process.OutputDataReceived += (_, e) => { };
        process.ErrorDataReceived += (_, e) => { };
        try
        {
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            await TestPorts.WaitForPortAsync(port, TimeSpan.FromSeconds(10));
            return server;
        }
        catch
        {
            await server.DisposeAsync();
            throw;
        }
    }

    private sealed class GoStream : IAsyncDisposable
    {
        private readonly System.Diagnostics.Process _process;
        public GoStream(System.Diagnostics.Process process, string baseUrl)
        {
            _process = process;
            BaseUrl = baseUrl;
        }
        public string BaseUrl { get; }
        public async ValueTask DisposeAsync()
        {
            try
            {
                if (!_process.HasExited)
                    _process.Kill();
            }
            catch (InvalidOperationException)
            {
            }
            await _process.WaitForExitAsync();
            _process.Dispose();
        }
    }
}
