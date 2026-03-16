using System.Globalization;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;

namespace T0.ProviderStarter;

public static partial class Program
{
    private const string Blue = "\u001B[34m";
    private const string Green = "\u001B[32m";
    private const string Yellow = "\u001B[33m";
    private const string Red = "\u001B[31m";
    private const string Reset = "\u001B[0m";

    private static bool _noColor;

    public static int Main(string[] args)
    {
        try
        {
            _noColor = args.Contains("--no-color");

            if (args.Contains("--help") || args.Contains("-h"))
            {
                PrintUsage();
                return 0;
            }

            if (args.Contains("--version") || args.Contains("-v"))
            {
                var version = typeof(Program).Assembly
                    .GetCustomAttribute<AssemblyInformationalVersionAttribute>()
                    ?.InformationalVersion ?? "unknown";
                Console.WriteLine($"T-0 Provider Starter (C#) {version}");
                return 0;
            }

            PrintHeader();

            // Get project name from args (skip flags)
            var projectName = args.FirstOrDefault(a => !a.StartsWith('-'));
            if (string.IsNullOrEmpty(projectName))
            {
                Console.Write("Enter your project name: ");
                Console.Out.Flush();
                projectName = Console.ReadLine();
            }

            projectName = SanitizeProjectName(projectName);
            if (string.IsNullOrEmpty(projectName))
            {
                PrintError("Invalid project name. Use only letters, numbers, and hyphens.");
                return 1;
            }

            var targetDir = Path.GetFullPath(projectName);
            if (Directory.Exists(targetDir))
            {
                PrintError($"Directory '{targetDir}' already exists. Please choose a different name.");
                return 1;
            }

            PrintInfo($"Creating project: {projectName}");

            // Extract template files
            PrintInfo("Extracting template files...");
            var pascalName = ToPascalCase(projectName);
            ExtractTemplate(targetDir, projectName, pascalName);
            PrintSuccess("Template files extracted");

            // Generate keypair
            PrintInfo("Generating secp256k1 keypair...");
            var (privateKeyHex, publicKeyHex) = KeyGenerator.Generate();
            PrintSuccess("Keypair generated");

            // Create .env file from .env.example
            PrintInfo("Creating .env file...");
            CreateEnvFile(targetDir, privateKeyHex);
            PrintSuccess("Environment configured");

            PrintCompletionMessage(targetDir, publicKeyHex);
            return 0;
        }
        catch (Exception e)
        {
            PrintError($"Failed to initialize project: {e.Message}");
            if (Environment.GetEnvironmentVariable("DEBUG") is not null)
                Console.Error.WriteLine(e);
            return 1;
        }
    }

    private static void ExtractTemplate(string targetDir, string projectName, string pascalName)
    {
        foreach (var (relativePath, content) in TemplateFiles.All)
        {
            var outputPath = relativePath
                .Replace("{{PROJECT_NAME}}", projectName);

            var filePath = Path.Combine(targetDir, outputPath);
            var dir = Path.GetDirectoryName(filePath);
            if (dir is not null)
                Directory.CreateDirectory(dir);

            var processed = content
                .Replace("{{PROJECT_NAME}}", projectName)
                .Replace("{{PROJECT_NAME_PASCAL}}", pascalName);

            File.WriteAllText(filePath, processed, new UTF8Encoding(false));
        }
    }

    private static void CreateEnvFile(string targetDir, string privateKeyHex)
    {
        var envExamplePath = Path.Combine(targetDir, ".env.example");
        if (!File.Exists(envExamplePath))
            return;

        var content = File.ReadAllText(envExamplePath);
        content = content.Replace("PROVIDER_PRIVATE_KEY=", $"PROVIDER_PRIVATE_KEY=0x{privateKeyHex}");
        File.WriteAllText(Path.Combine(targetDir, ".env"), content, new UTF8Encoding(false));
    }

    /// <summary>
    /// Converts "my-provider" to "MyProvider".
    /// </summary>
    internal static string ToPascalCase(string kebabName)
    {
        var ti = CultureInfo.InvariantCulture.TextInfo;
        return string.Concat(
            kebabName.Split('-', StringSplitOptions.RemoveEmptyEntries)
                .Select(w => ti.ToTitleCase(w)));
    }

    private static string SanitizeProjectName(string? name)
    {
        if (string.IsNullOrWhiteSpace(name))
            return "";
        return ProjectNameRegex().Replace(name.ToLowerInvariant().Replace(' ', '-'), "");
    }

    [GeneratedRegex("[^a-z0-9-]")]
    private static partial Regex ProjectNameRegex();

    private static void PrintHeader()
    {
        Console.WriteLine();
        Console.WriteLine(Color(Blue, "+-----------------------------------------------------------+"));
        Console.WriteLine($"{Color(Blue, "|")}     T-0 Network Provider SDK - C# Initializer             {Color(Blue, "|")}");
        Console.WriteLine(Color(Blue, "+-----------------------------------------------------------+"));
        Console.WriteLine();
    }

    private static void PrintCompletionMessage(string targetDir, string publicKey)
    {
        Console.WriteLine();
        Console.WriteLine(Color(Green, "+-----------------------------------------------------------+"));
        Console.WriteLine($"{Color(Green, "|")}                  Project Created Successfully!            {Color(Green, "|")}");
        Console.WriteLine(Color(Green, "+-----------------------------------------------------------+"));
        Console.WriteLine();
        Console.WriteLine($"Your project is ready at: {Color(Blue, targetDir)}");
        Console.WriteLine();
        Console.WriteLine($"{Color(Yellow, "Your public key (share with T-0 team):")}");
        Console.WriteLine(Color(Blue, $"0x{publicKey}"));
        Console.WriteLine();
        Console.WriteLine($"{Color(Yellow, "Next Steps:")}");
        Console.WriteLine();
        Console.WriteLine("  1. Navigate to your project:");
        Console.WriteLine($"     {Color(Blue, $"cd {targetDir}")}");
        Console.WriteLine();
        Console.WriteLine("  2. Run the application:");
        Console.WriteLine($"     {Color(Blue, "dotnet run")}");
        Console.WriteLine();
        Console.WriteLine("  3. Edit your integration logic:");
        Console.WriteLine($"     - {Color(Blue, "Services/PaymentHandler.cs")}");
        Console.WriteLine($"     - {Color(Blue, "Services/QuotePublisher.cs")}");
        Console.WriteLine();
    }

    private static void PrintUsage()
    {
        Console.WriteLine("Usage: t0-provider-starter [OPTIONS] [PROJECT_NAME]");
        Console.WriteLine();
        Console.WriteLine("Initialize a new T-0 Network provider project (C#/.NET)");
        Console.WriteLine();
        Console.WriteLine("Options:");
        Console.WriteLine("  -h, --help       Show this help message");
        Console.WriteLine("  -v, --version    Show version information");
        Console.WriteLine("  --no-color       Disable colored output");
    }

    private static void PrintInfo(string message) =>
        Console.WriteLine($"{Color(Blue, "[INFO]")} {message}");

    private static void PrintSuccess(string message) =>
        Console.WriteLine($"{Color(Green, "[OK]")} {message}");

    private static void PrintError(string message) =>
        Console.WriteLine($"{Color(Red, "[ERROR]")} {message}");

    private static string Color(string code, string text) =>
        _noColor ? text : $"{code}{text}{Reset}";
}
