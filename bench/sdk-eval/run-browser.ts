import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { build } from 'vite';
import { describeHardware } from './hardware';
import { TARGET_MICROSECONDS } from './measure';
import type { ResultRow } from './measure';
import { printRows } from './print-table';

const here = dirname(fileURLToPath(import.meta.url));
const resultPath = resolve(here, '../results/sdk-eval-chromium.json');

async function bundle(): Promise<string> {
  const output = await build({
    root: here,
    logLevel: 'error',
    configFile: false,
    build: {
      write: false,
      minify: false,
      lib: { entry: resolve(here, 'browser-entry.ts'), formats: ['iife'], name: 'SdkEval' },
    },
  });
  const bundles = Array.isArray(output) ? output : [output];
  const chunk = bundles
    .flatMap((entry) => ('output' in entry ? entry.output : []))
    .find((item) => item.type === 'chunk');
  if (chunk === undefined || chunk.type !== 'chunk') {
    throw new Error('the browser bundle produced no chunk');
  }
  return chunk.code;
}

async function main(): Promise<void> {
  const code = await bundle();
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    await page.setContent('<!doctype html><title>sdk-eval</title>');
    await page.addScriptTag({ content: code });
    const userAgent = await page.evaluate(() => navigator.userAgent);
    const rows = (await page.evaluate(() => window.runSdkEval())) as readonly ResultRow[];
    const hardware = describeHardware();
    const report = {
      benchmark: 'sdk-eval',
      runtime: 'chromium',
      browserVersion: browser.version(),
      userAgent,
      note: 'performance.now() is clamped in a page that is not cross-origin isolated, so single call latencies fall below the timer resolution and p99 reads 0. The mean is taken over many calls and is reliable.',
      targetMicroseconds: TARGET_MICROSECONDS,
      hardware,
      results: rows,
    };
    console.log(`cpu       ${hardware.cpuModel} (${hardware.logicalCores} logical cores)`);
    console.log(`memory    ${hardware.memoryGiB} GiB`);
    console.log(`runtime   headless chromium ${browser.version()}`);
    console.log(`os        ${hardware.os}`);
    printRows(rows);
    mkdirSync(dirname(resultPath), { recursive: true });
    writeFileSync(resultPath, `${JSON.stringify(report, null, 2)}\n`);
    console.log(`wrote ${resultPath}`);
  } finally {
    await browser.close();
  }
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
