import type { FlagConfig } from '@flagtide/core';
import { booleanFlag, variantFlag } from '../../../../libs/core/test-support/fixtures';

export function shopFlags(overrides: Partial<Record<string, FlagConfig>> = {}): FlagConfig[] {
  const defaults: Record<string, FlagConfig> = {
    'promo-banner': booleanFlag('promo-banner'),
    promo: variantFlag(
      'promo',
      'json',
      [{ key: 'spring', value: { headline: 'Spring sale', code: 'SPRING10', discountPercent: 10 } }],
      'spring',
    ),
    'checkout-label': variantFlag('checkout-label', 'string', [{ key: 'a', value: 'Buy now' }], 'a'),
    'free-shipping-threshold': variantFlag(
      'free-shipping-threshold',
      'number',
      [{ key: 'a', value: 75 }],
      'a',
    ),
    'beta-recommendations': booleanFlag('beta-recommendations', { enabled: false }),
  };
  return Object.values({ ...defaults, ...overrides }).filter(
    (flag): flag is FlagConfig => flag !== undefined,
  );
}
