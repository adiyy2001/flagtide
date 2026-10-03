import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { repositoryRoot, runMaven } from './lib/maven.mjs';

const MINIMUM_CASES = 200;
const SUMMARY = /conformance (\w+) cases=(\d+) passed=(\d+) failed=(\d+)/;

function vectorsDirectory(argv) {
  const index = argv.indexOf('--vectors');
  return index >= 0 && argv[index + 1] ? resolve(argv[index + 1]) : resolve(repositoryRoot, 'spec/vectors');
}

function parseSummary(output) {
  const match = SUMMARY.exec(output);
  return match === null
    ? null
    : { language: match[1], cases: Number(match[2]), passed: Number(match[3]), failed: Number(match[4]) };
}

function runTypeScript(directory) {
  const result = spawnSync(
    'pnpm',
    ['exec', 'tsx', 'libs/core/test-support/conformance-cli.ts', '--vectors', directory],
    { cwd: repositoryRoot, encoding: 'utf8' },
  );
  return { status: result.status, output: `${result.stdout}${result.stderr}` };
}

function runJava(directory) {
  const result = runMaven([
    '-pl',
    'domain',
    'test',
    '-Dtest=ConformanceTest#reportsTheSameSummaryLineAsTheOtherRunner',
    '-Dsurefire.failIfNoSpecifiedTests=false',
    '-Djacoco.skip=true',
    `-Dflagwire.vectors=${directory}`,
  ]);
  return { status: result.status, output: `${result.stdout}${result.stderr}` };
}

function failureLines(output) {
  return output
    .split('\n')
    .filter((line) => /expected .* but got|threw|unknown vector file|no vectors/.test(line))
    .slice(0, 20);
}

const directory = vectorsDirectory(process.argv.slice(2));
const runs = [runTypeScript(directory), runJava(directory)];
const summaries = runs.map((run) => parseSummary(run.output));
let failed = false;

runs.forEach((run, index) => {
  const summary = summaries[index];
  if (summary === null) {
    console.error(`runner ${index === 0 ? 'typescript' : 'java'} printed no summary`);
    console.error(run.output.split('\n').slice(-30).join('\n'));
    failed = true;
    return;
  }
  console.log(
    `conformance ${summary.language} cases=${summary.cases} passed=${summary.passed} failed=${summary.failed}`,
  );
  if (run.status !== 0 || summary.failed > 0) {
    failureLines(run.output).forEach((line) => console.error(line));
    failed = true;
  }
});

if (!failed) {
  const [typescript, java] = summaries;
  if (typescript.cases !== java.cases) {
    console.error(`case counts differ: typescript ${typescript.cases}, java ${java.cases}`);
    failed = true;
  } else if (typescript.cases < MINIMUM_CASES) {
    console.error(`only ${typescript.cases} cases, at least ${MINIMUM_CASES} are required`);
    failed = true;
  } else {
    console.log(`conformance ok: both runners agree on ${typescript.cases} cases`);
  }
}

process.exit(failed ? 1 : 0);
