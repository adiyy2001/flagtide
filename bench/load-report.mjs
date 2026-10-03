import { readFileSync, writeFileSync } from 'node:fs';
import { describeHardware } from './hardware.mjs';

const [summaryPath, outputPath] = process.argv.slice(2);
const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
const latency = summary.metrics.commit_to_receipt_ms.values;
const counter = (name) => summary.metrics[name]?.values.count ?? 0;
const options = {
  virtualUsers: Number(process.env.FLAGWIRE_LOAD_VUS ?? 50),
  socketsPerVirtualUser: Number(process.env.FLAGWIRE_LOAD_SOCKETS_PER_VU ?? 100),
  rounds: Number(process.env.FLAGWIRE_LOAD_ROUNDS ?? 30),
};

const report = {
  benchmark: 'propagation-k6',
  targetP95Milliseconds: 300,
  hardware: describeHardware(),
  setup: {
    instances: 2,
    heapPerInstance: '-Xmx512m',
    clients: options.virtualUsers * options.socketsPerVirtualUser,
    rounds: options.rounds,
    clockNote: 'k6, servers and PostgreSQL run on one host, so commit and receipt share one clock',
  },
  socketsConnected: counter('sockets_connected'),
  socketsFailed: counter('sockets_failed'),
  socketsClosedEarly: counter('sockets_closed_early'),
  deltaFramesReceived: counter('delta_frames_received'),
  commitToReceiptMilliseconds: {
    p50: latency.med,
    p90: latency['p(90)'],
    p95: latency['p(95)'],
    p99: latency['p(99)'],
    max: latency.max,
    mean: latency.avg,
  },
  withinTarget: latency['p(95)'] < 300,
};

writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);
const { hardware, commitToReceiptMilliseconds: ms } = report;
process.stdout.write(
  [
    `hardware: ${hardware.cpuModel}, ${hardware.logicalCores} logical cores, ${hardware.memoryGiB} GiB, ${hardware.os}, docker ${hardware.docker}`,
    `clients: ${report.setup.clients} over ${report.setup.instances} instances, ${report.setup.rounds} rounds`,
    `sockets connected ${report.socketsConnected}, failed ${report.socketsFailed}, closed early ${report.socketsClosedEarly}`,
    `delta frames received ${report.deltaFramesReceived}`,
    `commit to receipt: p50 ${ms.p50.toFixed(1)} ms, p95 ${ms.p95.toFixed(1)} ms, p99 ${ms.p99.toFixed(1)} ms, max ${ms.max.toFixed(1)} ms`,
    `target p95 under ${report.targetP95Milliseconds} ms: ${report.withinTarget ? 'met' : 'missed'}`,
    '',
  ].join('\n'),
);
