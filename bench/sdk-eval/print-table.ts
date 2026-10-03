import type { ResultRow } from './measure';

export function printRows(rows: readonly ResultRow[]): void {
  console.table(
    rows.map((row) => ({
      scenario: row.scenario,
      'mean us': row.meanMicroseconds,
      'p99 us': row.p99Microseconds,
      'ops/s': row.operationsPerSecond,
      'within 5 us': row.withinTarget,
    })),
  );
}
