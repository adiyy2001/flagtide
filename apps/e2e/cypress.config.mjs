import { execFile } from 'node:child_process';
import { resolve } from 'node:path';
import { promisify } from 'node:util';
import { defineConfig } from 'cypress';
import { startHarness } from './harness/server.mjs';

const run = promisify(execFile);
const root = resolve(import.meta.dirname, '../..');

const urls = {
  adminUrl: process.env.FLAGTIDE_E2E_ADMIN_URL ?? 'http://127.0.0.1:14200',
  shopUrl: process.env.FLAGTIDE_E2E_SHOP_URL ?? 'http://127.0.0.1:14300',
  apiA: process.env.FLAGTIDE_E2E_API_A ?? 'http://127.0.0.1:18081',
  apiB: process.env.FLAGTIDE_E2E_API_B ?? 'http://127.0.0.1:18082',
};

async function waitForReady(base) {
  for (let attempt = 0; attempt < 90; attempt += 1) {
    const ready = await fetch(`${base}/q/health/ready`).then(
      (response) => response.ok,
      () => false,
    );
    if (ready) {
      return true;
    }
    await new Promise((done) => setTimeout(done, 1000));
  }
  throw new Error(`${base} did not become ready`);
}

export default defineConfig({
  e2e: {
    baseUrl: urls.adminUrl,
    specPattern: 'cypress/e2e/**/*.cy.ts',
    supportFile: 'cypress/support/e2e.ts',
    chromeWebSecurity: false,
    video: false,
    screenshotsFolder: '../../tmp/e2e/screenshots',
    downloadsFolder: '../../tmp/e2e/downloads',
    defaultCommandTimeout: 8000,
    retries: { runMode: 1, openMode: 0 },
    expose: { ...urls, harnessUrl: 'http://127.0.0.1:14400' },
    async setupNodeEvents(on) {
      const harness = await startHarness({ adminUrl: urls.adminUrl, shopUrl: urls.shopUrl });
      on(
        'after:run',
        () =>
          new Promise((done) => {
            harness.close(done);
            harness.closeAllConnections();
          }),
      );
      const core = await import(resolve(root, 'dist/libs/core/index.js'));
      const remembered = new Map();
      on('task', {
        remember({ name, value }) {
          if (!remembered.has(name)) {
            remembered.set(name, value);
          }
          return remembered.get(name);
        },
        forget(name) {
          remembered.delete(name);
          return null;
        },
        evaluateFlag({ flag, segments, key, attributes }) {
          const result = core.evaluate(
            flag,
            { key, attributes: attributes ?? {} },
            core.indexSegments(segments),
          );
          return {
            variantKey: result.variantKey,
            value: result.value,
            bucket: result.bucket,
            reason: result.reason,
          };
        },
        visitorInBucketRange({ flagKey, salt, from, to }) {
          for (let index = 0; index < 100000; index += 1) {
            const key = `rollout-visitor-${index}`;
            const bucket = core.bucketOf(flagKey, salt, key);
            if (bucket >= from && bucket < to) {
              return { key, bucket };
            }
          }
          throw new Error('no visitor found in the requested bucket range');
        },
        async stopServers() {
          await run('docker', ['compose', 'stop', 'server-a', 'server-b'], { cwd: root });
          return null;
        },
        async startServers() {
          await run('docker', ['compose', 'start', 'server-a', 'server-b'], { cwd: root });
          await waitForReady(urls.apiA);
          await waitForReady(urls.apiB);
          return null;
        },
        log(message) {
          console.log(message);
          return null;
        },
      });
    },
  },
});
