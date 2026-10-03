import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { Cart, discountedTotal, shippingProgress } from './cart';

describe('shippingProgress', () => {
  it('reports what is missing and how far along the order is', () => {
    expect(shippingProgress(2500, 50)).toEqual({ free: false, remainingCents: 2500, share: 0.5 });
  });

  it('is free at the threshold and above it', () => {
    expect(shippingProgress(5000, 50)).toEqual({ free: true, remainingCents: 0, share: 1 });
    expect(shippingProgress(9000, 50).share).toBe(1);
  });

  it('is always free when the threshold is zero or negative', () => {
    expect(shippingProgress(0, 0).free).toBe(true);
    expect(shippingProgress(0, -5).free).toBe(true);
  });

  it('rounds a fractional threshold to whole cents', () => {
    expect(shippingProgress(0, 19.999).remainingCents).toBe(2000);
  });
});

describe('discountedTotal', () => {
  it('takes the percentage off and rounds to cents', () => {
    expect(discountedTotal(6900, 10)).toBe(6210);
    expect(discountedTotal(999, 10)).toBe(899);
  });

  it('leaves the total alone without a discount', () => {
    expect(discountedTotal(1234, 0)).toBe(1234);
  });
});

describe('Cart', () => {
  let cart: Cart;

  beforeEach(() => {
    TestBed.resetTestingModule();
    cart = TestBed.inject(Cart);
  });

  it('adds up quantities and the subtotal', () => {
    cart.add('kettle');
    cart.add('kettle');
    cart.add('beans');
    expect(cart.count()).toBe(3);
    expect(cart.subtotalCents()).toBe(6900 * 2 + 1600);
    expect(cart.lines().map((line) => line.product.id)).toEqual(['kettle', 'beans']);
  });

  it('drops a line when its last unit is removed and ignores unknown ones', () => {
    cart.add('kettle');
    cart.remove('kettle');
    cart.remove('kettle');
    expect(cart.lines()).toEqual([]);
  });

  it('clears everything', () => {
    cart.add('kettle');
    cart.clear();
    expect(cart.count()).toBe(0);
  });
});
