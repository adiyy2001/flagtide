import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { describe, it } from 'node:test';
import { planRequests, seed, waitForApi } from '../../../docker/seed/seed.mjs';

const adminKeys = { dev: 'fwa_dev', staging: 'fwa_staging', prod: 'fwa_prod' };

async function loadDefinitions() {
  const url = new URL('../../../docker/seed/definitions.json', import.meta.url);
  return JSON.parse(await readFile(url, 'utf8'));
}

function recordingFetcher(answer) {
  const calls = [];
  const fetcher = async (url, init = {}) => {
    calls.push({ url, method: init.method ?? 'GET', headers: init.headers, body: init.body });
    const status = answer(url, init.method ?? 'GET');
    return { ok: status < 400, status, text: async () => 'body' };
  };
  return { calls, fetcher };
}

describe('planRequests', () => {
  it('plans every segment for every environment with that environment admin key', async () => {
    const definitions = await loadDefinitions();
    const segments = planRequests(definitions, adminKeys).filter((request) => request.kind === 'segment');
    assert.equal(segments.length, definitions.segments.length * definitions.environments.length);
    segments.forEach((request) => assert.equal(request.adminKey, adminKeys[request.environment]));
  });

  it('plans each flag once with settings for every environment', async () => {
    const definitions = await loadDefinitions();
    const flags = planRequests(definitions, adminKeys).filter((request) => request.kind === 'flag');
    assert.deepEqual(
      flags.map((request) => request.key),
      definitions.flags.map((flag) => flag.key),
    );
    flags.forEach((request) => assert.equal(request.settings.length, definitions.environments.length));
  });

  it('covers all four flag types', async () => {
    const definitions = await loadDefinitions();
    const types = new Set(definitions.flags.map((flag) => flag.type));
    assert.deepEqual([...types].sort(), ['boolean', 'json', 'number', 'string']);
  });

  it('keeps every rollout summing to exactly 100000', async () => {
    const definitions = await loadDefinitions();
    const serves = definitions.flags.flatMap((flag) => [
      flag.fallthrough,
      ...flag.rules.map((rule) => rule.serve),
    ]);
    serves
      .filter((serve) => serve.rollout !== undefined)
      .forEach((serve) => {
        assert.equal(
          serve.rollout.reduce((total, entry) => total + entry.weight, 0),
          100000,
        );
      });
  });
});

describe('seed', () => {
  it('creates segments and flags on an empty server', async () => {
    const definitions = await loadDefinitions();
    const { calls, fetcher } = recordingFetcher((url, method) => {
      if (method === 'GET') {
        return 404;
      }
      return method === 'POST' ? 201 : 200;
    });
    const messages = [];
    await seed({
      api: 'http://api',
      project: 'demo',
      adminKeys,
      definitions,
      fetcher,
      log: (m) => messages.push(m),
    });
    const posts = calls.filter((call) => call.method === 'POST');
    assert.equal(posts.length, definitions.flags.length);
    const puts = calls.filter((call) => call.method === 'PUT' && call.url.includes('/flags/'));
    assert.equal(puts.length, definitions.flags.length * definitions.environments.length);
    assert.ok(messages.every((message) => message.endsWith('created')));
  });

  it('sends the bearer admin key of the environment it writes to', async () => {
    const definitions = await loadDefinitions();
    const { calls, fetcher } = recordingFetcher((url, method) => (method === 'GET' ? 404 : 200));
    await seed({ api: 'http://api', project: 'demo', adminKeys, definitions, fetcher, log: () => {} });
    const stagingCall = calls.find((call) => call.url.includes('/environments/staging/'));
    assert.equal(stagingCall.headers.Authorization, 'Bearer fwa_staging');
  });

  it('leaves existing segments and flags alone on a second run', async () => {
    const definitions = await loadDefinitions();
    const { calls, fetcher } = recordingFetcher((url, method) => (method === 'POST' ? 409 : 200));
    const messages = [];
    await seed({
      api: 'http://api',
      project: 'demo',
      adminKeys,
      definitions,
      fetcher,
      log: (m) => messages.push(m),
    });
    assert.equal(calls.filter((call) => call.method === 'PUT').length, 0);
    assert.ok(messages.every((message) => message.endsWith('kept')));
  });

  it('fails with the status when the server rejects a flag', async () => {
    const definitions = await loadDefinitions();
    const { fetcher } = recordingFetcher((url, method) => {
      if (method === 'POST') {
        return 422;
      }
      return method === 'GET' ? 404 : 200;
    });
    await assert.rejects(
      seed({ api: 'http://api', project: 'demo', adminKeys, definitions, fetcher, log: () => {} }),
      /422/,
    );
  });
});

describe('waitForApi', () => {
  it('retries until the readiness probe answers', async () => {
    let attempts = 0;
    const fetcher = async () => {
      attempts += 1;
      if (attempts < 3) {
        throw new Error('connection refused');
      }
      return { ok: true };
    };
    await waitForApi('http://api', fetcher, async () => {});
    assert.equal(attempts, 3);
  });

  it('gives up after the attempt limit', async () => {
    const fetcher = async () => ({ ok: false });
    await assert.rejects(
      waitForApi('http://api', fetcher, async () => {}),
      /did not become ready/,
    );
  });
});
