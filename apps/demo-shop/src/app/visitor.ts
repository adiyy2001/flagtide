import { inject, REQUEST } from '@angular/core';
import type { EvaluationContext } from '@flagwire/core';

export interface Visitor {
  readonly key: string;
  readonly country: string;
  readonly plan: string;
}

export const DEFAULT_VISITOR: Visitor = { key: 'visitor-1', country: 'PL', plan: 'standard' };

const MAX_FIELD_LENGTH = 64;

function clean(value: string | null, fallback: string): string {
  const trimmed = value?.trim() ?? '';
  return trimmed === '' ? fallback : trimmed.slice(0, MAX_FIELD_LENGTH);
}

export function visitorFromUrl(url: string | null | undefined): Visitor {
  if (url === null || url === undefined || url === '') {
    return DEFAULT_VISITOR;
  }
  const query = url.includes('?') ? url.slice(url.indexOf('?') + 1).split('#')[0] : '';
  const params = new URLSearchParams(query);
  return {
    key: clean(params.get('visitor'), DEFAULT_VISITOR.key),
    country: clean(params.get('country'), DEFAULT_VISITOR.country).toUpperCase(),
    plan: clean(params.get('plan'), DEFAULT_VISITOR.plan),
  };
}

export function toEvaluationContext(visitor: Visitor): EvaluationContext {
  return { key: visitor.key, attributes: { country: visitor.country, plan: visitor.plan } };
}

export function injectVisitor(): Visitor {
  const requestUrl = inject(REQUEST, { optional: true })?.url;
  return visitorFromUrl(requestUrl ?? globalThis.location?.href);
}
