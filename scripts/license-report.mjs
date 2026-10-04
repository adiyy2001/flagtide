import { existsSync, mkdirSync, readFileSync, readdirSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runMaven } from './lib/maven.mjs';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const policy = JSON.parse(readFileSync(join(root, 'scripts/license-policy.json'), 'utf8'));
const outputDirectory = join(root, 'docs/licenses');
const sourceRoots = [
  'apps/admin/src',
  'apps/demo-shop/src',
  'libs/core/src',
  'libs/angular/src',
  'libs/angular/overrides',
  'libs/angular/ssr',
];
const testFile = /(\.spec\.|-test\.ts$|\.test\.|[\\/]test-support[\\/]|[\\/]integration[\\/])/;
const importPattern = /(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g;

function walk(directory) {
  if (!existsSync(directory)) {
    return [];
  }
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'node_modules' ? [] : walk(path);
    }
    return /\.(ts|mjs)$/.test(entry.name) && !testFile.test(path) ? [path] : [];
  });
}

function packageNameOf(specifier) {
  if (specifier.startsWith('.') || specifier.startsWith('node:') || specifier.startsWith('@flagwire/')) {
    return null;
  }
  const parts = specifier.split('/');
  return specifier.startsWith('@') ? parts.slice(0, 2).join('/') : parts[0];
}

function runtimeRoots() {
  const names = new Set();
  sourceRoots
    .flatMap((directory) => walk(join(root, directory)))
    .forEach((file) => {
      for (const match of readFileSync(file, 'utf8').matchAll(importPattern)) {
        const name = packageNameOf(match[1]);
        if (name && existsSync(join(root, 'node_modules', name, 'package.json'))) {
          names.add(name);
        }
      }
    });
  const peerRoots = ['libs/core', 'libs/angular'].flatMap((directory) => {
    const manifest = JSON.parse(readFileSync(join(root, directory, 'package.json'), 'utf8'));
    return [...Object.keys(manifest.dependencies ?? {}), ...Object.keys(manifest.peerDependencies ?? {})];
  });
  peerRoots.filter((name) => !name.startsWith('@flagwire/')).forEach((name) => names.add(name));
  return [...names].sort();
}

function locate(name, fromDirectory) {
  let directory = fromDirectory;
  for (;;) {
    const candidate = join(directory, 'node_modules', name);
    if (existsSync(join(candidate, 'package.json'))) {
      return realpathSync(candidate);
    }
    const parent = dirname(directory);
    if (parent === directory) {
      return null;
    }
    directory = parent;
  }
}

function licenseOf(manifest) {
  const declared = manifest.license ?? manifest.licenses;
  if (typeof declared === 'string') {
    return declared;
  }
  if (Array.isArray(declared)) {
    return declared.map((entry) => (typeof entry === 'string' ? entry : entry.type)).join(' OR ');
  }
  return declared?.type ?? 'UNKNOWN';
}

function spdxAllowed(expression, allowed) {
  const cleaned = expression.replace(/[()]/g, ' ').trim();
  return cleaned
    .split(/\s+OR\s+/i)
    .some((alternative) => alternative.split(/\s+AND\s+/i).every((id) => allowed.includes(id.trim())));
}

function buildTimeExceptions() {
  const pnpmStore = join(root, 'node_modules/.pnpm');
  const installed = existsSync(pnpmStore) ? readdirSync(pnpmStore) : [];
  return policy.buildTimeNpmExceptions.flatMap((exception) =>
    installed
      .filter((entry) => entry.startsWith(`${exception.name}@`))
      .map((entry) => {
        const manifest = JSON.parse(
          readFileSync(join(pnpmStore, entry, 'node_modules', exception.name, 'package.json'), 'utf8'),
        );
        return {
          name: exception.name,
          version: manifest.version,
          license: licenseOf(manifest),
          expected: exception.license,
          reason: exception.reason,
        };
      }),
  );
}

function npmReport() {
  const roots = runtimeRoots();
  const seen = new Map();
  const queue = roots.map((name) => ({ name, from: root }));
  while (queue.length > 0) {
    const { name, from } = queue.shift();
    const directory = locate(name, from);
    if (!directory || seen.has(directory)) {
      continue;
    }
    const manifest = JSON.parse(readFileSync(join(directory, 'package.json'), 'utf8'));
    seen.set(directory, { name: manifest.name, version: manifest.version, license: licenseOf(manifest) });
    const optional = new Set(Object.keys(manifest.optionalDependencies ?? {}));
    const required = Object.keys({ ...manifest.dependencies, ...manifest.peerDependencies }).filter(
      (dependency) => !optional.has(dependency),
    );
    required.forEach((dependency) => queue.push({ name: dependency, from: directory }));
  }
  const packages = [...seen.values()].sort((a, b) => a.name.localeCompare(b.name));
  const rejected = packages.filter((entry) => !spdxAllowed(entry.license, policy.permissiveNpm));
  const buildTime = buildTimeExceptions();
  return { roots, packages, rejected, buildTime };
}

function normalizeMaven(name) {
  if (/apache/i.test(name)) return 'Apache-2.0';
  if (/^MIT-0/i.test(name)) return 'MIT-0';
  if (/^MIT/i.test(name)) return 'MIT';
  if (/BSD/i.test(name)) return 'BSD';
  if (/Eclipse Distribution|^EDL/i.test(name)) return 'BSD-3-Clause';
  if (/public domain/i.test(name)) return 'Public-Domain';
  if (/EPL|Eclipse Public/i.test(name)) return 'EPL';
  if (/GPL|General Public/i.test(name)) return 'GPL';
  return name;
}

const permissiveMaven = new Set([
  'Apache-2.0',
  'MIT',
  'MIT-0',
  'BSD',
  'BSD-3-Clause',
  'Public-Domain',
  'ISC',
]);

function mavenReport() {
  const scratch = join(root, 'tmp/licenses');
  mkdirSync(scratch, { recursive: true });
  const result = runMaven(
    [
      '-q',
      '-pl',
      'bootstrap',
      '-am',
      'org.codehaus.mojo:license-maven-plugin:2.7.1:aggregate-add-third-party',
      '-Dlicense.includedScopes=compile,runtime',
      `-Dlicense.outputDirectory=${scratch}`,
      '-Dlicense.failOnMissing=false',
    ],
    { stdio: 'inherit' },
  );
  if (result.status !== 0) {
    throw new Error('the Maven license plugin failed');
  }
  const lines = readFileSync(join(scratch, 'THIRD-PARTY.txt'), 'utf8').split('\n');
  const entry = /^\s+((?:\([^)]*\)\s*)+)(.*) \(([^:()\s]+:[^:()\s]+):([^\s]+) - (.*)\)\s*$/;
  const packages = lines
    .map((line) => entry.exec(line))
    .filter((match) => match !== null)
    .map((match) => ({
      id: match[3],
      version: match[4],
      name: match[2].trim(),
      licenses: [...match[1].matchAll(/\(([^)]*)\)/g)].map((license) => license[1].trim()),
    }))
    .filter((item) => !item.id.startsWith('dev.flagwire:'))
    .map((item) => {
      const normalized = item.licenses.map(normalizeMaven);
      const permissive = normalized.some((license) => permissiveMaven.has(license));
      const exception = policy.mavenExceptions.find((candidate) => item.id.startsWith(candidate.prefix));
      return {
        ...item,
        accepted: permissive ? 'permissive' : exception ? 'exception' : 'rejected',
        reason: permissive ? undefined : exception?.reason,
      };
    })
    .sort((a, b) => a.id.localeCompare(b.id));
  return { packages, rejected: packages.filter((item) => item.accepted === 'rejected') };
}

function write(name, report) {
  mkdirSync(outputDirectory, { recursive: true });
  writeFileSync(join(outputDirectory, name), `${JSON.stringify(report, null, 2)}\n`);
}

const targets = process.argv.slice(2);
const wanted = targets.length > 0 ? targets : ['npm', 'maven'];
let failed = false;

if (wanted.includes('npm')) {
  const report = npmReport();
  write('npm-runtime.json', report);
  console.log(
    `npm: ${report.packages.length} runtime packages from ${report.roots.length} roots, ${report.rejected.length} outside the policy`,
  );
  report.rejected.forEach((entry) => console.error(`  ${entry.name}@${entry.version}: ${entry.license}`));
  report.buildTime
    .filter((entry) => entry.license !== entry.expected)
    .forEach((entry) =>
      console.error(
        `  build time ${entry.name}@${entry.version}: ${entry.license}, expected ${entry.expected}`,
      ),
    );
  failed ||= report.rejected.length > 0 || report.buildTime.some((entry) => entry.license !== entry.expected);
}

if (wanted.includes('maven')) {
  const report = mavenReport();
  write('maven-runtime.json', report);
  const exceptions = report.packages.filter((item) => item.accepted === 'exception').length;
  console.log(
    `maven: ${report.packages.length} runtime artifacts, ${exceptions} under a documented exception, ${report.rejected.length} outside the policy`,
  );
  report.rejected.forEach((item) =>
    console.error(`  ${item.id}:${item.version}: ${item.licenses.join(' / ')}`),
  );
  failed ||= report.rejected.length > 0;
}

process.exit(failed ? 1 : 0);
