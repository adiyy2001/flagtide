import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const results = join(root, 'bench/results');
const badgePath = join(root, 'docs/media/coverage.svg');

function load(name) {
  return JSON.parse(readFileSync(join(results, name), 'utf8'));
}

function row(cells) {
  return `| ${cells.join(' | ')} |`;
}

function table(header, rows) {
  return [row(header), row(header.map(() => '---')), ...rows.map(row)].join('\n');
}

function badge(label, value, color) {
  const labelWidth = 63;
  const valueWidth = 8 * value.length + 14;
  const width = labelWidth + valueWidth;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="20" role="img" aria-label="${label}: ${value}"><title>${label}: ${value}</title><clipPath id="round"><rect width="${width}" height="20" rx="3"/></clipPath><g clip-path="url(#round)"><rect width="${labelWidth}" height="20" fill="#555"/><rect x="${labelWidth}" width="${valueWidth}" height="20" fill="${color}"/></g><g fill="#fff" font-family="Verdana,DejaVu Sans,sans-serif" font-size="11" text-anchor="middle"><text x="${labelWidth / 2}" y="14">${label}</text><text x="${labelWidth + valueWidth / 2}" y="14">${value}</text></g></svg>\n`;
}

function hardware(info) {
  return `${info.cpuModel}, ${info.logicalCores} logical cores, ${info.memoryGiB} GiB RAM, ${info.os}`;
}

const load5000 = load('propagation-k6.json');
const sdkNode = load('sdk-eval-node.json');
const sdkChromium = load('sdk-eval-chromium.json');
const lighthouse = load('lighthouse.json');
const coverage = load('coverage.json');
const counts = load('test-counts.json');

const out = [];
out.push(`Hardware: ${hardware(load5000.hardware)}, Docker ${load5000.hardware.docker}`);
out.push(
  `Node ${sdkNode.hardware.node}, V8 ${sdkNode.hardware.v8}, Chromium ${sdkChromium.browserVersion} (headless)`,
);
out.push('');
out.push('Propagation, k6 against the compose stack');
out.push(
  table(
    ['Clients', 'Instances', 'Changes', 'Frames received', 'p50 ms', 'p95 ms', 'p99 ms', 'max ms'],
    [
      [
        load5000.setup.clients,
        load5000.setup.instances,
        load5000.setup.rounds,
        load5000.deltaFramesReceived,
        load5000.commitToReceiptMilliseconds.p50,
        load5000.commitToReceiptMilliseconds.p95,
        load5000.commitToReceiptMilliseconds.p99,
        load5000.commitToReceiptMilliseconds.max,
      ],
    ],
  ),
);
out.push(
  `Server side acknowledgement over ${load5000.serverSideAcknowledgements.samples} samples: p50 ${load5000.serverSideAcknowledgements.p50Millis} ms, p95 ${load5000.serverSideAcknowledgements.p95Millis} ms, p99 ${load5000.serverSideAcknowledgements.p99Millis} ms. Sockets connected ${load5000.socketsConnected}, failed ${load5000.socketsFailed}. Target p95 under ${load5000.targetP95Milliseconds} ms: ${load5000.withinTarget ? 'met' : 'missed'}.`,
);
out.push('');
out.push('SDK evaluation per flag');
out.push(
  table(
    ['Runtime', 'Scenario', 'Mean us', 'p99 us', 'Evaluations per second'],
    [sdkNode, sdkChromium].flatMap((file) =>
      file.results.map((entry) => [
        file.runtime,
        entry.scenario,
        entry.meanMicroseconds,
        entry.p99Microseconds,
        entry.operationsPerSecond.toLocaleString('en-US'),
      ]),
    ),
  ),
);
out.push('');
out.push(
  `Lighthouse ${lighthouse.lighthouseVersion}, accessibility floor ${lighthouse.minimumAccessibility}`,
);
out.push(
  table(
    ['Page', 'Accessibility', 'Best practices', 'Failing audits'],
    lighthouse.pages.map((page) => [
      page.page,
      page.accessibility,
      page.bestPractices,
      page.failingAudits.length,
    ]),
  ),
);
out.push('');
out.push('Coverage, lines');
out.push(
  table(
    ['Area', 'Lines %', 'Branches %'],
    [
      ...coverage.web.map((entry) => [entry.project, entry.lines, entry.branches]),
      ...coverage.java.map((entry) => [`server ${entry.layer}`, entry.lines, entry.branches]),
    ],
  ),
);
out.push('');
out.push('Tests');
out.push(
  table(
    ['Suite', 'Tests'],
    [
      ...Object.entries(counts.web).map(([name, total]) => [`web ${name}`, total]),
      ...Object.entries(counts.java).map(([name, totals]) => [`server ${name}`, totals.tests]),
      ['server total', counts.javaTotal],
      ['conformance cases, all kinds', counts.vectorCases.total],
      ['conformance cases, evaluation', counts.vectorCases.evaluation],
    ],
  ),
);
console.log(out.join('\n'));

const lowest = Math.min(
  ...coverage.web.map((entry) => entry.lines),
  ...coverage.java.map((entry) => entry.lines),
);
mkdirSync(join(root, 'docs/media'), { recursive: true });
writeFileSync(badgePath, badge('coverage', `${lowest}% lines or more`, '#2e7d32'));
