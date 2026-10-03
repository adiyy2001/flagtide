import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const adminUrl = process.env.ADMIN_URL ?? 'http://127.0.0.1:14200';
const apiUrl = process.env.API_URL ?? 'http://127.0.0.1:18081';
const adminKey = process.env.ADMIN_KEY ?? 'fwa_demo_dev_admin_000000000000';
const sdkKey = process.env.SDK_KEY ?? 'fws_demo_dev_sdk_0000000000000';
const outDir = resolve(process.argv[2] ?? 'tmp/admin-shots');
const clients = Number(process.env.CLIENTS ?? 40);
const flag = process.env.FLAG ?? 'page-size';

mkdirSync(outDir, { recursive: true });

function connect(index) {
  const socket = new WebSocket(`${apiUrl.replace('http', 'ws')}/sdk/v1/stream`);
  socket.addEventListener('open', () =>
    socket.send(JSON.stringify({ t: 'hello', sdkKey, clientId: `shot-${index}`, sdk: 'screenshots' })),
  );
  socket.addEventListener('message', (event) => {
    const frame = JSON.parse(event.data);
    if (frame.t === 'snapshot') {
      socket.send(JSON.stringify({ t: 'ack', v: frame.v }));
    } else if (frame.t === 'deltas' && frame.entries.length > 0) {
      socket.send(JSON.stringify({ t: 'ack', v: frame.to }));
    }
  });
  return socket;
}

async function flip(enabled) {
  const url = `${apiUrl}/api/v1/projects/demo/flags/${flag}/environments/dev/enabled`;
  const response = await fetch(url, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${adminKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  if (!response.ok) {
    throw new Error(`flip answered ${response.status}`);
  }
}

const sockets = Array.from({ length: clients }, (_, index) => connect(index));
await new Promise((done) => setTimeout(done, 1500));
for (let round = 0; round < 6; round += 1) {
  await flip(round % 2 === 0);
  await new Promise((done) => setTimeout(done, 400));
}

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const consoleProblems = [];
page.on('console', (message) => {
  if (['error', 'warning'].includes(message.type())) {
    consoleProblems.push(`${message.type()}: ${message.text()}`);
  }
});
page.on('pageerror', (error) => consoleProblems.push(`pageerror: ${error.message}`));

async function shot(name, fullPage = true) {
  await page.waitForTimeout(400);
  await page.screenshot({ path: `${outDir}/${name}.png`, fullPage });
  console.log(`wrote ${name}.png`);
}

await page.goto(`${adminUrl}/flags`);
await page.getByRole('table', { name: 'Flags' }).waitFor();
await shot('list');

await page.goto(`${adminUrl}/flags/checkout-redesign`);
await page.getByRole('heading', { name: 'Targeting rules' }).waitFor();
await shot('editor');

const percent = page.locator('input[id$="-percent-0"]').first();
await percent.fill('40');
await page.getByText('Over by 15%').first().waitFor();
await shot('rollout-error');

await page.goto(`${adminUrl}/audit`, { waitUntil: 'networkidle' });
await page.locator('.toggle').first().click();
await shot('audit');

await page.goto(`${adminUrl}/propagation`);
const keepAlive = setInterval(() => flip(Math.random() > 0.5).catch(() => undefined), 700);
await page.getByText('Commit to acknowledgement').waitFor();
await page.waitForTimeout(5000);
await shot('monitor');
clearInterval(keepAlive);

await page.goto(`${adminUrl}/environments`);
await page.getByRole('heading', { name: 'Keys' }).waitFor();
await shot('environments');

await page.goto(`${adminUrl}/segments`);
await page.getByRole('button', { name: /beta-users/ }).click();
await shot('segments');

await browser.close();
sockets.forEach((socket) => socket.close());
if (consoleProblems.length > 0) {
  console.log(`console problems:\n${consoleProblems.join('\n')}`);
} else {
  console.log('no console problems');
}
