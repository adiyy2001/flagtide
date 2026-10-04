import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const RETRY_DELAY_MS = 1000;
const MAX_ATTEMPTS = 60;

export function planRequests(definitions, adminKeys) {
  const [firstEnvironment] = definitions.environments;
  const requests = [];
  definitions.environments.forEach((environment) => {
    definitions.segments.forEach((segment) => {
      requests.push({ kind: 'segment', environment, key: segment.key, body: segment });
    });
  });
  definitions.flags.forEach((flag) => {
    requests.push({
      kind: 'flag',
      environment: firstEnvironment,
      key: flag.key,
      body: {
        key: flag.key,
        description: flag.description,
        type: flag.type,
        variants: flag.variants,
        offVariant: flag.offVariant,
        fallthroughVariant: flag.fallthrough.variant ?? flag.variants[0].key,
      },
      settings: definitions.environments.map((environment) => ({
        environment,
        body: {
          enabled: flag.enabled[environment] ?? false,
          offVariant: flag.offVariant,
          rules: flag.rules,
          fallthrough: flag.fallthrough,
        },
      })),
    });
  });
  return requests.map((request) => ({ ...request, adminKey: adminKeys[request.environment] }));
}

function headers(adminKey) {
  return { Authorization: `Bearer ${adminKey}`, 'Content-Type': 'application/json' };
}

async function ensureSegment(api, project, request, fetcher) {
  const url = `${api}/api/v1/projects/${project}/environments/${request.environment}/segments/${request.key}`;
  const existing = await fetcher(url, { headers: headers(request.adminKey) });
  if (existing.ok) {
    return 'kept';
  }
  const saved = await fetcher(url, {
    method: 'PUT',
    headers: headers(request.adminKey),
    body: JSON.stringify({
      name: request.body.name,
      included: request.body.included,
      excluded: request.body.excluded,
      rules: request.body.rules,
    }),
  });
  if (!saved.ok) {
    throw new Error(
      `segment ${request.key} in ${request.environment}: ${saved.status} ${await saved.text()}`,
    );
  }
  return 'created';
}

async function ensureFlag(api, project, request, adminKeys, fetcher) {
  const flagsUrl = `${api}/api/v1/projects/${project}/flags`;
  const created = await fetcher(flagsUrl, {
    method: 'POST',
    headers: headers(request.adminKey),
    body: JSON.stringify(request.body),
  });
  if (created.status === 409) {
    return 'kept';
  }
  if (!created.ok) {
    throw new Error(`flag ${request.key}: ${created.status} ${await created.text()}`);
  }
  for (const setting of request.settings) {
    const response = await fetcher(`${flagsUrl}/${request.key}/environments/${setting.environment}`, {
      method: 'PUT',
      headers: headers(adminKeys[setting.environment]),
      body: JSON.stringify(setting.body),
    });
    if (!response.ok) {
      throw new Error(
        `flag ${request.key} in ${setting.environment}: ${response.status} ${await response.text()}`,
      );
    }
  }
  return 'created';
}

export async function seed({ api, project, adminKeys, definitions, fetcher = fetch, log = console.log }) {
  const requests = planRequests(definitions, adminKeys);
  for (const request of requests.filter((candidate) => candidate.kind === 'segment')) {
    log(
      `segment ${request.key} in ${request.environment}: ${await ensureSegment(api, project, request, fetcher)}`,
    );
  }
  for (const request of requests.filter((candidate) => candidate.kind === 'flag')) {
    log(`flag ${request.key}: ${await ensureFlag(api, project, request, adminKeys, fetcher)}`);
  }
}

export async function waitForApi(
  api,
  fetcher = fetch,
  sleep = (ms) => new Promise((done) => setTimeout(done, ms)),
) {
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
    const ready = await fetcher(`${api}/q/health/ready`).then(
      (response) => response.ok,
      () => false,
    );
    if (ready) {
      return;
    }
    await sleep(RETRY_DELAY_MS);
  }
  throw new Error(`${api} did not become ready`);
}

async function main() {
  const api = (process.env.FLAGTIDE_SEED_API ?? 'http://127.0.0.1:18081').replace(/\/+$/u, '');
  const project = process.env.FLAGTIDE_SEED_PROJECT ?? 'demo';
  const adminKeys = JSON.parse(process.env.FLAGTIDE_SEED_ADMIN_KEYS ?? '{}');
  const definitions = JSON.parse(await readFile(new URL('./definitions.json', import.meta.url), 'utf8'));
  await waitForApi(api);
  await seed({ api, project, adminKeys, definitions });
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((failure) => {
    console.error(failure instanceof Error ? failure.message : failure);
    process.exit(1);
  });
}
