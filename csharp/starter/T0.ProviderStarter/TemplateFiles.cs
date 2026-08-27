namespace T0.ProviderStarter;

/// <summary>
/// Reads template files from the template/ directory on disk.
/// Dotfiles use dot- prefix convention (dot-gitignore → .gitignore)
/// to avoid embedded resource naming issues.
/// </summary>
public static class TemplateFiles
{
    private static readonly string TemplateDir = Path.Combine(
        AppContext.BaseDirectory, "template");

    public static IReadOnlyDictionary<string, string> All => LoadAll();

    private static Dictionary<string, string> LoadAll()
    {
        var files = new Dictionary<string, string>();
        LoadDirectory(TemplateDir, "", files);
        return files;
    }

    private static void LoadDirectory(string dir, string prefix, Dictionary<string, string> files)
    {
        foreach (var file in Directory.GetFiles(dir))
        {
            var name = Path.GetFileName(file);
            var key = string.IsNullOrEmpty(prefix) ? name : $"{prefix}/{name}";
            files[key] = File.ReadAllText(file);
        }

        foreach (var subdir in Directory.GetDirectories(dir))
        {
            var name = Path.GetFileName(subdir);
            var subprefix = string.IsNullOrEmpty(prefix) ? name : $"{prefix}/{name}";
            LoadDirectory(subdir, subprefix, files);
        }
    }
}
