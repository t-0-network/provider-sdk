# t-0 Network Provider Starter

CLI tool to quickly bootstrap a Node.js TypeScript integration service for the t-0 Network.

## Overview

This CLI tool scaffolds a complete TypeScript project configured to integrate with the t-0 Network as a provider. It automatically generates secp256k1 cryptographic key pairs, sets up your development environment, and includes all necessary dependencies to get started immediately.

## Prerequisites

Before using this tool, ensure you have:

- **Node.js** >= 14.0.0
- **npm** >= 6.0.0
- **OpenSSL** (for cryptographic key generation)

## Quick Start

### Using npx (Recommended)

Run the following command to create a new t-0 Network integration:

```bash
npx @t-0/provider-starter-ts
```

### Alternative: Global Installation

```bash
npm install -g @t-0/provider-starter-ts
provider-starter-ts
```

## What It Does

When you run the CLI tool, it performs the following steps automatically:

1. **Interactive Prompt** - Asks for your project name
2. **Project Directory** - Creates a new directory with your project name
3. **Template Setup** - Copies pre-configured TypeScript project structure
4. **Key Generation** - Generates a secp256k1 cryptographic key pair using OpenSSL
5. **Environment Configuration** - Creates `.env` file with:
   - Generated private key (`PROVIDER_PRIVATE_KEY`)
   - Generated public key (as comment for reference)
   - t-0 Network public key (`NETWORK_PUBLIC_KEY`)
   - Server configuration (`PORT`, `NODE_ENV`)
   - Optional quote publishing interval
6. **Git Initialization** - Initializes git repository with `.gitignore`
7. **Dependency Installation** - Runs `npm install` to install all dependencies

## Generated Project Structure

After running the CLI, your new project will have the following structure:

```
your-project-name/
├── src/
│   ├── index.ts              # Main entry point
│   ├── service.ts            # Provider service implementation
│   ├── publish_quotes.ts     # Quote publishing logic
│   ├── get_quote.ts          # Quote retrieval logic
│   ├── submit_payment.ts     # Payment submission logic
│   └── lib.ts                # Utility functions
├── .env                      # Environment variables (with generated keys)
├── .env.example              # Example environment file
├── .eslintrc.json           # ESLint configuration
├── .gitignore               # Git ignore rules
├── package.json             # Project dependencies
└── tsconfig.json            # TypeScript configuration
```

## Environment Variables

The generated `.env` file includes:

| Variable | Description |
|----------|-------------|
| `NETWORK_PUBLIC_KEY` | t-0 Network's public key (pre-configured) |
| `PROVIDER_PRIVATE_KEY` | Your generated private key (keep secret!) |
| `PORT` | Server port (default: 3000) |
| `NODE_ENV` | Environment mode (default: development) |
| `TZERO_ENDPOINT` | t-0 Network API endpoint (default: sandbox) |
| `QUOTE_PUBLISHING_INTERVAL` | Quote publishing frequency in ms (optional) |

## Available Scripts

In your generated project, you can run:

### `npm run dev`

Runs the service in development mode using `ts-node`.

### `npm run build`

Compiles TypeScript to JavaScript in the `dist/` directory.

### `npm start`

Runs the compiled production build from `dist/`.

### `npm run lint`

Lints TypeScript files in the `src/` directory using ESLint.

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
   - Find your public key in the `.env` file (marked as "Step 1.2")
   - Contact the t-0 team to register your provider

4. **Implement quote publishing:**
   - Edit `src/publish_quotes.ts` to implement your quote publishing logic
   - This determines how you provide exchange rate quotes to the network

5. **Start development server:**
   ```bash
   npm run dev
   ```

6. **Test your integration:**
   - Follow the TODO comments in `src/index.ts` for step-by-step guidance
   - Test quote retrieval
   - Test payment submission
   - Verify payout endpoint


## Key Features

- **Type Safety** - Full TypeScript support with strict mode enabled
- **Automatic Key Generation** - Secure secp256k1 key pairs generated via OpenSSL
- **Pre-configured SDK** - t-0 Provider SDK integrated and ready to use
- **Development Ready** - Hot reload support with ts-node
- **Production Ready** - Optimized build configuration
- **Security First** - `.env` automatically excluded from git
- **Code Quality** - ESLint configured with TypeScript rules


## Security Considerations

- **Never commit `.env` file** - It's automatically added to `.gitignore`
- **Keep private key secure** - The `PROVIDER_PRIVATE_KEY` must remain confidential
- **Share only public key** - Only the public key should be shared with t-0 team
- **Use environment-specific configs** - Different keys for dev/staging/production

## Deployment

When ready to deploy:

1. **Build your project:**
   ```bash
   npm run build
   ```

2. **Set environment variables** on your hosting platform

3. **Provide your base URL to t-0 team** for network registration

4. **Start the service:**
   ```bash
   npm start
   ```

## Troubleshooting

### "Directory already exists" Error
The project directory name is already taken. Choose a different project name.

### "OpenSSL not found" Error
Install OpenSSL:
- **macOS:** `brew install openssl`
- **Ubuntu/Debian:** `sudo apt-get install openssl`
- **Windows:** Download from [openssl.org](https://www.openssl.org/)

### Key Generation Fails
Ensure OpenSSL is installed and accessible in your PATH. Try running:
```bash
openssl version
```

### npm install Fails
Check your Node.js and npm versions:
```bash
node --version  # Should be >= 14
npm --version   # Should be >= 6
```

## Support

For issues or questions:
- Review the generated code comments and TODO markers
- Check the [t-0 Network documentation](https://t-0.network)
- Contact the t-0 team for integration support