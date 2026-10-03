import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { createMemoryStore } from '@flagwire/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { FakeServer } from '../test-support/fake-server';
import { mount } from '../test-support/harness';

describe('app shell', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('redirects to the flag list and marks the current page in the navigation', async () => {
    const harness = await mount();
    await harness.go('/');
    expect(TestBed.inject(Router).url).toBe('/flags');
    expect(harness.query('nav a.active').getAttribute('aria-current')).toBe('page');
    expect(harness.textOf('.project')).toBe('Demo');
  });

  it('moves focus to the main region after navigating', async () => {
    const harness = await mount();
    await harness.go('/flags');
    await harness.go('/segments');
    expect(document.activeElement).toBe(harness.query('main'));
  });

  it('lets the skip link focus the main region', async () => {
    const harness = await mount();
    harness.query<HTMLAnchorElement>('a.skip').click();
    expect(document.activeElement).toBe(harness.query('main'));
  });

  it('keeps every navigation link reachable as a link with an accessible name', async () => {
    const harness = await mount();
    const links = harness.queryAll<HTMLAnchorElement>('nav.side a');
    expect(links.map((link) => link.textContent?.trim())).toEqual([
      'Flags',
      'Segments',
      'Environments',
      'Audit log',
      'Propagation',
    ]);
    links.forEach((link) => expect(link.tabIndex).toBeGreaterThanOrEqual(0));
  });

  it('remembers the chosen environment', async () => {
    const store = createMemoryStore();
    const harness = await mount({ store });
    await harness.go('/flags');
    const select = harness.query('header mat-select');
    select.querySelector<HTMLElement>('.mat-mdc-select-trigger')?.click();
    await harness.settle();
    harness
      .queryAll<HTMLElement>('mat-option')
      .find((option) => option.textContent?.trim() === 'Production')
      ?.click();
    await harness.settle();
    expect(store.get('flagwire.admin.environment')).toBe('prod');
  });

  it('shows a banner with a retry when the API is unreachable', async () => {
    const server = new FakeServer();
    server.failNext = { status: 503, problem: { title: 'Down', detail: 'no route' } };
    const harness = await mount({ server });
    expect(harness.textOf('main .banner')).toContain('not reachable');
    harness.query<HTMLButtonElement>('main .banner button').click();
    await harness.settle();
    expect(harness.queryAll('main .banner[role=alert]')).toHaveLength(0);
  });

  it('warns when the selected environment has no admin key', async () => {
    const store = createMemoryStore();
    store.set('flagwire.admin.environment', 'prod');
    const harness = await mount({ adminKeys: { dev: 'k' }, store });
    expect(harness.textOf('.banner.warn')).toContain('No admin key for Production');
  });

  it('shows a not found page for unknown addresses', async () => {
    const harness = await mount();
    await harness.go('/nothing-here');
    expect(harness.textOf('h1')).toBe('Page not found');
  });
});
