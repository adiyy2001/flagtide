import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const outputPath = join(root, 'bench/results/test-counts.json');
const readOnly = process.argv.includes('--read-only');
const ansi = /\x1b\[[0-9;]*m/g;

function webCounts() {
  const run = spawnSync(
    'pnpm',
    ['nx', 'run-many', '-t', 'test', '--exclude', 'server', '--skip-nx-cache', '--output-style=stream'],
    { cwd: root, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 },
  );
  if (run.status !== 0) {
    console.error(run.stdout.slice(-4000));
    throw new Error('the web tests failed');
  }
  const counts = new Map();
  run.stdout
    .replace(ansi, '')
    .split('\n')
    .forEach((line) => {
      const match = /^(\S+?):\s+Tests\s+(\d+) passed/.exec(line);
      if (match) {
        counts.set(match[1], (counts.get(match[1]) ?? 0) + Number(match[2]));
      }
    });
  return Object.fromEntries([...counts].sort(([a], [b]) => a.localeCompare(b)));
}

function summaryTotals(directory) {
  const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  readdirSync(directory)
    .filter((name) => name.endsWith('.txt'))
    .forEach((name) => {
      const line = readFileSync(join(directory, name), 'utf8').match(/Tests run: .*/)?.[0] ?? '';
      const found = {
        tests: /Tests run: (\d+)/,
        failures: /Failures: (\d+)/,
        errors: /Errors: (\d+)/,
        skipped: /Skipped: (\d+)/,
      };
      Object.entries(found).forEach(([key, pattern]) => {
        totals[key] += Number(pattern.exec(line)?.[1] ?? 0);
      });
    });
  return totals;
}

function runJavaTests() {
  const run = spawnSync('pnpm', ['nx', 'run', 'server:verify', '--skip-nx-cache'], {
    cwd: root,
    encoding: 'utf8',
    maxBuffer: 512 * 1024 * 1024,
  });
  if (run.status !== 0) {
    console.error(run.stdout.slice(-4000));
    throw new Error('the server tests failed');
  }
}

function javaCounts() {
  const serverRoot = join(root, 'apps/server');
  const modules = readdirSync(serverRoot).filter((name) => existsSync(join(serverRoot, name, 'target')));
  const result = {};
  modules.forEach((name) => {
    ['surefire-reports', 'failsafe-reports'].forEach((kind) => {
      const directory = join(serverRoot, name, 'target', kind);
      if (existsSync(directory)) {
        const totals = summaryTotals(directory);
        if (totals.tests > 0) {
          result[`${name} ${kind === 'surefire-reports' ? 'unit and component' : 'integration'}`] = totals;
        }
      }
    });
  });
  return result;
}

function vectorCases() {
  const directory = join(root, 'spec/vectors');
  const counts = { total: 0, evaluation: 0 };
  readdirSync(directory)
    .filter((name) => name.endsWith('.json'))
    .forEach((name) => {
      const parsed = JSON.parse(readFileSync(join(directory, name), 'utf8'));
      const listed = Object.values(parsed)
        .filter(Array.isArray)
        .reduce((sum, entries) => sum + entries.length, 0);
      const cases = listed + (parsed.verification ? 1 : 0);
      counts.total += cases;
      counts.evaluation += name.startsWith('eval-') ? cases : 0;
    });
  return counts;
}

if (!readOnly) {
  runJavaTests();
}
const java = javaCounts();
const javaTotal = Object.values(java).reduce((sum, entry) => sum + entry.tests, 0);
const web = readOnly ? {} : webCounts();
const report = { web, java, javaTotal, vectorCases: vectorCases() };
mkdirSync(dirname(outputPath), { recursive: true });
writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify(report, null, 2));
