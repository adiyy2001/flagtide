import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount } from '../../../test-support/harness';

describe('propagation monitor', () => {
  beforeEach(() => TestBed.resetTestingModule());
  afterEach(() => vi.useRealTimers());

  it('shows an idle message while nothing has been acknowledged', async () => {
    const harness = await mount();
    await harness.go('/propagation');
    expect(harness.textOf('.empty')).toContain('No acknowledgements yet');
    expect(harness.textOf('.stats')).toContain('Connected clients');
    expect(harness.queryAll('.chart svg')).toHaveLength(0);
  });

  it('draws the chart and a data table once samples arrive', async () => {
    const harness = await mount();
    harness.server.propagation = {
      connectedClients: 12,
      samples: 40,
      p50Millis: 8.2,
      p95Millis: 21,
      p99Millis: 140,
    };
    await harness.go('/propagation');
    expect(harness.textOf('.stats')).toContain('12');
    expect(harness.textOf('.stats')).toContain('8.2 ms');
    expect(harness.textOf('.stats')).toContain('140 ms');
    expect(harness.queryAll('svg path.series')).toHaveLength(3);
    expect(harness.queryAll('.data tbody tr')).toHaveLength(1);
    expect(harness.query('.chart svg').getAttribute('role')).toBe('img');
  });

  it('polls every second and appends to the history', async () => {
    const harness = await mount();
    harness.server.propagation = {
      connectedClients: 2,
      samples: 5,
      p50Millis: 5,
      p95Millis: 9,
      p99Millis: 12,
    };
    await harness.go('/propagation');
    const before = harness.server.callsTo('GET', '/propagation').length;
    await new Promise<void>((resolve) => setTimeout(resolve, 2200));
    await harness.tick();
    expect(harness.server.callsTo('GET', '/propagation').length).toBeGreaterThanOrEqual(before + 2);
    expect(harness.queryAll('.data tbody tr').length).toBeGreaterThanOrEqual(2);
  });

  it('stops polling when the page is left', async () => {
    const harness = await mount();
    await harness.go('/propagation');
    await harness.go('/flags');
    const count = harness.server.callsTo('GET', '/propagation').length;
    await new Promise<void>((resolve) => setTimeout(resolve, 1300));
    expect(harness.server.callsTo('GET', '/propagation')).toHaveLength(count);
  });

  it('shows the failure and recovers on the next poll', async () => {
    const harness = await mount();
    await harness.go('/propagation');
    harness.server.failNext = { status: 500, problem: { title: 'Down', detail: 'unreachable' } };
    await new Promise<void>((resolve) => setTimeout(resolve, 1200));
    await harness.tick();
    expect(harness.textOf('main [role=alert]')).toContain('could not read the server');
    await new Promise<void>((resolve) => setTimeout(resolve, 1200));
    await harness.tick();
    expect(harness.queryAll('main [role=alert]')).toHaveLength(0);
  });
});
