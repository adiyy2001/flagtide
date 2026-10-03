import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { describeHardware } from './hardware.mjs';

const [directory, outputPath, minimumText] = process.argv.slice(2);
const minimum = Number(minimumText ?? '95');

const pages = readdirSync(directory)
  .filter((name) => name.endsWith('.json'))
  .sort()
  .map((name) => {
    const report = JSON.parse(readFileSync(join(directory, name), 'utf8'));
    const failing = Object.values(report.audits)
      .filter((audit) => audit.score !== null && audit.score < 1 && audit.scoreDisplayMode === 'binary')
      .map((audit) => audit.id);
    return {
      page: name.replace(/\.json$/, ''),
      url: report.finalDisplayedUrl,
      accessibility: Math.round(report.categories.accessibility.score * 100),
      bestPractices: Math.round(report.categories['best-practices'].score * 100),
      failingAudits: failing,
      lighthouse: report.lighthouseVersion,
      userAgent: report.environment.hostUserAgent,
    };
  });

const result = {
  tool: 'lighthouse',
  lighthouseVersion: pages[0]?.lighthouse ?? 'unknown',
  browser: pages[0]?.userAgent ?? 'unknown',
  hardware: describeHardware(),
  minimumAccessibility: minimum,
  pages: pages.map(({ lighthouse, userAgent, ...rest }) => rest),
};

writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);
result.pages.forEach((entry) => {
  console.log(
    `${entry.page.padEnd(22)} accessibility ${String(entry.accessibility).padStart(3)}  best practices ${String(entry.bestPractices).padStart(3)}${entry.failingAudits.length ? `  failing: ${entry.failingAudits.join(', ')}` : ''}`,
  );
});
const below = result.pages.filter((entry) => entry.accessibility < minimum);
if (pages.length === 0 || below.length > 0) {
  console.error(
    `accessibility below ${minimum} on: ${below.map((entry) => entry.page).join(', ') || 'no pages measured'}`,
  );
  process.exit(1);
}
console.log(`all ${pages.length} pages at ${minimum} or more`);
