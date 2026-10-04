import { describe, expect, it } from 'vitest';
import { createFlagtideClient } from '../src/lib/client.js';
import { createMemoryStore } from '../src/lib/storage.js';
import type { DeltasFrame } from '../src/lib/protocol.js';

const baseUrl = process.env['FLAGTIDE_IT_URL'];
const project = process.env['FLAGTIDE_IT_PROJECT'] ?? 'demo';
const adminKey = process.env['FLAGTIDE_IT_ADMIN_KEY'] ?? 'fwa_demo_dev_admin_000000000000';
const sdkKey = process.env['FLAGTIDE_IT_SDK_KEY'] ?? 'fws_demo_dev_sdk_0000000000000';

async function admin(method: string, path: string, body?: unknown): Promise<Response> {
  const response = await fetch(`${baseUrl}/api/v1/projects/${project}${path}`, {
    method,
    headers: { Authorization: `Bearer ${adminKey}`, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!response.ok) {
    throw new Error(`${method} ${path} answered ${response.status}: ${await response.text()}`);
  }
  return response;
}

async function createBooleanFlag(): Promise<string> {
  const key = `it-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`;
  await admin('POST', '/flags', {
    key,
    description: 'integration test',
    type: 'boolean',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    fallthroughVariant: 'on',
  });
  return key;
}

async function setEnabled(flag: string, enabled: boolean): Promise<void> {
  await admin('PUT', `/flags/${flag}/environments/dev/enabled`, { enabled });
}

async function until(condition: () => boolean, timeoutMs = 5000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (!condition()) {
    if (Date.now() > deadline) {
      throw new Error('condition was not met in time');
    }
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
}

function streamUrl(): string {
  return `${(baseUrl ?? '').replace(/^http/, 'ws')}/sdk/v1/stream`;
}

async function firstFrameAfterHello(version: number): Promise<DeltasFrame> {
  const socket = new WebSocket(streamUrl());
  const frame = await new Promise<DeltasFrame>((resolve, reject) => {
    socket.onopen = () => socket.send(JSON.stringify({ t: 'hello', sdkKey, version }));
    socket.onmessage = (event) => resolve(JSON.parse(String(event.data)) as DeltasFrame);
    socket.onerror = () => reject(new Error('socket error'));
  });
  socket.close();
  return frame;
}

describe.skipIf(baseUrl === undefined)('SDK against a running server', () => {
  it('sees a flag change made over REST and reports how long it took', async () => {
    const flag = await createBooleanFlag();
    const client = createFlagtideClient({
      streamUrl: streamUrl(),
      sdkKey,
      context: { key: 'integration-user', attributes: {} },
      store: createMemoryStore(),
    });
    client.start();
    try {
      await until(() => client.status === 'live');
      expect(client.value(flag, true)).toBe(false);
      const changedAt = performance.now();
      await setEnabled(flag, true);
      await until(() => client.value(flag, false));
      console.log(`rest write to client evaluation: ${Math.round(performance.now() - changedAt)} ms`);
      await setEnabled(flag, false);
      await until(() => !client.value(flag, true));
    } finally {
      client.stop();
    }
  });

  it('gives a client that reconnects with an old version exactly the deltas it missed', async () => {
    const flag = await createBooleanFlag();
    const client = createFlagtideClient({
      streamUrl: streamUrl(),
      sdkKey,
      context: { key: 'integration-user', attributes: {} },
      store: createMemoryStore(),
    });
    client.start();
    await until(() => client.status === 'live' && client.version !== null);
    const known = client.version ?? 0;
    client.stop();

    await setEnabled(flag, true);
    await setEnabled(flag, false);
    await setEnabled(flag, true);

    const frame = await firstFrameAfterHello(known);
    expect(frame.t).toBe('deltas');
    expect(frame.from).toBe(known);
    expect(frame.entries.map((entry) => entry.v)).toEqual([known + 1, known + 2, known + 3]);
  });

  it('resumes a stopped client from its stored version', async () => {
    const flag = await createBooleanFlag();
    const store = createMemoryStore();
    const options = {
      streamUrl: streamUrl(),
      sdkKey,
      context: { key: 'integration-user', attributes: {} },
      store,
    };
    const first = createFlagtideClient(options);
    first.start();
    await until(() => first.status === 'live');
    first.stop();
    await setEnabled(flag, true);

    const second = createFlagtideClient(options);
    expect(second.status).toBe('stale');
    second.start();
    try {
      await until(() => second.status === 'live');
      expect(second.value(flag, false)).toBe(true);
    } finally {
      second.stop();
    }
  });
});
