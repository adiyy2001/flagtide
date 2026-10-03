import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describeHardware } from './hardware';
import { TARGET_MICROSECONDS, measureScenarios } from './measure';
import { printRows } from './print-table';

const here = dirname(fileURLToPath(import.meta.url));
const resultPath = resolve(here, '../results/sdk-eval-node.json');

async function main(): Promise<void> {
  const hardware = describeHardware();
  const rows = await measureScenarios();

  const report = {
    benchmark: 'sdk-eval',
    runtime: 'node',
    targetMicroseconds: TARGET_MICROSECONDS,
    hardware,
    heapUsedMiB: Math.round(process.memoryUsage().heapUsed / 1024 / 1024),
    results: rows,
  };

  console.log(`cpu       ${hardware.cpuModel} (${hardware.logicalCores} logical cores)`);
  console.log(`memory    ${hardware.memoryGiB} GiB`);
  console.log(`runtime   node ${hardware.node}, v8 ${hardware.v8}`);
  console.log(`os        ${hardware.os}`);
  printRows(rows);

  mkdirSync(dirname(resultPath), { recursive: true });
  writeFileSync(resultPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`wrote ${resultPath}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
