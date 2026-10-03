import { describe, expect, it } from 'vitest';
import { DEFAULT_VISITOR, toEvaluationContext, visitorFromUrl } from './visitor';

describe('visitorFromUrl', () => {
  it('uses the default visitor without a query', () => {
    expect(visitorFromUrl('http://shop/')).toEqual(DEFAULT_VISITOR);
    expect(visitorFromUrl(undefined)).toEqual(DEFAULT_VISITOR);
    expect(visitorFromUrl(null)).toEqual(DEFAULT_VISITOR);
    expect(visitorFromUrl('')).toEqual(DEFAULT_VISITOR);
  });

  it('reads visitor, country and plan from the query and upper cases the country', () => {
    expect(visitorFromUrl('http://shop/?visitor=anna&country=de&plan=pro')).toEqual({
      key: 'anna',
      country: 'DE',
      plan: 'pro',
    });
  });

  it('works with a relative url and ignores the fragment', () => {
    expect(visitorFromUrl('/?visitor=b#top').key).toBe('b');
  });

  it('keeps unicode keys intact', () => {
    expect(visitorFromUrl('/?visitor=%C5%81ukasz').key).toBe('Łukasz');
  });

  it('falls back for empty values and cuts very long ones', () => {
    expect(visitorFromUrl('/?visitor=%20%20').key).toBe(DEFAULT_VISITOR.key);
    expect(visitorFromUrl(`/?visitor=${'a'.repeat(200)}`).key).toHaveLength(64);
  });
});

describe('toEvaluationContext', () => {
  it('uses the visitor key as the context key and the rest as attributes', () => {
    expect(toEvaluationContext({ key: 'k', country: 'PL', plan: 'pro' })).toEqual({
      key: 'k',
      attributes: { country: 'PL', plan: 'pro' },
    });
  });
});
