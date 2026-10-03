import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  booleanFlag,
  deltasFrame,
  upsertFlag,
  variantFlag,
} from '../../../../libs/core/test-support/fixtures';
import { mountShop } from '../test-support/harness';
import { shopFlags } from '../test-support/flags';

describe('shop shell', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('shows the promo banner with the headline and code from the json flag', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    const banner = shop.textOf('[data-testid="promo-banner"]');
    expect(banner).toContain('Spring sale');
    expect(banner).toContain('SPRING10');
  });

  it('removes the banner when the boolean flag turns off and brings it back', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    shop.sockets.latest.receive(
      deltasFrame(1, [upsertFlag(booleanFlag('promo-banner', { enabled: false }))]),
    );
    await shop.settle();
    expect(shop.has('[data-testid="promo-banner"]')).toBe(false);
    shop.sockets.latest.receive(deltasFrame(2, [upsertFlag(booleanFlag('promo-banner'))]));
    await shop.settle();
    expect(shop.has('[data-testid="promo-banner"]')).toBe(true);
  });

  it('falls back to a plain promo when the json flag has the wrong shape', async () => {
    const shop = await mountShop();
    await shop.deliver(
      shopFlags({ promo: variantFlag('promo', 'json', [{ key: 'bad', value: { headline: 42 } }], 'bad') }),
    );
    expect(shop.textOf('[data-testid="promo-banner"]')).toContain('Free returns on every order');
  });

  it('hides the beta link for visitors outside the flag and shows it for those inside', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    expect(shop.has('[data-testid="nav-recommendations"]')).toBe(false);
    shop.sockets.latest.receive(deltasFrame(1, [upsertFlag(booleanFlag('beta-recommendations'))]));
    await shop.settle();
    expect(shop.textOf('[data-testid="nav-recommendations"]')).toBe('Recommended Beta');
  });

  it('sends visitors without the flag from the beta route back to the shop', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    await shop.go('/recommendations');
    expect(TestBed.inject(Router).url).toBe('/');
  });

  it('opens the beta route once the flag is on', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags({ 'beta-recommendations': booleanFlag('beta-recommendations') }));
    await shop.go('/recommendations');
    expect(TestBed.inject(Router).url).toBe('/recommendations');
    expect(shop.textOf('h1')).toBe('Recommended for you');
  });

  it('shows the connection status as text next to a marker', async () => {
    const shop = await mountShop();
    expect(shop.query('[role="status"]').getAttribute('data-status')).toBe('connecting');
    await shop.deliver(shopFlags());
    expect(shop.query('[role="status"]').getAttribute('data-status')).toBe('live');
    expect(shop.textOf('[role="status"]')).toBe('Flags: Live');
  });

  it('shows the visitor taken from the url and keeps them in the switch form', async () => {
    const shop = await mountShop('?visitor=visitor-beta&country=DE&plan=pro');
    expect(shop.query<HTMLInputElement>('#visitor-key').value).toBe('visitor-beta');
    expect(shop.textOf('[data-testid="visitor-attributes"]')).toBe('Country DE, plan pro');
    expect(shop.query<HTMLInputElement>('input[name="country"]').value).toBe('DE');
  });

  it('moves focus to the main region from the skip link', async () => {
    const shop = await mountShop();
    shop.query<HTMLAnchorElement>('a.skip').click();
    expect(document.activeElement).toBe(shop.query('main'));
  });

  it('renders a not found page for unknown addresses', async () => {
    const shop = await mountShop();
    await shop.go('/nowhere');
    expect(shop.textOf('h1')).toBe('Page not found');
  });
});
