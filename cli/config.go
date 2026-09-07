package main

var Config = CLIConfig{
	ProductName:      "t0",
	Command:          "t0 init",
	Description:      "a new T-0 Network provider project",
	RoleRequired:     false,
	DefaultRole:      "",
	Languages:        []string{"go", "node", "python", "java", "csharp"},
	JavaRepositories: []string{"jitpack", "maven-central"},
	JavaSDKArtifacts: []string{"com.github.t-0-network:provider-sdk", "network.t-0:provider-sdk-java"},
}
