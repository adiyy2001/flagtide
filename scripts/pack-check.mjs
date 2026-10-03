import { spawnSync } from 'node:child_process';
import {
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  symlinkSync,
  writeFileSync,
} from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const work = resolve(root, 'tmp/pack-check');
const failures = [];

function check(condition, message) {
  if (!condition) {
    failures.push(message);
  }
}

function run(command, args, cwd) {
  const result = spawnSync(command, args, { cwd, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
  return { ok: result.status === 0, output: `${result.stdout ?? ''}${result.stderr ?? ''}` };
}

function pack(project) {
  const source = resolve(root, 'dist/libs', project);
  check(
    existsSync(resolve(source, 'package.json')),
    `${project}: dist/libs/${project} is missing, run the build first`,
  );
  const result = run('npm', ['pack', source, '--pack-destination', work, '--json', '--ignore-scripts'], work);
  check(result.ok, `${project}: npm pack failed\n${result.output}`);
  const [report] = JSON.parse(result.output.slice(result.output.indexOf('[')));
  return report;
}

function expectFiles(project, report, required, forbidden) {
  const names = report.files.map((file) => file.path);
  for (const name of required) {
    check(names.includes(name), `${project}: the tarball lacks ${name}`);
  }
  for (const pattern of forbidden) {
    const offenders = names.filter((name) => pattern.test(name));
    check(offenders.length === 0, `${project}: the tarball contains ${offenders.join(', ')}`);
  }
  check(report.size < 400_000, `${project}: the tarball is ${report.size} bytes, expected under 400000`);
  console.log(
    `${project}: ${names.length} files, ${report.size} bytes packed, ${report.unpackedSize} unpacked`,
  );
}

function checkManifest(project, manifest) {
  check(manifest.license === 'MIT', `${project}: license is not MIT`);
  check(manifest.version === '0.1.0', `${project}: unexpected version ${manifest.version}`);
  check(manifest.private !== true, `${project}: package is marked private`);
  const spec = JSON.stringify({ ...manifest.dependencies, ...manifest.peerDependencies });
  check(!/workspace:|file:|link:/u.test(spec), `${project}: a dependency points into the workspace`);
  check(typeof manifest.exports === 'object', `${project}: no exports map`);
}

rmSync(work, { recursive: true, force: true });
mkdirSync(work, { recursive: true });

const core = pack('core');
const angular = pack('angular');
const sources = /(^|\/)(test-support|integration|ssr)\/|\.spec\.|\.ssr-test\.|\.tsbuildinfo$/u;
expectFiles('core', core, ['package.json', 'README.md', 'LICENSE', 'index.js', 'index.d.ts'], [sources]);
expectFiles(
  'angular',
  angular,
  [
    'package.json',
    'README.md',
    'LICENSE',
    'fesm2022/flagwire-angular.mjs',
    'fesm2022/flagwire-angular-overrides.mjs',
    'types/flagwire-angular.d.ts',
    'types/flagwire-angular-overrides.d.ts',
  ],
  [sources],
);

const project = resolve(work, 'consumer');
const modules = resolve(project, 'node_modules');
mkdirSync(resolve(modules, '@flagwire'), { recursive: true });
for (const [name, report] of [
  ['core', core],
  ['angular', angular],
]) {
  const extract = run('tar', ['-xzf', resolve(work, report.filename), '-C', work], work);
  check(extract.ok, `${name}: could not extract the tarball\n${extract.output}`);
  cpSync(resolve(work, 'package'), resolve(modules, '@flagwire', name), { recursive: true });
  rmSync(resolve(work, 'package'), { recursive: true, force: true });
  checkManifest(name, JSON.parse(readFileSync(resolve(modules, '@flagwire', name, 'package.json'), 'utf8')));
}
for (const dependency of ['@angular', 'rxjs', 'tslib']) {
  symlinkSync(resolve(root, 'node_modules', dependency), resolve(modules, dependency), 'dir');
}
writeFileSync(
  resolve(project, 'package.json'),
  JSON.stringify({ name: 'consumer', private: true, type: 'module' }),
);

const runtime = `
import '@angular/compiler';
import * as core from '@flagwire/core';
import * as angular from '@flagwire/angular';
import * as overrides from '@flagwire/angular/overrides';

const missing = [];
const expectExports = (label, module, names) => names.forEach((name) => { if (!(name in module)) missing.push(label + '.' + name); });
expectExports('core', core, ['createFlagwireClient', 'FlagwireClient', 'evaluate', 'fetchSnapshot', 'resolveFlag', 'jsonEquals', 'murmur3x86_32', 'createMemoryStore', 'createBrowserSocketFactory']);
expectExports('angular', angular, ['provideFlagwire', 'injectFlag', 'FlagwireFlagDirective', 'flagwireGuard', 'Flagwire', 'FlagwireStatus']);
expectExports('overrides', overrides, ['FlagwireOverridesPanel']);
if (missing.length > 0) {
  console.error('missing exports: ' + missing.join(', '));
  process.exit(1);
}
const client = core.createFlagwireClient({
  streamUrl: 'ws://127.0.0.1:1/sdk/v1/stream',
  sdkKey: 'fws_pack_check',
  context: { key: 'pack-check', attributes: {} },
  store: core.createMemoryStore(),
});
client.hydrate({ version: 1, flags: [], segments: [] });
if (client.value('absent', 'fallback') !== 'fallback') {
  console.error('the packed client did not return the fallback');
  process.exit(1);
}
client.stop();
console.log('runtime imports ok');
`;
writeFileSync(resolve(project, 'runtime.mjs'), runtime);
const runtimeResult = run('node', ['runtime.mjs'], project);
check(runtimeResult.ok, `runtime import failed\n${runtimeResult.output}`);

const consumer = `
import { provideFlagwire, injectFlag, flagwireGuard, FlagwireFlagDirective, FlagwireStatus } from '@flagwire/angular';
import type { FlagwireConfig, ConnectionStatus } from '@flagwire/angular';
import { FlagwireOverridesPanel } from '@flagwire/angular/overrides';
import { createFlagwireClient, evaluate } from '@flagwire/core';
import type { FlagSnapshot, JsonValue } from '@flagwire/core';

const config: FlagwireConfig = { sdkKey: 'k', streamUrl: 'wss://example.test/sdk/v1/stream', context: { key: 'u', attributes: {} } };
export const providers = provideFlagwire(config);
export const guard = flagwireGuard('checkout', { equals: true });
export const declared = [FlagwireFlagDirective, FlagwireStatus, FlagwireOverridesPanel];
export const reader = (): boolean => injectFlag('banner', false)();
export const status = (value: ConnectionStatus): string => value;
export const snapshot = (value: FlagSnapshot): number => value.version;
export const json = (value: JsonValue): JsonValue => value;
export const client = createFlagwireClient;
export const evaluator = evaluate;
`;
writeFileSync(resolve(project, 'consumer.ts'), consumer);
writeFileSync(
  resolve(project, 'tsconfig.json'),
  JSON.stringify({
    compilerOptions: {
      target: 'ES2022',
      module: 'ESNext',
      moduleResolution: 'bundler',
      strict: true,
      noEmit: true,
      skipLibCheck: false,
      lib: ['ES2023', 'DOM'],
      types: [],
    },
    include: ['consumer.ts'],
  }),
);
const typecheck = run(resolve(root, 'node_modules/.bin/tsc'), ['-p', 'tsconfig.json'], project);
check(typecheck.ok, `the packed typings do not compile for a strict consumer\n${typecheck.output}`);

const leftovers = readdirSync(work).filter((name) => name.endsWith('.tgz'));
check(leftovers.length === 2, `expected 2 tarballs, found ${leftovers.length}`);

if (failures.length > 0) {
  console.error(`\npack-check failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}
console.log('pack-check ok');
