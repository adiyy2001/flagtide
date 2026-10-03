import { Bench } from 'tinybench';
import { buildScenarios } from './scenarios';

export const TARGET_MICROSECONDS = 5;

export interface ResultRow {
  readonly scenario: string;
  readonly meanMicroseconds: number;
  readonly p99Microseconds: number;
  readonly operationsPerSecond: number;
  readonly samples: number;
  readonly withinTarget: boolean;
}

export async function measureScenarios(): Promise<readonly ResultRow[]> {
  const bench = new Bench({ name: 'sdk-eval', time: 1000, warmupTime: 300 });
  for (const scenario of buildScenarios()) {
    bench.add(scenario.name, () => {
      scenario.run();
    });
  }
  await bench.run();
  return bench.tasks.map((task) => {
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
}
