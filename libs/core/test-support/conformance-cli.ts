import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { runConformance, summaryLine } from './conformance';

const defaultDirectory = resolve(fileURLToPath(new URL('.', import.meta.url)), '../../../spec/vectors');

function vectorsDirectory(argv: readonly string[]): string {
  const flagIndex = argv.indexOf('--vectors');
  return flagIndex >= 0 && argv[flagIndex + 1] !== undefined
    ? resolve(argv[flagIndex + 1])
    : defaultDirectory;
}

const report = runConformance(vectorsDirectory(process.argv.slice(2)));
for (const failure of report.failures.slice(0, 20)) {
  console.error(failure);
}
console.log(summaryLine(report));
process.exit(report.failures.length === 0 ? 0 : 1);
