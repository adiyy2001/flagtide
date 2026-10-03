import { computed, Injectable, signal } from '@angular/core';
import { PRODUCTS } from './products';
import type { Product } from './products';

export interface CartLine {
  readonly product: Product;
  readonly quantity: number;
}

export interface ShippingProgress {
  readonly free: boolean;
  readonly remainingCents: number;
  readonly share: number;
}

export function shippingProgress(subtotalCents: number, thresholdEuros: number): ShippingProgress {
  const thresholdCents = Math.max(0, Math.round(thresholdEuros * 100));
  if (thresholdCents === 0) {
    return { free: true, remainingCents: 0, share: 1 };
  }
  const remainingCents = Math.max(0, thresholdCents - subtotalCents);
  return { free: remainingCents === 0, remainingCents, share: Math.min(1, subtotalCents / thresholdCents) };
}

export function discountedTotal(subtotalCents: number, discountPercent: number): number {
  return Math.round((subtotalCents * (100 - discountPercent)) / 100);
}

@Injectable({ providedIn: 'root' })
export class Cart {
  private readonly quantities = signal<Readonly<Record<string, number>>>({});

  readonly lines = computed<readonly CartLine[]>(() =>
    PRODUCTS.flatMap((product) => {
      const quantity = this.quantities()[product.id] ?? 0;
      return quantity > 0 ? [{ product, quantity }] : [];
    }),
  );
  readonly count = computed(() => this.lines().reduce((sum, line) => sum + line.quantity, 0));
  readonly subtotalCents = computed(() =>
    this.lines().reduce((sum, line) => sum + line.product.priceCents * line.quantity, 0),
  );

  add(productId: string): void {
    this.quantities.update((current) => ({ ...current, [productId]: (current[productId] ?? 0) + 1 }));
  }

  remove(productId: string): void {
    this.quantities.update((current) => {
      const next = { ...current, [productId]: (current[productId] ?? 0) - 1 };
      return Object.fromEntries(Object.entries(next).filter(([, quantity]) => quantity > 0));
    });
  }

  clear(): void {
    this.quantities.set({});
  }
}
