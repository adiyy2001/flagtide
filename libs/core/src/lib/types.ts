export type Scalar = string | number | boolean;

export type JsonValue =
  null | boolean | number | string | readonly JsonValue[] | { readonly [key: string]: JsonValue };

export type FlagType = 'boolean' | 'string' | 'number' | 'json';

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

export interface AttributeCondition {
  readonly attribute: string;
  readonly operator: Operator;
  readonly values: readonly Scalar[];
  readonly negate?: boolean;
}

export interface SegmentCondition {
  readonly segment: string;
  readonly negate?: boolean;
}

export type Condition = AttributeCondition | SegmentCondition;

export interface WeightedVariant {
  readonly variant: string;
  readonly weight: number;
}

export interface VariantServe {
  readonly variant: string;
}

export interface RolloutServe {
  readonly rollout: readonly WeightedVariant[];
}

export type Serve = VariantServe | RolloutServe;

export interface Rule {
  readonly id: string;
  readonly conditions: readonly Condition[];
  readonly serve: Serve;
}

export interface Variant {
  readonly key: string;
  readonly value: JsonValue;
}

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

export interface EvaluationContext {
  readonly key: string;
  readonly attributes: Readonly<Record<string, unknown>>;
}

export type Reason = 'KILL_SWITCH' | 'OFF' | 'RULE_MATCH' | 'FALLTHROUGH';

export interface EvaluationResult {
  readonly variantKey: string;
  readonly value: JsonValue;
  readonly reason: Reason;
  readonly ruleIndex: number | null;
  readonly ruleId: string | null;
  readonly bucket: number | null;
}

export type SegmentIndex = ReadonlyMap<string, Segment>;
