package main

// Convention: <lang>/starter/template/ — override with lang=path for exceptions.
// Python's template lives at python/starter/template/ (not inside the package
// source tree) but still needs the override because the starter package root
// is python/starter/, not python/.
//go:generate go run ./internal/sync go node python=python/starter/template java csharp
