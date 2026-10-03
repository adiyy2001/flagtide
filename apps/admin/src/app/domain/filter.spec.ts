import { describe, expect, it } from 'vitest';
import { booleanFlag, environmentConfig } from '../../test-support/fixtures';
import { EMPTY_FILTER, filterFlags, isFiltered } from './filter';

const flags = [
  booleanFlag('new-checkout', { description: 'Checkout redesign' }),
  booleanFlag('promo-banner', {
    type: 'json',
    environments: {
      dev: environmentConfig({ enabled: false }),
      prod: environmentConfig({ killSwitch: true }),
    },
  }),
  booleanFlag('old-flag', { archived: true }),
];

describe('filterFlags', () => {
  it('hides archived flags unless asked for them', () => {
    expect(filterFlags(flags, EMPTY_FILTER, 'dev').map((flag) => flag.key)).toEqual([
      'new-checkout',
      'promo-banner',
    ]);
    expect(filterFlags(flags, { ...EMPTY_FILTER, state: 'archived' }, 'dev').map((flag) => flag.key)).toEqual(
      ['old-flag'],
    );
  });

  it('matches the key or the description without regard to case', () => {
    expect(filterFlags(flags, { ...EMPTY_FILTER, text: 'CHECK' }, 'dev').map((flag) => flag.key)).toEqual([
      'new-checkout',
    ]);
    expect(filterFlags(flags, { ...EMPTY_FILTER, text: 'redesign' }, 'dev')).toHaveLength(1);
    expect(filterFlags(flags, { ...EMPTY_FILTER, text: 'zzz' }, 'dev')).toEqual([]);
  });

  it('filters by type', () => {
    expect(filterFlags(flags, { ...EMPTY_FILTER, type: 'json' }, 'dev').map((flag) => flag.key)).toEqual([
      'promo-banner',
    ]);
  });

  it('filters by state in the selected environment', () => {
    const keys = (state: 'enabled' | 'disabled' | 'killed', environment: string) =>
      filterFlags(flags, { ...EMPTY_FILTER, state }, environment).map((flag) => flag.key);
    expect(keys('enabled', 'dev')).toEqual(['new-checkout']);
    expect(keys('disabled', 'dev')).toEqual(['promo-banner']);
    expect(keys('killed', 'prod')).toEqual(['promo-banner']);
    expect(keys('enabled', 'prod')).toEqual([]);
    expect(keys('enabled', 'missing')).toEqual([]);
  });

  it('knows when a filter is active', () => {
    expect(isFiltered(EMPTY_FILTER)).toBe(false);
    expect(isFiltered({ ...EMPTY_FILTER, text: ' x ' })).toBe(true);
    expect(isFiltered({ ...EMPTY_FILTER, type: 'string' })).toBe(true);
    expect(isFiltered({ ...EMPTY_FILTER, state: 'enabled' })).toBe(true);
  });
});
