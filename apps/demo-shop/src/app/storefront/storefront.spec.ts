import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  booleanFlag,
  deltasFrame,
  upsertFlag,
  variantFlag,
} from '../../../../../libs/core/test-support/fixtures';
import { mountShop } from '../../test-support/harness';
import type { ShopHarness } from '../../test-support/harness';
import { shopFlags } from '../../test-support/flags';

async function click(shop: ShopHarness, selector: string): Promise<void> {
  shop.query(selector).click();
  await shop.settle();
}

describe('storefront', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('lists the products and starts with an empty cart and a disabled checkout', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    expect(shop.queryAll('.card')).toHaveLength(6);
    expect(shop.textOf('.empty')).toBe('Nothing here yet.');
    expect(shop.query<HTMLButtonElement>('[data-testid="checkout"]').disabled).toBe(true);
  });

  it('labels the checkout button from the string flag and follows a change', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    expect(shop.textOf('[data-testid="checkout"]')).toBe('Buy now');
    shop.sockets.latest.receive(
      deltasFrame(1, [
        upsertFlag(variantFlag('checkout-label', 'string', [{ key: 'b', value: 'Pay securely' }], 'b')),
      ]),
    );
    await shop.settle();
    expect(shop.textOf('[data-testid="checkout"]')).toBe('Pay securely');
  });

  it('uses the default label before any flag arrives', async () => {
    const shop = await mountShop();
    expect(shop.textOf('[data-testid="checkout"]')).toBe('Checkout');
  });

  it('adds and removes products and counts them in the header', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    await click(shop, '[data-testid="add-kettle"]');
    await click(shop, '[data-testid="add-kettle"]');
    await click(shop, '[data-testid="add-beans"]');
    expect(shop.textOf('[data-testid="cart-count"]')).toBe('Cart: 3');
    expect(shop.textOf('.lines')).toContain('2 x Gooseneck kettle');
    await click(shop, '.lines .link');
    expect(shop.textOf('[data-testid="cart-count"]')).toBe('Cart: 2');
  });

  it('applies the percentage from the promo flag while the banner flag is on', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    await click(shop, '[data-testid="add-kettle"]');
    expect(shop.textOf('[data-testid="discount"]')).toBe('-€6.90');
    expect(shop.textOf('[data-testid="total"]')).toBe('€62.10');
    shop.sockets.latest.receive(
      deltasFrame(1, [upsertFlag(booleanFlag('promo-banner', { enabled: false }))]),
    );
    await shop.settle();
    expect(shop.has('[data-testid="discount"]')).toBe(false);
    expect(shop.textOf('[data-testid="total"]')).toBe('€69.00');
  });

  it('shows how much is missing for free shipping and unlocks it at the threshold', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    expect(shop.textOf('[data-testid="shipping"]')).toContain('€75.00 more for free shipping');
    await click(shop, '[data-testid="add-kettle"]');
    await click(shop, '[data-testid="add-grinder"]');
    expect(shop.textOf('[data-testid="shipping"]')).toContain('Free shipping unlocked');
  });

  it('follows a new threshold from the number flag', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    shop.sockets.latest.receive(
      deltasFrame(1, [
        upsertFlag(variantFlag('free-shipping-threshold', 'number', [{ key: 'high', value: 100 }], 'high')),
      ]),
    );
    await shop.settle();
    expect(shop.textOf('[data-testid="threshold"]')).toBe('(free from €100.00)');
  });

  it('places the order, empties the cart and says nothing was charged', async () => {
    const shop = await mountShop();
    await shop.deliver(shopFlags());
    await click(shop, '[data-testid="add-dripper"]');
    await click(shop, '[data-testid="checkout"]');
    expect(shop.textOf('[data-testid="ordered"]')).toContain('nothing was charged');
    expect(shop.textOf('[data-testid="cart-count"]')).toBe('Cart: 0');
    await click(shop, '[data-testid="add-dripper"]');
    expect(shop.has('[data-testid="ordered"]')).toBe(false);
  });

  it('gives every add button a name that says which product it adds', async () => {
    const shop = await mountShop();
    const names = shop
      .queryAll('.card button')
      .map((button) => button.textContent?.replace(/\s+/gu, ' ').trim());
    expect(names[0]).toBe('Add Gooseneck kettle to the cart');
  });
});
