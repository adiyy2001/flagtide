import { TestBed } from '@angular/core/testing';
import { createMemoryStore } from '@flagwire/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer, wireFlag } from '../../../test-support/fake-server';
import { mount } from '../../../test-support/harness';

describe('flag list', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists the flags with their state in the selected environment', async () => {
    const harness = await mount();
    await harness.go('/flags');
    expect(harness.queryAll('tbody tr')).toHaveLength(2);
    expect(harness.textOf('.count')).toBe('Showing 2 of 2 flags');
    expect(harness.textOf('tbody tr:first-child')).toContain('checkout');
  });

  it('filters by the search text and clears the filter', async () => {
    const harness = await mount();
    await harness.go('/flags');
    await harness.type('input[type=search]', 'banner');
    expect(harness.queryAll('tbody tr')).toHaveLength(1);
    expect(harness.textOf('.count')).toBe('Showing 1 of 2 flags');
    await harness.type('input[type=search]', 'zzz');
    expect(harness.textOf('.empty')).toContain('No flag matches the filter');
    await harness.click('.empty button');
    expect(harness.queryAll('tbody tr')).toHaveLength(2);
  });

  it('switches a flag through the API with its revision', async () => {
    const harness = await mount();
    await harness.go('/flags');
    const toggle = harness.query<HTMLButtonElement>('mat-slide-toggle button[role=switch]');
    toggle.click();
    await harness.settle();
    const [call] = harness.server.callsTo('PUT', '/enabled');
    expect(call?.path).toContain('/flags/checkout/environments/dev/enabled');
    expect(call?.body).toEqual({ enabled: false });
    expect(call?.ifMatch).toBe('"1"');
  });

  it('refreshes the list after a conflict on the toggle', async () => {
    const harness = await mount();
    await harness.go('/flags');
    harness.server.conflictOnNextWrite = true;
    harness.query<HTMLButtonElement>('mat-slide-toggle button[role=switch]').click();
    await harness.settle();
    expect(harness.server.callsTo('GET', '/flags').length).toBeGreaterThan(1);
  });

  it('shows the failure when the list cannot be loaded', async () => {
    const harness = await mount();
    await harness.go('/flags');
    harness.server.failNext = { status: 500, problem: { title: 'Down', detail: 'database is down' } };
    await harness.click('.actions button');
    expect(harness.textOf('main [role=alert]')).toContain('Flags could not be loaded');
  });

  it('invites the first flag when the project is empty', async () => {
    const server = new FakeServer();
    server.flags = [];
    const harness = await mount({ server });
    await harness.go('/flags');
    expect(harness.textOf('.empty')).toContain('no flags yet');
  });

  it('disables the toggle without an admin key for the selected environment', async () => {
    const store = createMemoryStore();
    store.set('flagwire.admin.environment', 'prod');
    const harness = await mount({ adminKeys: { dev: 'dev-key' }, store });
    await harness.go('/flags');
    expect(harness.query<HTMLButtonElement>('mat-slide-toggle button').disabled).toBe(true);
  });

  it('hides archived flags until the state filter asks for them', async () => {
    const server = new FakeServer();
    server.flags = [wireFlag('old', { archived: true }), wireFlag('current')];
    const harness = await mount({ server });
    await harness.go('/flags');
    expect(harness.queryAll('tbody tr')).toHaveLength(1);
    const [, state] = harness.queryAll('main mat-select');
    state?.querySelector<HTMLElement>('.mat-mdc-select-trigger')?.click();
    await harness.settle();
    const archived = harness
      .queryAll<HTMLElement>('mat-option')
      .find((option) => option.textContent?.includes('Archived'));
    archived?.click();
    await harness.settle();
    expect(harness.textOf('tbody')).toContain('old');
    expect(harness.textOf('tbody')).toContain('Archived');
  });
});
