import type { JsonValue } from '@flagwire/core';

export interface DiffLine {
  readonly path: string;
  readonly kind: 'added' | 'removed' | 'changed';
  readonly before: string | null;
  readonly after: string | null;
}

function isContainer(value: JsonValue | undefined): value is JsonValue[] | { [key: string]: JsonValue } {
  return typeof value === 'object' && value !== null;
}

export function flatten(value: JsonValue | undefined, prefix = '', into = new Map<string, string>()) {
  if (isContainer(value)) {
    const entries = Array.isArray(value)
      ? value.map((item, index) => [String(index), item] as const)
      : Object.entries(value);
    if (entries.length === 0) {
      into.set(prefix, Array.isArray(value) ? '[]' : '{}');
    }
    for (const [key, child] of entries) {
      flatten(child, prefix === '' ? key : `${prefix}.${key}`, into);
    }
  } else if (value !== undefined) {
    into.set(prefix, JSON.stringify(value));
  }
  return into;
}

export function diffJson(before: JsonValue | undefined, after: JsonValue | undefined): DiffLine[] {
  const left = before === null ? new Map<string, string>() : flatten(before);
  const right = after === null ? new Map<string, string>() : flatten(after);
  const paths = [...new Set([...left.keys(), ...right.keys()])].sort((a, b) =>
    a.localeCompare(b, 'en', { numeric: true }),
  );
  const lines: DiffLine[] = [];
  for (const path of paths) {
    const was = left.get(path) ?? null;
    const now = right.get(path) ?? null;
    if (was === now) {
      continue;
    }
    const kind = was === null ? 'added' : now === null ? 'removed' : 'changed';
    lines.push({ path: path === '' ? '(value)' : path, kind, before: was, after: now });
  }
  return lines;
}
