import type { FlagType, JsonValue } from '@flagtide/core';
import { parseNumberText } from './operators';

const SLUG = /^[a-z0-9][a-z0-9_-]{0,63}$/u;

export function isSlug(text: string): boolean {
  return SLUG.test(text);
}

export type ParsedValue = { readonly value: JsonValue } | { readonly error: string };

export function parseVariantValue(type: FlagType, text: string): ParsedValue {
  switch (type) {
    case 'boolean':
      return text === 'true' || text === 'false'
        ? { value: text === 'true' }
        : { error: 'Choose true or false' };
    case 'string':
      return { value: text };
    case 'number': {
      const value = parseNumberText(text.trim());
      return value === null ? { error: 'Enter a number' } : { value };
    }
    case 'json':
      return parseJson(text);
  }
}

function parseJson(text: string): ParsedValue {
  try {
    return { value: JSON.parse(text) as JsonValue };
  } catch (failure) {
    const detail = failure instanceof Error ? failure.message : 'invalid JSON';
    return { error: `Not valid JSON: ${detail}` };
  }
}

export function formatVariantValue(type: FlagType, value: JsonValue): string {
  if (type === 'json') {
    return JSON.stringify(value, null, 2);
  }
  return typeof value === 'string' ? value : String(value);
}

export interface DefaultVariant {
  readonly key: string;
  readonly valueText: string;
}

export function defaultVariants(type: FlagType): DefaultVariant[] {
  switch (type) {
    case 'boolean':
      return [
        { key: 'on', valueText: 'true' },
        { key: 'off', valueText: 'false' },
      ];
    case 'string':
      return [
        { key: 'control', valueText: 'control' },
        { key: 'treatment', valueText: 'treatment' },
      ];
    case 'number':
      return [
        { key: 'low', valueText: '10' },
        { key: 'high', valueText: '20' },
      ];
    case 'json':
      return [
        { key: 'default', valueText: '{}' },
        { key: 'custom', valueText: '{ "enabled": true }' },
      ];
  }
}
