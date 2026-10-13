import { createFlagtideClient, createMemoryStore } from '@flagtide/core';
import type { FlagtideClientOptions } from '@flagtide/core';
import { ErrorCode, OpenFeature, ProviderEvents, ProviderStatus } from '@openfeature/web-sdk';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FakeEnvironment, FakeSockets } from '../../../core/test-support/fake-socket.js';
import {
  booleanFlag,
  deltasFrame,
  snapshotFrame,
  upsertFlag,
  variantFlag,
} from '../../../core/test-support/fixtures.js';
import { FlagtideWebProvider } from './flagtide-web-provider.js';

function build(overrides: Partial<FlagtideClientOptions> = {}, readyTimeoutMs = 1_000) {
  const sockets = new FakeSockets();
  const environment = new FakeEnvironment();
  const client = createFlagtideClient({
    streamUrl: 'ws://test',
    sdkKey: 'fws_test',
    store: createMemoryStore(),
    socketFactory: sockets.factory,
    environment,
    context: { key: 'anonymous-1', attributes: {} },
    ...overrides,
  });
  const provider = new FlagtideWebProvider(client, { readyTimeoutMs });
  return { client, provider, sockets, environment };
}

const checkout = booleanFlag('checkout', {
  rules: [
    {
      id: 'pro-plan',
      conditions: [{ attribute: 'plan', operator: 'equals', values: ['pro'] }],
      serve: { variant: 'on' },
    },
  ],
  fallthrough: { variant: 'off' },
});

const banner = variantFlag(
  'banner',
  'string',
  [
    { key: 'plain', value: 'Welcome' },
    { key: 'sale', value: 'Sale' },
  ],
  'sale',
);

const limits = variantFlag('limits', 'json', [{ key: 'default', value: { items: 3 } }], 'default');

afterEach(async () => {
  await OpenFeature.clearProviders();
  await OpenFeature.clearContext();
  OpenFeature.clearHandlers();
});

describe('FlagtideWebProvider', () => {
  it('becomes ready on the first snapshot and evaluates every flag type through OpenFeature', async () => {
    const { provider, sockets } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(
      snapshotFrame(1, [
        checkout,
        banner,
        limits,
        variantFlag('size', 'number', [{ key: 'm', value: 2 }], 'm'),
      ]),
    );
    await ready;
    const client = OpenFeature.getClient();
    expect(client.providerStatus).toBe(ProviderStatus.READY);
    expect(client.getBooleanDetails('checkout', true)).toMatchObject({
      value: false,
      variant: 'off',
      reason: 'DEFAULT',
    });
    expect(client.getStringValue('banner', 'none')).toBe('Sale');
    expect(client.getNumberValue('size', 0)).toBe(2);
    expect(client.getObjectValue('limits', {})).toEqual({ items: 3 });
  });

  it('maps the OpenFeature context onto the flagtide context and follows context changes', async () => {
    const { provider, sockets, client } = build();
    await OpenFeature.setContext({ targetingKey: 'user-7', plan: 'free', joined: new Date(0) });
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    expect(client.context).toEqual({
      key: 'user-7',
      attributes: { plan: 'free', joined: '1970-01-01T00:00:00.000Z' },
    });
    const configurationChanged = vi.fn();
    provider.events.addHandler(ProviderEvents.ConfigurationChanged, configurationChanged);
    await OpenFeature.setContext({ targetingKey: 'user-7', plan: 'pro' });
    const details = OpenFeature.getClient().getBooleanDetails('checkout', false);
    expect(details).toMatchObject({ value: true, reason: 'TARGETING_MATCH' });
    expect(details.flagMetadata).toEqual({ ruleId: 'pro-plan', ruleIndex: 0 });
    expect(configurationChanged).not.toHaveBeenCalled();
  });

  it('falls back to the anonymous key when the context has no targeting key', async () => {
    const { provider, sockets, client } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    await OpenFeature.setContext({ targetingKey: '', plan: 'pro', tags: ['a', 'b'], nested: { x: 1 } });
    expect(client.context).toEqual({
      key: 'anonymous-1',
      attributes: { plan: 'pro', tags: ['a', 'b'], nested: { x: 1 } },
    });
  });

  it('keeps the client context when OpenFeature starts with an empty context', async () => {
    const { provider, sockets, client } = build({ context: { key: 'u1', attributes: { plan: 'pro' } } });
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    expect(client.context).toEqual({ key: 'u1', attributes: { plan: 'pro' } });
    expect(OpenFeature.getClient().getBooleanValue('checkout', false)).toBe(true);
  });

  it('reports missing flags and type mismatches as errors with the default value', async () => {
    const { provider, sockets } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    const client = OpenFeature.getClient();
    expect(client.getBooleanDetails('missing', true)).toMatchObject({
      value: true,
      reason: 'ERROR',
      errorCode: ErrorCode.FLAG_NOT_FOUND,
    });
    expect(client.getStringDetails('checkout', 'x')).toMatchObject({
      value: 'x',
      reason: 'ERROR',
      errorCode: ErrorCode.TYPE_MISMATCH,
      errorMessage: 'flag "checkout" is a boolean flag',
    });
  });

  it('reports rollouts as splits, disabled flags as disabled and overrides as overrides', async () => {
    const rollout = booleanFlag('rollout', {
      fallthrough: {
        rollout: [
          { variant: 'on', weight: 50_000 },
          { variant: 'off', weight: 50_000 },
        ],
      },
    });
    const { provider, sockets, client } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [rollout, booleanFlag('off', { enabled: false })]));
    await ready;
    const features = OpenFeature.getClient();
    const split = features.getBooleanDetails('rollout', false);
    expect(split.reason).toBe('SPLIT');
    expect(typeof split.flagMetadata['bucket']).toBe('number');
    expect(features.getBooleanDetails('off', true)).toMatchObject({ value: false, reason: 'DISABLED' });
    client.overrides.set('off', true);
    expect(features.getBooleanDetails('off', false)).toMatchObject({ value: true, reason: 'OVERRIDE' });
  });

  it('is ready at once when the client already holds a snapshot', async () => {
    const { provider, client, sockets } = build();
    client.hydrate({ version: 4, flags: [checkout], segments: [] });
    await OpenFeature.setProviderAndWait(provider);
    expect(OpenFeature.getClient().providerStatus).toBe(ProviderStatus.READY);
    expect(sockets.sockets).toHaveLength(1);
  });

  it('announces configuration changes that arrive over the stream', async () => {
    const { provider, sockets } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    const changed = vi.fn();
    OpenFeature.addHandler(ProviderEvents.ConfigurationChanged, changed);
    sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('checkout'))]));
    await vi.waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
    expect(OpenFeature.getClient().getBooleanValue('checkout', false)).toBe(true);
  });

  it('turns stale when the connection drops and ready again when it comes back', async () => {
    const { provider, sockets, environment } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    const client = OpenFeature.getClient();
    environment.goOffline();
    await vi.waitFor(() => expect(client.providerStatus).toBe(ProviderStatus.STALE));
    expect(client.getBooleanValue('checkout', true)).toBe(false);
    environment.goOnline();
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(2, [checkout]));
    await vi.waitFor(() => expect(client.providerStatus).toBe(ProviderStatus.READY));
  });

  it('fails initialization when no data arrives in time and recovers once it does', async () => {
    const { provider, sockets } = build({}, 50);
    await expect(OpenFeature.setProviderAndWait(provider)).rejects.toThrow(
      'no flag data arrived within 50 ms',
    );
    const client = OpenFeature.getClient();
    expect(client.providerStatus).toBe(ProviderStatus.ERROR);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await vi.waitFor(() => expect(client.providerStatus).toBe(ProviderStatus.READY));
  });

  it('fails fatally when the server rejects the SDK key', async () => {
    const { provider, sockets } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.closeFromServer(4401);
    await expect(ready).rejects.toThrow('The SDK key was rejected by the server');
    expect(OpenFeature.getClient().providerStatus).toBe(ProviderStatus.FATAL);
  });

  it('reports a rejected SDK key after it was ready as a fatal error event', async () => {
    const { provider, sockets } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    const errors = vi.fn();
    provider.events.addHandler(ProviderEvents.Error, errors);
    sockets.latest.closeFromServer(4401);
    await vi.waitFor(() =>
      expect(errors).toHaveBeenCalledWith(expect.objectContaining({ errorCode: ErrorCode.PROVIDER_FATAL })),
    );
  });

  it('stops the client when it is closed and can be initialized again', async () => {
    const { provider, sockets, client } = build();
    const ready = OpenFeature.setProviderAndWait(provider);
    sockets.latest.open();
    sockets.latest.receive(snapshotFrame(1, [checkout]));
    await ready;
    await OpenFeature.close();
    expect(sockets.latest.closedByClient).not.toBeNull();
    await provider.initialize();
    expect(sockets.sockets).toHaveLength(2);
    expect(client.version).toBe(1);
  });
});
