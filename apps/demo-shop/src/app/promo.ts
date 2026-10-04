import type { JsonValue } from '@flagtide/core';

export interface Promo {
  readonly headline: string;
  readonly code: string;
  readonly discountPercent: number;
}

export const DEFAULT_PROMO: Promo = {
  headline: 'Free returns on every order',
  code: 'RETURNS',
  discountPercent: 0,
};

function isRecord(value: JsonValue | undefined): value is { [key: string]: JsonValue } {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function parsePromo(value: JsonValue): Promo {
  if (!isRecord(value)) {
    return DEFAULT_PROMO;
  }
  const { headline, code, discountPercent } = value;
  if (typeof headline !== 'string' || headline.trim() === '') {
    return DEFAULT_PROMO;
  }
  return {
    headline: headline.trim(),
    code: typeof code === 'string' ? code.trim() : '',
    discountPercent:
      typeof discountPercent === 'number' && discountPercent > 0 && discountPercent <= 100
        ? discountPercent
        : 0,
  };
}
