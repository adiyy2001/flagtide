import { execFileSync } from 'node:child_process';
import { chromium } from 'playwright';

const ports = {
  a: process.env.FLAGTIDE_PORT_SERVER_A ?? '18081',
  b: process.env.FLAGTIDE_PORT_SERVER_B ?? '18082',
  admin: process.env.FLAGTIDE_PORT_ADMIN ?? '14200',
  shop: process.env.FLAGTIDE_PORT_DEMO_SHOP ?? '14300',
};
const apiA = `http://127.0.0.1:${ports.a}`;
const apiB = `http://127.0.0.1:${ports.b}`;
const adminUrl = `http://127.0.0.1:${ports.admin}`;
const shopUrl = `http://127.0.0.1:${ports.shop}`;
const project = 'demo';
const devAdmin = 'fwa_demo_dev_admin_000000000000';
const devSdk = 'fws_demo_dev_sdk_0000000000000';
const stagingSdk = 'fws_demo_staging_sdk_000000000';
const chromePath = process.env.CHROME_PATH ?? '/usr/bin/google-chrome';
const skipOffline = process.env.FLAGTIDE_SKIP_OFFLINE === 'true';
const runId = `${Date.now().toString(36)}`;

const results = [];

async function check(name, body) {
  const started = Date.now();
  try {
    const detail = await body();
    results.push({ name, ok: true });
    console.log(`PASS ${name}${detail ? ` (${detail})` : ''} [${Date.now() - started} ms]`);
  } catch (error) {
    results.push({ name, ok: false });
    console.log(`FAIL ${name}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

function expect(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function adminHeaders(extra = {}) {
  return { Authorization: `Bearer ${devAdmin}`, 'Content-Type': 'application/json', ...extra };
}

async function api(base, path, { method = 'GET', headers = adminHeaders(), body } = {}) {
  const response = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  return { status: response.status, headers: response.headers, text, json: text ? safeJson(text) : null };
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function flagBody(key) {
  return {
    key,
    description: `must-have check ${runId}`,
    type: 'boolean',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    fallthroughVariant: 'on',
  };
}

async function createFlag(key) {
  const created = await api(apiA, `/api/v1/projects/${project}/flags`, {
    method: 'POST',
    body: flagBody(key),
  });
  expect(created.status === 201, `creating ${key} answered ${created.status}: ${created.text.slice(0, 200)}`);
}

async function toggle(base, key, enabled, revision) {
  const headers = revision === undefined ? adminHeaders() : adminHeaders({ 'If-Match': `"${revision}"` });
  return api(base, `/api/v1/projects/${project}/flags/${key}/environments/dev/enabled`, {
    method: 'PUT',
    headers,
    body: { enabled },
  });
}

function openStream(base, hello, { onFrame } = {}) {
  const socket = new WebSocket(`${base.replace('http', 'ws')}/sdk/v1/stream`);
  const frames = [];
  const waiters = [];
  const closed = new Promise((resolve) => {
    socket.addEventListener('close', (event) => resolve(event.code));
  });
  socket.addEventListener('open', () => socket.send(JSON.stringify(hello)));
  socket.addEventListener('message', (event) => {
    const frame = JSON.parse(String(event.data));
    frames.push(frame);
    onFrame?.(frame, socket);
    waiters.splice(0).forEach((resolve) => resolve());
  });
  async function waitFor(predicate, timeoutMs = 10000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const found = frames.find(predicate);
      if (found) {
        return found;
      }
      const remaining = deadline - Date.now();
      expect(
        remaining > 0,
        `no matching frame within ${timeoutMs} ms, saw ${frames.map((f) => f.t).join(',')}`,
      );
      await new Promise((resolve) => {
        waiters.push(resolve);
        setTimeout(resolve, remaining);
      });
    }
  }
  return { socket, frames, closed, waitFor, close: () => socket.close() };
}

async function stackIsUp() {
  const raw = execFileSync('docker', ['compose', 'ps', '--format', 'json', '--all'], { encoding: 'utf8' });
  return raw
    .trim()
    .split('\n')
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

await check('docker compose runs postgres, two server instances, admin and demo shop', async () => {
  const services = await stackIsUp();
  const running = new Set(
    services.filter((s) => s.State === 'running' && s.Health !== 'unhealthy').map((s) => s.Service),
  );
  const missing = ['postgres', 'server-a', 'server-b', 'admin', 'demo-shop'].filter((s) => !running.has(s));
  expect(missing.length === 0, `not running: ${missing.join(', ')}`);
  return [...running].sort().join(', ');
});

await check('admin and demo shop answer over HTTP', async () => {
  const admin = await fetch(adminUrl);
  const shop = await fetch(shopUrl);
  expect(admin.status === 200, `admin answered ${admin.status}`);
  expect(shop.status === 200, `shop answered ${shop.status}`);
});

await check('OpenAPI document is served and describes the admin API', async () => {
  const response = await api(apiA, '/q/openapi?format=json', { headers: {} });
  expect(response.status === 200, `status ${response.status}`);
  const paths = Object.keys(response.json?.paths ?? {});
  expect(paths.includes('/api/v1/projects/{project}/flags'), 'flags path missing');
  expect(paths.includes('/sdk/v1/snapshot'), 'snapshot path missing');
  return `${paths.length} paths`;
});

await check(
  'API keys: no key 401, SDK key on the admin API 403 for reads and writes, unknown stream key closes 4401',
  async () => {
    const flags = `/api/v1/projects/${project}/flags`;
    const anonymous = await api(apiA, flags, { headers: {} });
    expect(anonymous.status === 401, `anonymous answered ${anonymous.status}`);
    const withSdk = await api(apiA, flags, { headers: { Authorization: `Bearer ${devSdk}` } });
    expect(withSdk.status === 403, `sdk key answered ${withSdk.status}`);
    const sdkWrite = await api(apiA, flags, {
      method: 'POST',
      headers: { Authorization: `Bearer ${devSdk}`, 'Content-Type': 'application/json' },
      body: flagBody('never-created'),
    });
    expect(sdkWrite.status === 403, `sdk key write answered ${sdkWrite.status}`);
    const stream = openStream(apiA, { t: 'hello', sdkKey: 'fws_not_a_real_key' });
    const code = await stream.closed;
    expect(code === 4401, `unknown key closed with ${code}`);
  },
);

const conflictFlag = `must-have-conflict-${runId}`;
await check('optimistic concurrency: a stale If-Match answers 409 with problem details', async () => {
  await createFlag(conflictFlag);
  const first = await toggle(apiA, conflictFlag, true, 1);
  expect(first.status === 200, `first toggle answered ${first.status}`);
  const stale = await toggle(apiB, conflictFlag, false, 1);
  expect(stale.status === 409, `stale toggle answered ${stale.status}`);
  expect(stale.text.includes('urn:flagtide:problem'), 'conflict is not a problem document');
});

await check('flag invariant: a rollout that does not sum to 100 percent is rejected', async () => {
  const response = await api(apiA, `/api/v1/projects/${project}/flags/${conflictFlag}/environments/dev`, {
    method: 'PUT',
    body: {
      enabled: true,
      offVariant: 'off',
      fallthrough: {
        rollout: [
          { variant: 'on', weight: 60000 },
          { variant: 'off', weight: 30000 },
        ],
      },
      rules: [],
    },
  });
  expect(response.status === 400 || response.status === 422, `answered ${response.status}`);
});

await check('snapshot answers 304 for its own version and carries the flag', async () => {
  const first = await api(apiB, '/sdk/v1/snapshot', { headers: { Authorization: `Bearer ${devSdk}` } });
  expect(first.status === 200, `status ${first.status}`);
  expect(first.text.includes(conflictFlag), 'snapshot on instance B lacks a flag created through instance A');
  const etag = first.headers.get('etag') ?? '';
  const again = await api(apiB, '/sdk/v1/snapshot', {
    headers: { Authorization: `Bearer ${devSdk}`, 'If-None-Match': etag },
  });
  expect(again.status === 304, `conditional request answered ${again.status}`);
});

const deltaFlag = `must-have-deltas-${runId}`;
await check('reconnect with an old version receives exactly the missed deltas', async () => {
  await createFlag(deltaFlag);
  const first = openStream(apiA, { t: 'hello', sdkKey: devSdk });
  const snapshot = await first.waitFor((frame) => frame.t === 'snapshot');
  first.close();
  await first.closed;
  const startVersion = snapshot.v;
  const one = await toggle(apiA, deltaFlag, true, 1);
  const two = await toggle(apiA, deltaFlag, false, 2);
  expect(one.status === 200 && two.status === 200, `toggles answered ${one.status} and ${two.status}`);
  const second = openStream(apiB, { t: 'hello', sdkKey: devSdk, version: startVersion });
  const deltas = await second.waitFor((frame) => frame.t === 'deltas');
  second.close();
  expect(deltas.from === startVersion, `from ${deltas.from} instead of ${startVersion}`);
  expect(deltas.entries.length === 2, `${deltas.entries.length} entries instead of 2`);
  expect(deltas.to === startVersion + 2, `to ${deltas.to}`);
  return `versions ${startVersion} to ${deltas.to}`;
});

await check(
  'snapshot fallback: no version and a version ahead of the server both get a snapshot',
  async () => {
    const none = openStream(apiA, { t: 'hello', sdkKey: devSdk });
    await none.waitFor((frame) => frame.t === 'snapshot');
    none.close();
    const ahead = openStream(apiA, { t: 'hello', sdkKey: devSdk, version: 999999999 });
    const frame = await ahead.waitFor((candidate) => candidate.t === 'snapshot' || candidate.t === 'deltas');
    ahead.close();
    expect(frame.t === 'snapshot', `answered with ${frame.t}`);
  },
);

await check('environment isolation: a staging SDK key never receives dev changes', async () => {
  const staging = openStream(apiA, { t: 'hello', sdkKey: stagingSdk });
  await staging.waitFor((frame) => frame.t === 'snapshot');
  const before = staging.frames.length;
  await toggle(apiA, deltaFlag, true);
  await new Promise((resolve) => setTimeout(resolve, 1000));
  const leaked = staging.frames
    .slice(before)
    .filter((frame) => frame.t === 'deltas' && frame.entries.length > 0);
  staging.close();
  expect(leaked.length === 0, 'staging socket received a dev delta');
});

await check('two instances: a change made on A reaches clients of A and of B', async () => {
  const key = `must-have-fanout-${runId}`;
  await createFlag(key);
  const onA = openStream(apiA, { t: 'hello', sdkKey: devSdk });
  const onB = openStream(apiB, { t: 'hello', sdkKey: devSdk });
  const [snapshotA, snapshotB] = await Promise.all([
    onA.waitFor((frame) => frame.t === 'snapshot'),
    onB.waitFor((frame) => frame.t === 'snapshot'),
  ]);
  const wanted = Math.max(snapshotA.v, snapshotB.v) + 1;
  const startedAt = Date.now();
  const response = await toggle(apiA, key, true);
  expect(response.status === 200, `toggle answered ${response.status}`);
  const reaches = (frame) =>
    frame.t === 'deltas' && frame.entries.some((entry) => entry.changes.some((change) => change.key === key));
  const [deltaA, deltaB] = await Promise.all([onA.waitFor(reaches), onB.waitFor(reaches)]);
  const elapsed = Date.now() - startedAt;
  onA.close();
  onB.close();
  expect(deltaA.to >= wanted && deltaB.to >= wanted, 'delta versions did not advance');
  return `both clients informed within ${elapsed} ms`;
});

await check('audit log records author, before and after for the change', async () => {
  const audit = await api(apiA, `/api/v1/projects/${project}/audit`);
  expect(audit.status === 200, `status ${audit.status}`);
  expect(audit.text.includes(deltaFlag), 'audit lacks the flag');
  const entry = (Array.isArray(audit.json) ? audit.json : []).find((candidate) =>
    JSON.stringify(candidate).includes(deltaFlag),
  );
  const serialized = JSON.stringify(entry ?? {});
  expect(/before/.test(serialized) && /after/.test(serialized), 'entry has no before and after');
});

await check('propagation monitor endpoint reports p50, p95 and p99', async () => {
  const response = await api(apiA, `/api/v1/projects/${project}/environments/dev/propagation`);
  expect(response.status === 200, `status ${response.status}`);
  const keys = Object.keys(response.json ?? {}).join(',');
  expect(
    /p50/.test(response.text) && /p95/.test(response.text) && /p99/.test(response.text),
    `fields: ${keys}`,
  );
});

await check('SSR: the HTML from the shop already contains the flagged content and the snapshot', async () => {
  const response = await fetch(`${shopUrl}/?visitor=visitor-beta`);
  const html = await response.text();
  expect(response.status === 200, `status ${response.status}`);
  expect(html.includes('data-testid="promo-banner"'), 'banner missing from the server render');
  expect(html.includes('data-testid="nav-recommendations"'), 'beta link missing for the beta visitor');
  expect(html.includes('ng-state'), 'no TransferState payload');
  const other = await (await fetch(`${shopUrl}/?visitor=somebody-else`)).text();
  expect(
    !other.includes('data-testid="nav-recommendations"'),
    'beta link rendered for a visitor outside the segment',
  );
  const missing = await fetch(`${shopUrl}/no-such-page`);
  expect(missing.status === 404, `unknown path answered ${missing.status}`);
});

const browser = await chromium.launch({ executablePath: chromePath, headless: true });
try {
  await check('status signal and overrides panel work in the production shop', async () => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(shopUrl);
    await page.getByText('Flags: Live').waitFor({ timeout: 15000 });
    await page.getByTestId('promo-banner').waitFor();
    await page.getByRole('button', { name: /^Flags/ }).click();
    const overrideSwitch = page.getByRole('switch', { name: 'Override promo-banner' });
    await overrideSwitch.focus();
    await page.keyboard.press('Space');
    await page.getByTestId('promo-banner').waitFor({ state: 'detached', timeout: 5000 });
    await page.getByText('OVERRIDE').first().waitFor();
    await context.close();
  });

  await check('a flag change reaches an open shop tab through the socket', async () => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.goto(shopUrl);
    await page.getByText('Flags: Live').waitFor({ timeout: 15000 });
    await page.getByTestId('promo-banner').waitFor();
    const startedAt = Date.now();
    const off = await api(apiA, `/api/v1/projects/${project}/flags/promo-banner/environments/dev/enabled`, {
      method: 'PUT',
      body: { enabled: false },
    });
    expect(off.status === 200, `disable answered ${off.status}`);
    await page.getByTestId('promo-banner').waitFor({ state: 'detached', timeout: 5000 });
    const elapsed = Date.now() - startedAt;
    await api(apiA, `/api/v1/projects/${project}/flags/promo-banner/environments/dev/enabled`, {
      method: 'PUT',
      body: { enabled: true },
    });
    await page.getByTestId('promo-banner').waitFor({ timeout: 5000 });
    await context.close();
    return `${elapsed} ms from the REST call to the DOM change`;
  });

  if (skipOffline) {
    console.log('SKIP offline start (FLAGTIDE_SKIP_OFFLINE=true)');
  } else {
    await check(
      'offline start: the shop renders from the stored snapshot while both servers are down',
      async () => {
        const context = await browser.newContext();
        const page = await context.newPage();
        const visit = `${shopUrl}/?visitor=visitor-beta`;
        await page.goto(visit);
        await page.getByText('Flags: Live').waitFor({ timeout: 15000 });
        await page.getByTestId('promo-banner').waitFor();
        try {
          execFileSync('docker', ['compose', 'stop', 'server-a', 'server-b'], { stdio: 'ignore' });
          await page.goto(visit);
          await page.getByTestId('promo-banner').waitFor({ timeout: 15000 });
          await page.getByTestId('nav-recommendations').waitFor();
          const status = await page.getByRole('status').first().innerText();
          expect(/Cached flags|Offline|Stale/i.test(status), `status reads "${status}"`);
          return `status "${status.trim()}"`;
        } finally {
          execFileSync('docker', ['compose', 'start', 'server-a', 'server-b'], { stdio: 'ignore' });
          await context.close();
          await waitForHealthy();
        }
      },
    );
  }
} finally {
  await browser.close();
}

async function waitForHealthy() {
  const deadline = Date.now() + 120000;
  while (Date.now() < deadline) {
    const [a, b] = await Promise.all(
      [apiA, apiB].map((base) =>
        fetch(`${base}/q/health/ready`).then(
          (response) => response.ok,
          () => false,
        ),
      ),
    );
    if (a && b) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
}

const failed = results.filter((result) => !result.ok);
console.log(`\n${results.length - failed.length} of ${results.length} must-have checks passed`);
process.exit(failed.length === 0 ? 0 : 1);
