import type { DeltasFrame, SnapshotFrame, StreamChange } from '../src/lib/protocol.js';
import type { FlagConfig, JsonValue, Segment } from '../src/lib/types.js';

export function booleanFlag(key: string, overrides: Partial<FlagConfig> = {}): FlagConfig {
  return {
    key,
    type: 'boolean',
    enabled: true,
    killSwitch: false,
    salt: 'a1b2c3',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    offVariant: 'off',
    rules: [],
    fallthrough: { variant: 'on' },
    ...overrides,
  };
}

export function variantFlag(
  key: string,
  type: FlagConfig['type'],
  variants: readonly { key: string; value: JsonValue }[],
  served: string,
): FlagConfig {
  return {
    key,
    type,
    enabled: true,
    killSwitch: false,
    salt: 'a1b2c3',
    variants,
    offVariant: variants[0]?.key ?? 'off',
    rules: [],
    fallthrough: { variant: served },
  };
}

export function segment(key: string, included: readonly string[] = []): Segment {
  return { key, included, excluded: [], rules: [] };
}

export function snapshotFrame(
  version: number,
  flags: readonly FlagConfig[] = [],
  segments: readonly Segment[] = [],
): SnapshotFrame {
  return { t: 'snapshot', v: version, committedAtMs: 1_000 + version, flags, segments };
}

export function upsertFlag(config: FlagConfig): StreamChange {
  return { op: 'upsert', kind: 'flag', key: config.key, config };
}

export function deltasFrame(
  from: number,
  ...changesPerEntry: readonly (readonly StreamChange[])[]
): DeltasFrame {
  return {
    t: 'deltas',
    from,
    to: from + changesPerEntry.length,
    entries: changesPerEntry.map((changes, index) => ({
      v: from + index + 1,
      committedAtMs: 1_000 + from + index + 1,
      changes,
    })),
  };
}
