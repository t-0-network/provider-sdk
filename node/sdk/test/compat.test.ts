import { it } from 'node:test';
import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import path from 'node:path';

const execFileAsync = promisify(execFile);

// The v1.2.1 call forms in test/compat/v1_2_1_api.ts still compile under --strict.
it('the v1.2.1 public API still compiles', { timeout: 120_000 }, async () => {
  const sdk = path.resolve(import.meta.dirname, '..');
  const tsc = path.join(sdk, 'node_modules', '.bin', 'tsc');
  try {
    await execFileAsync(tsc, [
      '--ignoreConfig', '--noEmit', '--strict', '--skipLibCheck', '--module', 'nodenext', '--moduleResolution', 'nodenext',
      '--target', 'es2022', '--types', 'node', path.join('test', 'compat', 'v1_2_1_api.ts'),
    ], { cwd: sdk });
  } catch (e) {
    const err = e as { stdout?: string; stderr?: string };
    assert.fail(`tsc failed:\n${err.stdout ?? ''}${err.stderr ?? ''}`);
  }
});
