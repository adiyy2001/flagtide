import { TestBed } from '@angular/core/testing';
import { createMemoryStore } from '@flagtide/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer } from '../../test-support/fake-server';
import { AdminApi } from './admin-api';
import { ApiError, MissingKeyError } from './problem';
import { RUNTIME_CONFIG } from './runtime-config';
import { FETCH, LOCAL_STORE } from './tokens';

function setup(adminKeys: Record<string, string> = { dev: 'dev-key' }) {
  const server = new FakeServer();
  TestBed.configureTestingModule({
    providers: [
      { provide: RUNTIME_CONFIG, useValue: { apiUrl: 'http://api', project: 'demo', adminKeys } },
      { provide: LOCAL_STORE, useValue: createMemoryStore() },
      { provide: FETCH, useValue: server.fetch },
    ],
  });
  return { api: TestBed.inject(AdminApi), server };
}

describe('AdminApi', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists flags with the filter in the query and any key for reads', async () => {
    const { api, server } = setup();
    const flags = await api.listFlags({ text: 'check', type: 'boolean' });
    expect(flags.map((flag) => flag.key)).toEqual(['checkout']);
    const call = server.calls[0];
    expect(call?.search).toContain('q=check');
    expect(call?.search).toContain('type=boolean');
    expect(call?.authorization).toBe('Bearer dev-key');
  });

  it('writes with the key of the environment and sends If-Match', async () => {
    const { api, server } = setup({ dev: 'dev-key', prod: 'prod-key' });
    const flag = await api.setEnabled('prod', 'checkout', true, 4);
    expect(flag.environments['prod']?.enabled).toBe(true);
    const call = server.calls[0];
    expect(call?.authorization).toBe('Bearer prod-key');
    expect(call?.ifMatch).toBe('"4"');
    expect(call?.method).toBe('PUT');
  });

  it('refuses a write when the environment has no admin key', async () => {
    const { api, server } = setup({ dev: 'dev-key' });
    await expect(api.setEnabled('prod', 'checkout', true, null)).rejects.toBeInstanceOf(MissingKeyError);
    expect(server.calls).toHaveLength(0);
  });

  it('raises an ApiError with the problem on 409', async () => {
    const { api, server } = setup();
    server.conflictOnNextWrite = true;
    const failure = await api
      .updateDefinition('dev', 'checkout', { description: 'x', variants: [] }, 1)
      .catch((e: unknown) => e);
    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).isConflict).toBe(true);
  });

  it('raises an ApiError with the status for an unknown flag', async () => {
    const { api } = setup();
    const failure = await api.getFlag('missing').catch((e: unknown) => e);
    expect((failure as ApiError).status).toBe(404);
  });

  it('covers kill switch, archive, segments, keys, environments, audit and propagation', async () => {
    const { api, server } = setup();
    expect((await api.setKillSwitch('dev', 'checkout', true)).environments['dev']?.killSwitch).toBe(true);
    expect((await api.setKillSwitch('dev', 'checkout', false)).environments['dev']?.killSwitch).toBe(false);
    expect((await api.archiveFlag('dev', 'checkout', null)).archived).toBe(true);
    expect(
      (
        await api.createFlag('dev', {
          key: 'fresh',
          type: 'boolean',
          description: '',
          variants: [],
          offVariant: 'off',
          fallthroughVariant: 'on',
        })
      ).key,
    ).toBe('fresh');
    expect(
      await api.configureEnvironment(
        'dev',
        'beta-banner',
        {
          enabled: false,
          offVariant: 'off',
          rules: [],
          fallthrough: { variant: 'on' },
        },
        3,
      ),
    ).toBeDefined();
    const saved = await api.saveSegment(
      'dev',
      'beta',
      { name: 'Beta', included: ['u1'], excluded: [], rules: [] },
      null,
    );
    expect(saved.key).toBe('beta');
    expect((await api.listSegments('dev')).map((segment) => segment.key)).toEqual(['beta']);
    await api.deleteSegment('dev', 'beta', null);
    expect(await api.listSegments('dev')).toEqual([]);
    expect((await api.listKeys('dev')).some((key) => key.sdkKey === 'sdk-dev')).toBe(true);
    expect((await api.createEnvironment('dev', 'qa', 'QA')).keys).toHaveLength(2);
    expect(await api.readAudit({ environment: 'dev', entity: 'x', limit: 5, offset: 0 })).toEqual([]);
    expect((await api.loadProject()).environments).toHaveLength(3);
    expect((await api.readPropagation('dev')).samples).toBe(0);
    expect(api.project).toBe('demo');
    expect(server.callsTo('POST', 'kill-switch')).toHaveLength(1);
  });
});
