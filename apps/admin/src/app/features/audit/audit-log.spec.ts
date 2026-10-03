import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer } from '../../../test-support/fake-server';
import { mount } from '../../../test-support/harness';

function serverWithAudit(count = 3): FakeServer {
  const server = new FakeServer();
  server.audit = Array.from({ length: count }, (_, index) => ({
    id: `a${index}`,
    at: `2026-10-03T10:0${index % 10}:00Z`,
    author: 'dev admin',
    action: index === 0 ? 'flag.created' : 'flag.updated',
    entityType: 'flag',
    entityKey: index % 2 === 0 ? 'checkout' : 'beta-banner',
    environment: 'dev',
    environmentVersion: index + 1,
    before: index === 0 ? null : { enabled: false, rollout: 10 },
    after: { enabled: true, rollout: 50 },
  }));
  return server;
}

describe('audit log', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists entries with author and time', async () => {
    const harness = await mount({ server: serverWithAudit() });
    await harness.go('/audit');
    expect(harness.queryAll('.entries > li')).toHaveLength(3);
    expect(harness.textOf('.entries > li')).toContain('dev admin');
    expect(harness.textOf('.entries > li')).toContain('UTC');
  });

  it('expands an entry into a before and after table with text labels', async () => {
    const harness = await mount({ server: serverWithAudit() });
    await harness.go('/audit');
    const toggles = harness.queryAll<HTMLButtonElement>('.toggle');
    expect(toggles[1]?.getAttribute('aria-expanded')).toBe('false');
    toggles[1]?.click();
    await harness.settle();
    expect(toggles[1]?.getAttribute('aria-expanded')).toBe('true');
    const text = harness.textOf('.diff');
    expect(text).toContain('enabled');
    expect(text).toContain('Changed');
    toggles[1]?.click();
    await harness.settle();
    expect(harness.queryAll('.diff')).toHaveLength(0);
  });

  it('marks added fields for a created entry', async () => {
    const harness = await mount({ server: serverWithAudit() });
    await harness.go('/audit');
    harness.queryAll<HTMLButtonElement>('.toggle')[0]?.click();
    await harness.settle();
    expect(harness.textOf('.diff')).toContain('Added');
  });

  it('filters by entity key', async () => {
    const harness = await mount({ server: serverWithAudit() });
    await harness.go('/audit');
    await harness.type('input.mono', 'beta-banner');
    expect(harness.queryAll('.entries > li')).toHaveLength(1);
    expect(harness.server.callsTo('GET', '/audit').at(-1)?.search).toContain('entity=beta-banner');
  });

  it('loads more until the log is exhausted', async () => {
    const harness = await mount({ server: serverWithAudit(30) });
    await harness.go('/audit');
    expect(harness.queryAll('.entries > li')).toHaveLength(25);
    await harness.click('.more button');
    expect(harness.queryAll('.entries > li')).toHaveLength(30);
    expect(harness.queryAll('.more')).toHaveLength(0);
  });

  it('says so when nothing matches and reports failures', async () => {
    const harness = await mount({ server: serverWithAudit(0) });
    await harness.go('/audit');
    expect(harness.textOf('.empty')).toContain('No audit entries');
    harness.server.failNext = { status: 500, problem: { title: 'Down', detail: 'database is down' } };
    await harness.click('.actions button');
    expect(harness.textOf('main [role=alert]')).toContain('audit log could not be loaded');
  });
});
