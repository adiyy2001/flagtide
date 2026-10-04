import { execFileSync } from 'node:child_process';
import { mkdirSync, readdirSync, renameSync, rmSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';
import { startHarness } from '../apps/e2e/harness/server.mjs';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const apiB = process.env.API_B ?? 'http://127.0.0.1:18082';
const shopUrl = process.env.SHOP_URL ?? 'http://127.0.0.1:14300';
const sdkKey = process.env.SDK_KEY ?? 'fws_demo_dev_sdk_0000000000000';
const output = resolve(root, process.argv[2] ?? 'docs/media/demo.gif');
const workDir = resolve(root, 'tmp/record-demo');
const flag = 'beta-recommendations';
const apiA = process.env.API_A ?? 'http://127.0.0.1:18081';
const adminKey = process.env.ADMIN_KEY ?? 'fwa_demo_dev_admin_000000000000';
const flagUrl = `${apiA}/api/v1/projects/demo/flags/${flag}`;
const adminHeaders = { Authorization: `Bearer ${adminKey}`, 'Content-Type': 'application/json' };
const size = { width: 1440, height: 810 };
const gifWidth = Number(process.env.GIF_WIDTH ?? 960);
const gifFps = Number(process.env.GIF_FPS ?? 10);

async function visitorInTheMiddleOfTheRollout() {
  const core = await import(resolve(root, 'dist/libs/core/index.js'));
  const response = await fetch(`${apiB}/sdk/v1/snapshot`, { headers: { Authorization: `Bearer ${sdkKey}` } });
  const snapshot = await response.json();
  const salt = snapshot.flags.find((entry) => entry.key === flag).salt;
  for (let index = 0; index < 100000; index += 1) {
    const key = `visitor-${index}`;
    const bucket = core.bucketOf(flag, salt, key);
    if (bucket >= 45000 && bucket < 55000) {
      return key;
    }
  }
  throw new Error('no visitor found');
}

async function readSettings() {
  const response = await fetch(flagUrl, { headers: adminHeaders });
  const { enabled, offVariant, rules, fallthrough } = (await response.json()).environments.dev;
  return { enabled, offVariant, rules, fallthrough };
}

async function restoreSettings(settings) {
  const response = await fetch(`${flagUrl}/environments/dev`, {
    method: 'PUT',
    headers: adminHeaders,
    body: JSON.stringify(settings),
  });
  if (!response.ok) {
    throw new Error(`restoring ${flag} answered ${response.status}`);
  }
}

async function caption(page, text) {
  await page.evaluate((message) => {
    let element = document.getElementById('caption');
    if (!element) {
      element = document.createElement('div');
      element.id = 'caption';
      element.setAttribute(
        'style',
        'position:fixed;left:50%;bottom:14px;transform:translateX(-50%);z-index:10;padding:8px 16px;border-radius:8px;background:#111827;color:#fff;font:600 15px system-ui,sans-serif;box-shadow:0 2px 8px rgba(0,0,0,.4)',
      );
      document.body.append(element);
    }
    element.textContent = message;
  }, text);
}

async function pause(page, milliseconds) {
  await page.waitForTimeout(milliseconds);
}

async function run() {
  rmSync(workDir, { recursive: true, force: true });
  mkdirSync(workDir, { recursive: true });
  const visitor = await visitorInTheMiddleOfTheRollout();
  const original = await readSettings();
  const harness = await startHarness({ shopUrl });
  const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true });
  const context = await browser.newContext({
    viewport: size,
    recordVideo: { dir: workDir, size },
  });
  const page = await context.newPage();
  try {
    await page.goto('http://127.0.0.1:14400/');
    await page.evaluate((url) => {
      document.getElementById('shop').src = url;
    }, `${shopUrl}/?visitor=${visitor}`);
    const admin = page.frameLocator('#admin');
    const shop = page.frameLocator('#shop');
    await shop.getByText('Flags: Live').waitFor();
    await admin.getByRole('row', { name: /promo-banner/ }).waitFor();
    await caption(page, 'Admin on the left, shop on the right, two server instances behind them');
    await pause(page, 2500);

    const banner = shop.getByTestId('promo-banner');
    const bannerSwitch = admin.getByRole('row', { name: /promo-banner/ }).getByRole('switch');
    await caption(page, 'Switch promo-banner off in the admin');
    await pause(page, 1200);
    await bannerSwitch.click();
    await banner.waitFor({ state: 'detached' });
    await pause(page, 2200);
    await caption(page, 'And on again');
    await bannerSwitch.click();
    await banner.waitFor();
    await pause(page, 2200);

    await caption(page, 'Open beta-recommendations and change its rollout');
    await admin.getByRole('link', { name: flag }).click();
    const percent = admin.getByLabel('Percent', { exact: true }).first();
    await percent.scrollIntoViewIfNeeded();
    await pause(page, 1500);
    const recommended = shop.getByRole('link', { name: /Recommended/ });
    await percent.fill('');
    await percent.pressSequentially('60', { delay: 150 });
    await admin.getByRole('button', { name: 'Fill' }).nth(1).click();
    await pause(page, 1200);
    await caption(page, 'Raise the rollout to 60 percent and save');
    await admin.getByRole('button', { name: 'Save Development' }).click();
    await recommended.waitFor();
    await pause(page, 2800);
    await caption(page, 'Lower it to 30 percent, the same visitor drops out');
    await percent.fill('');
    await percent.pressSequentially('30', { delay: 150 });
    await admin.getByRole('button', { name: 'Fill' }).nth(1).click();
    await admin.getByRole('button', { name: 'Save Development' }).click();
    await recommended.waitFor({ state: 'detached' });
    await pause(page, 2800);

    await caption(page, 'The propagation monitor: clients and commit to acknowledgement times');
    await admin.getByRole('link', { name: 'Propagation' }).click();
    await pause(page, 5500);
  } finally {
    await context.close();
    await browser.close();
    harness.close();
    await restoreSettings(original);
  }
  const recorded = readdirSync(workDir).find((name) => name.endsWith('.webm'));
  if (!recorded) {
    throw new Error('no video was recorded');
  }
  const video = resolve(workDir, recorded);
  const palette = resolve(workDir, 'palette.png');
  const filters = `fps=${gifFps},scale=${gifWidth}:-1:flags=lanczos`;
  execFileSync('ffmpeg', [
    '-y',
    '-loglevel',
    'error',
    '-i',
    video,
    '-vf',
    `${filters},palettegen=stats_mode=diff`,
    palette,
  ]);
  mkdirSync(resolve(output, '..'), { recursive: true });
  execFileSync('ffmpeg', [
    '-y',
    '-loglevel',
    'error',
    '-i',
    video,
    '-i',
    palette,
    '-lavfi',
    `${filters}[x];[x][1:v]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle`,
    output,
  ]);
  const megabytes = statSync(output).size / 1024 / 1024;
  console.log(`wrote ${output}, ${megabytes.toFixed(2)} MB, visitor ${visitor}`);
  if (megabytes >= 8) {
    throw new Error('the gif is 8 MB or larger');
  }
}

await run();
