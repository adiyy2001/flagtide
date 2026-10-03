/** A value an attribute condition compares against. */
export type Scalar = string | number | boolean;

/** Any JSON value, the type of flag variant values. */
export type JsonValue =
  null | boolean | number | string | readonly JsonValue[] | { readonly [key: string]: JsonValue };

/** The type of a flag and of every one of its variant values. */
export type FlagType = 'boolean' | 'string' | 'number' | 'json';

/** Comparison operators of attribute conditions. */
export type Operator =
  | 'equals'
  | 'in'
  | 'contains'
  | 'startsWith'
  | 'lt'
  | 'lte'
  | 'gt'
  | 'gte'
  | 'semverEquals'
  | 'semverLt'
  | 'semverLte'
  | 'semverGt'
  | 'semverGte';

/** A condition on one attribute of the context. Matches when any of `values` matches, unless `negate` is set. */
export interface AttributeCondition {
  readonly attribute: string;
  readonly operator: Operator;
  readonly values: readonly Scalar[];
  readonly negate?: boolean;
}

/** A condition on membership of a reusable segment. */
/** A reusable group of contexts: explicit keys plus rules. */
export interface SegmentCondition {
  readonly segment: string;
  readonly negate?: boolean;
}

export type Condition = AttributeCondition | SegmentCondition;

/** One slice of a percentage rollout. Weights are integer thousandths of a percent and sum to 100000. */
export interface WeightedVariant {
  readonly variant: string;
  readonly weight: number;
}

/** A named value a flag can serve. */
export interface VariantServe {
  readonly variant: string;
}

export interface RolloutServe {
  readonly rollout: readonly WeightedVariant[];
}

export type Serve = VariantServe | RolloutServe;

/** A targeting rule. Every condition must match for the rule to serve. */
export interface Rule {
  readonly id: string;
  readonly conditions: readonly Condition[];
  readonly serve: Serve;
}

export interface Variant {
  readonly key: string;
  readonly value: JsonValue;
}

/** The compiled client format of a flag in one environment. */
export interface FlagConfig {
  readonly key: string;
  readonly type: FlagType;
  readonly enabled: boolean;
  readonly killSwitch: boolean;
  readonly salt: string;
  readonly variants: readonly Variant[];
  readonly offVariant: string;
  readonly rules: readonly Rule[];
  readonly fallthrough: Serve;
}

export interface Segment {
  readonly key: string;
  readonly included: readonly string[];
  readonly excluded: readonly string[];
  readonly rules: readonly (readonly AttributeCondition[])[];
}

/** Who a flag is evaluated for: a stable key plus attributes that rules can match on. */
export interface EvaluationContext {
  readonly key: string;
  readonly attributes: Readonly<Record<string, unknown>>;
}

/** Why the evaluation algorithm picked a variant. */
export type Reason = 'KILL_SWITCH' | 'OFF' | 'RULE_MATCH' | 'FALLTHROUGH';

/** The outcome of the evaluation algorithm. `bucket` is set when a rollout decided. */
export interface EvaluationResult {
  readonly variantKey: string;
  readonly value: JsonValue;
  readonly reason: Reason;
  readonly ruleIndex: number | null;
  readonly ruleId: string | null;
  readonly bucket: number | null;
}

/** Segments by key. */
export type SegmentIndex = ReadonlyMap<string, Segment>;
