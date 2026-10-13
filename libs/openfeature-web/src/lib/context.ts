import type { EvaluationContext as FlagtideContext } from '@flagtide/core';
import type { EvaluationContext, EvaluationContextValue } from '@openfeature/web-sdk';

function toAttribute(value: EvaluationContextValue): unknown {
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (Array.isArray(value)) {
    return value.map(toAttribute);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([name, nested]) => [name, toAttribute(nested)]));
  }
  return value;
}

export function toFlagtideContext(context: EvaluationContext, anonymousKey: string): FlagtideContext {
  const { targetingKey, ...rest } = context;
  const attributes: Record<string, unknown> = {};
  for (const [name, value] of Object.entries(rest)) {
    if (value !== undefined) {
      attributes[name] = toAttribute(value);
    }
  }
  const key = typeof targetingKey === 'string' && targetingKey !== '' ? targetingKey : anonymousKey;
  return { key, attributes };
}
