import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const [project, ...flags] = process.argv.slice(2);
const licenseOnly = flags.includes('--license-only');
if (project === undefined) {
  console.error('usage: node scripts/prepare-package.mjs <project>');
  process.exit(2);
}

const destination = resolve(root, 'dist/libs', project);
mkdirSync(destination, { recursive: true });
const files = licenseOnly
  ? [['LICENSE', 'LICENSE']]
  : [
      [`libs/${project}/package.json`, 'package.json'],
      [`libs/${project}/README.md`, 'README.md'],
      ['LICENSE', 'LICENSE'],
    ];
for (const [from, to] of files) {
  const source = resolve(root, from);
  if (!existsSync(source)) {
    console.error(`missing ${from}`);
    process.exit(1);
  }
  copyFileSync(source, resolve(destination, to));
}
console.log(`prepared dist/libs/${project}`);
