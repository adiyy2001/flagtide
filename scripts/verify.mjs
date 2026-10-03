import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');

const steps = [
  ['pnpm', ['check:text']],
  ['pnpm', ['format:check']],
  ['pnpm', ['nx', 'run-many', '-t', 'lint', 'typecheck', 'test', 'build', '--exclude', 'server']],
  ['pnpm', ['nx', 'run', 'angular:pack-check']],
  ['pnpm', ['nx', 'run', 'server:verify']],
  ['pnpm', ['conformance']],
];

for (const [command, args] of steps) {
  console.log(`\n> ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, { cwd: root, stdio: 'inherit' });
  if (result.status !== 0) {
    console.error(`verify failed at: ${command} ${args.join(' ')}`);
    process.exit(result.status ?? 1);
  }
}
console.log('\nverify ok');
