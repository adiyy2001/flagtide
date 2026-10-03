import { evaluate } from './evaluate.js';
import type { EvaluationContext, FlagConfig, FlagType, JsonValue, Reason, SegmentIndex } from './types.js';

/** Why a flag returned the value it returned. The first four come from the evaluation algorithm. */
export type ResolutionReason = Reason | 'FLAG_NOT_FOUND' | 'TYPE_MISMATCH' | 'OVERRIDE';

/** The outcome of reading one flag with a fallback. */
export interface Resolution<T extends JsonValue = JsonValue> {
  readonly value: T;
  readonly reason: ResolutionReason;
  readonly variantKey: string | null;
  readonly ruleId: string | null;
  readonly ruleIndex: number | null;
  readonly bucket: number | null;
}

/** The flag type a value belongs to. Booleans, strings and numbers have their own types, everything else is JSON. */
export function flagTypeOf(value: JsonValue): FlagType {
  switch (typeof value) {
    case 'boolean':
      return 'boolean';
    case 'string':
      return 'string';
    case 'number':
      return 'number';
    default:
      return 'json';
  }
}

function fallbackResolution<T extends JsonValue>(fallback: T, reason: ResolutionReason): Resolution<T> {
  return { value: fallback, reason, variantKey: null, ruleId: null, ruleIndex: null, bucket: null };
}

/**
 * Reads one flag. The fallback is returned when the flag does not exist (`FLAG_NOT_FOUND`) or when its type
 * differs from the type of the fallback (`TYPE_MISMATCH`). An override wins over the evaluated value when it
 * has the type of the fallback (`OVERRIDE`).
 */
export function resolveFlag<T extends JsonValue>(
  flag: FlagConfig | undefined,
  context: EvaluationContext,
  segments: SegmentIndex,
  fallback: T,
  override?: JsonValue,
): Resolution<T> {
  const expected = flagTypeOf(fallback);
  if (override !== undefined && flagTypeOf(override) === expected) {
    return { ...fallbackResolution(fallback, 'OVERRIDE'), value: override as T };
  }
  if (flag === undefined) {
    return fallbackResolution(fallback, 'FLAG_NOT_FOUND');
  }
  if (flag.type !== expected) {
    return fallbackResolution(fallback, 'TYPE_MISMATCH');
  }
  const result = evaluate(flag, context, segments);
  return {
    value: result.value as T,
    reason: result.reason,
    variantKey: result.variantKey,
    ruleId: result.ruleId,
    ruleIndex: result.ruleIndex,
    bucket: result.bucket,
  };
}
