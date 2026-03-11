import fs from 'fs-extra';
import path from 'path';
import { execSync } from 'child_process';
import inquirer from 'inquirer';
import chalk from 'chalk';

interface KeyPair {
  privateKey: string;
  publicKey: string;
}

interface ProjectAnswers {
  projectName: string;
}

async function create(): Promise<void> {
  console.log(chalk.blue('\n🚀 Create Your Service\n'));

  // Prompt for project name
  const answers = await inquirer.prompt<ProjectAnswers>([
    {
      type: 'input',
      name: 'projectName',
      message: 'Project name:',
      validate: (input: string) => {
        if (!input) return 'Project name is required';
        if (!/^[a-z0-9-_]+$/.test(input)) {
          return 'Project name can only contain lowercase letters, numbers, hyphens and underscores';
        }
        return true;
      }
    }
  ]);

  const projectName = answers.projectName;
  const projectPath = path.join(process.cwd(), projectName);

  // Check if directory exists
  if (fs.existsSync(projectPath)) {
    console.log(chalk.red(`\n❌ Directory "${projectName}" already exists!`));
    process.exit(1);
  }

  console.log(chalk.green(`\n✨ Creating project in ${projectPath}...\n`));

  try {
    // Create project directory
    fs.mkdirSync(projectPath);

    // Copy template files
    const templatePath = path.join(__dirname, '../template');
    fs.copySync(templatePath, projectPath);

    // Generate key pair
    console.log(chalk.cyan('🔐 Generating key pair...'));
    const keys = generateKeyPair();

    // Create .env file with keys
    const envContent = `
NETWORK_PUBLIC_KEY=0x041b6acf3e830b593aaa992f2f1543dc8063197acfeecefd65135259327ef3166acaca83d62db19eb4fecb3d04e44094378839b8c13a2af26bf78fed56a4af935b

# Private Key (secp256k1)    
PROVIDER_PRIVATE_KEY=${keys.privateKey}
# TODO: Step 1.2 Share this Public Key with t-0 team
# ${keys.publicKey}

# Server Configuration
PORT=3000
NODE_ENV=development

# QUOTE_PUBLISHING_INTERVAL=5000

`;
    fs.writeFileSync(path.join(projectPath, '.env'), envContent);

    // Update package.json with project name
    const packageJsonPath = path.join(projectPath, 'package.json');
    let packageJson = fs.readFileSync(packageJsonPath, 'utf8');
    packageJson = packageJson.replace('{{PROJECT_NAME}}', projectName);
    fs.writeFileSync(packageJsonPath, packageJson);

    // Create .gitignore
    const gitignoreContent = `node_modules/
dist/
.env
.env.local
.env.*.local
*.log
.DS_Store
`;
    fs.writeFileSync(path.join(projectPath, '.gitignore'), gitignoreContent);

    // Git init
    console.log(chalk.cyan('📦 Initializing git repository...'));
    execSync('git init', { cwd: projectPath, stdio: 'ignore' });

    // npm install
    console.log(chalk.cyan('📥 Installing dependencies...'));
    execSync('npm install', { cwd: projectPath, stdio: 'inherit' });

    console.log(chalk.green('\n✅ Project created successfully!\n'));
    console.log(chalk.white(`  cd ${projectName}`));
    console.log(chalk.white(`  npm run dev\n`));

  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';
    console.error(chalk.red('\n❌ Error creating project:'), errorMessage);
    // Cleanup on error
    if (fs.existsSync(projectPath)) {
      //fs.removeSync(projectPath);
    }
    process.exit(1);
  }
}

function generateKeyPair(): KeyPair {
  const tempDir = fs.mkdtempSync('/tmp/keygen-');
  const privateKeyPath = path.join(tempDir, 'private_key.pem');

  try {
    // Generate private key
    execSync(`openssl ecparam -genkey -name secp256k1 -noout -out ${privateKeyPath}`, {
      stdio: 'ignore'
    });

    // Extract private key hex
    const privateKeyHex = execSync(
      `openssl ec -in ${privateKeyPath} -text -noout 2>/dev/null | grep -A 3 'priv:' | tail -n +2 | tr -d '\\n: ' | sed 's/[^0-9a-f]//g'`,
      { encoding: 'utf8' }
    ).trim();

    // Extract public key hex
    const publicKeyHex = execSync(
      `openssl ec -in ${privateKeyPath} -text -noout 2>/dev/null | grep -A 5 'pub:' | tail -n +2 | tr -d '\\n: ' | sed 's/[^0-9a-f]//g'`,
      { encoding: 'utf8' }
    ).trim();

    return {
      privateKey: privateKeyHex,
      publicKey: publicKeyHex
    };
  } finally {
    // Cleanup temp files
    fs.removeSync(tempDir);
  }
}

export default create;
