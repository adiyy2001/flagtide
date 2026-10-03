import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';
import prettier from 'prettier';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const specPath = resolve(root, 'apps/server/openapi.json');
const targetPath = resolve(root, 'apps/admin/src/app/api/schema.ts');

const ast = await openapiTS(new URL(`file://${specPath}`), { rootTypes: true });
const raw = astToString(ast, { formatOptions: { removeComments: true } });
const config = await prettier.resolveConfig(targetPath);
const generated = await prettier.format(raw, { ...config, filepath: targetPath });

if (process.argv.includes('--check')) {
  const current = existsSync(targetPath) ? readFileSync(targetPath, 'utf8') : '';
  if (current !== generated) {
    console.error('apps/admin/src/app/api/schema.ts is stale, run: pnpm generate:admin-api');
    process.exit(1);
  }
  console.log('admin api types are current');
} else {
  writeFileSync(targetPath, generated);
  console.log(`wrote ${targetPath}`);
}
