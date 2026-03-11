# t-0 Network Go Provider Starter

CLI tool to quickly bootstrap a Go integration service for the t-0 Network.

## Overview

This CLI tool scaffolds a complete Go project configured to integrate with the t-0 Network as a provider. It automatically generates secp256k1 cryptographic key pairs, sets up your development environment, and includes all necessary dependencies to get started immediately.

## Prerequisites

Before using this tool, ensure you have:

- **Go** >= 1.20
- **Git** (for repository initialization)

## Quick Start

### Using `go run` (Recommended)

Run the following command to create a new t-0 Network integration:

```bash
go run github.com/t-0-network/provider-sdk/go/starter@latest your-project-name
```

### Alternative: Clone and Build

```bash
git clone https://github.com/t-0-network/provider-sdk.git
cd provider-sdk/go/starter
go run . your-project-name
```

## What It Does

When you run the CLI tool, it performs the following steps automatically:

1. **Interactive Input** - Accepts your project name as an argument
2. **Project Directory** - Creates a new directory with your project name
3. **Template Setup** - Copies pre-configured Go project structure
4. **Key Generation** - Generates a secp256k1 cryptographic key pair
5. **Environment Configuration** - Creates `.env` file with:
   - Generated private key (`PROVIDER_PRIVATE_KEY`)
   - Generated public key (as comment for reference)
   - t-0 Network public key (`NETWORK_PUBLIC_KEY`)
   - Server configuration (`PORT`, `TZERO_ENDPOINT`)
   - Optional quote publishing interval
6. **Module Initialization** - Configures `go.mod` with your project module name
7. **Dependency Management** - Sets up Go module dependencies

## Generated Project Structure

After running the CLI, your new project will have the following structure:

```
your-project-name/
├── cmd/
│   └── main.go              # Main entry point
├── internal/
│   ├── handler/
│   │   ├── provider.go      # Provider service implementation
│   │   └── payment.go       # Payment handler implementation
│   ├── get_quote.go         # Quote retrieval logic
│   ├── publish_quotes.go    # Quote publishing logic
│   └── service.go           # Service utilities
├── .env                     # Environment variables (with generated keys)
├── .env.example             # Example environment file
├── .gitignore               # Git ignore rules
├── Dockerfile               # Docker configuration
├── go.mod                   # Go module definition
├── go.sum                   # Go dependencies checksums
└── README.md                # Project documentation
```

## Environment Variables

The generated `.env` file includes:

| Variable | Description |
| --- | --- |
| `NETWORK_PUBLIC_KEY` | t-0 Network's public key (pre-configured) |
| `PROVIDER_PRIVATE_KEY` | Your generated private key (keep secret!) |
| `PORT` | Server port (default: 8080) |
| `TZERO_ENDPOINT` | t-0 Network API endpoint (default: sandbox) |
| `QUOTE_PUBLISHING_INTERVAL` | Quote publishing frequency in milliseconds (optional) |

## Available Commands

In your generated project, you can run:

### `go run ./cmd/main.go`

Runs the service in development mode.

### `go build -o provider ./cmd/main.go`

Compiles the Go binary for production use.

### `go test ./...`

Runs all tests in the project.

### `go fmt ./...`

Formats all Go files according to Go standards.

### `go vet ./...`

Runs static analysis on your code.

## Getting Started with Your Integration

After creating your project:

1. **Navigate to project directory:**

```bash
cd your-project-name
```

2. **Review the generated keys:**

   - Open `.env` file
   - Your private key is stored in `PROVIDER_PRIVATE_KEY`
   - Your public key is shown as a comment (you'll need to share this)

3. **Share your public key with t-0 team:**

   - Find your public key in the `.env` file (marked as "Step 1.2" in the code comments)
   - Contact the t-0 team to register your provider

4. **Implement quote publishing:**

   - Edit `internal/publish_quotes.go` to implement your quote publishing logic
   - This determines how you provide exchange rate quotes to the network

5. **Start development server:**

```bash
go run ./cmd/main.go
```

6. **Test your integration:**

   - Follow the TODO comments in `cmd/main.go` for step-by-step guidance
   - Test quote retrieval
   - Test payment submission
   - Verify payment endpoint

## Deployment

When ready to deploy:

1. **Build your project:**

```bash
go build -o provider ./cmd/main.go
```

2. **Set environment variables** on your hosting platform

3. **Provide your base URL to t-0 team** for network registration

4. **Start the service:**

```bash
./provider
```

### Docker Deployment

```bash
docker build -t your-provider:latest .
docker run -p 8080:8080 --env-file .env your-provider:latest
```

## Troubleshooting

### "Directory already exists" Error

The project directory name is already taken. Choose a different project name and try again.

### "Go not found" Error

Install Go from [golang.org](https://golang.org/dl/). Ensure it's in your PATH:

```bash
go version
```

### Key Generation Fails

Ensure your Go installation is complete and working:

```bash
go version
```

### Module Download Fails

Check your internet connection and Go module proxy settings:

```bash
go env GOPROXY
```

## Support

For issues or questions:

- Review the generated code comments and TODO markers
- Check the [t-0 Network documentation](https://t-0.network/)
- Review the [t-0 Provider SDK for Go](https://github.com/t-0-network/provider-sdk)
- Contact the t-0 team for integration support
