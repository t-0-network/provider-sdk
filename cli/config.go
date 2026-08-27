package main

var Config = CLIConfig{
	ProductName:  "t0",
	Command:      "t0 init",
	RoleRequired: false,
	DefaultRole:  "",
	Languages:    []string{"go", "node", "python", "java", "csharp"},

	// Override template paths that don't follow the convention <lang>/starter/template/
	TemplatePaths: map[string]string{
		"python": "python/starter/src/t0_provider_starter/template",
	},
}
