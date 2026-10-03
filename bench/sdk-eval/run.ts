import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Bench } from 'tinybench';
import { describeHardware } from './hardware';
import { buildScenarios } from './scenarios';

const TARGET_MICROSECONDS = 5;
const here = dirname(fileURLToPath(import.meta.url));
const resultPath = resolve(here, '../results/sdk-eval-node.json');

async function main(): Promise<void> {
  const hardware = describeHardware();
  const bench = new Bench({ name: 'sdk-eval', time: 1000, warmupTime: 300 });
  const scenarios = buildScenarios();
  for (const scenario of scenarios) {
    bench.add(scenario.name, () => {
      scenario.run();
    });
  }
  await bench.run();

  const rows = bench.tasks.map((task) => {
    const result = task.result;
    if (result === undefined || result.state !== 'completed') {
      throw new Error(`benchmark ${task.name} did not complete`);
    }
    const meanMicroseconds = result.latency.mean * 1000;
    return {
      scenario: task.name,
      meanMicroseconds: Number(meanMicroseconds.toFixed(3)),
      p99Microseconds: Number((result.latency.p99 * 1000).toFixed(3)),
      operationsPerSecond: Math.round(result.throughput.mean),
      samples: result.latency.samplesCount,
      withinTarget: meanMicroseconds <= TARGET_MICROSECONDS,
    };
  });

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
  console.table(
    rows.map((row) => ({
      scenario: row.scenario,
      'mean us': row.meanMicroseconds,
      'p99 us': row.p99Microseconds,
      'ops/s': row.operationsPerSecond,
      'within 5 us': row.withinTarget,
    })),
  );

  mkdirSync(dirname(resultPath), { recursive: true });
  writeFileSync(resultPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`wrote ${resultPath}`);
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
