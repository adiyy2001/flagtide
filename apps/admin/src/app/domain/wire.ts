import type { AttributeCondition, Condition, FlagConfig, Rule, Scalar, Variant } from '@flagtide/core';
import type { EnvironmentDraft, ConditionDraft, RuleDraft, ServeDraft, VariantDraft } from './drafts';
import type { EnvironmentConfigModel, FlagModel } from './models';
import { parseConditionValues } from './operators';
import { parsePercent } from './rollout';
import { parseVariantValue } from './variants';
import type { FlagType } from '@flagtide/core';
import type { components } from '../api/schema';

export type EnvironmentSettingsBody = components['schemas']['EnvironmentSettings'];
export type RuleBody = components['schemas']['Rule'];
export type ConditionBody = components['schemas']['Condition'];
export type SaveSegmentBody = components['schemas']['SaveSegment'];
export type VariantBody = components['schemas']['Variant'];

type ServeWire = { variant: string } | { rollout: { variant: string; weight: number }[] };

function serveOf(serve: ServeDraft): ServeWire {
  if (serve.mode === 'variant') {
    return { variant: serve.variant };
  }
  return {
    rollout: serve.rollout.map((entry) => ({
      variant: entry.variant,
      weight: parsePercent(entry.percent) ?? 0,
    })),
  };
}

export function conditionBody(condition: ConditionDraft): ConditionBody {
  if (condition.kind === 'segment') {
    return { segment: condition.segment, negate: condition.negate };
  }
  const parsed = parseConditionValues(condition.operator, condition.scalarType, condition.valuesText);
  return {
    attribute: condition.attribute.trim(),
    operator: condition.operator,
    values: 'values' in parsed ? parsed.values : [],
    negate: condition.negate,
  };
}

function ruleBody(rule: RuleDraft, order: number): RuleBody {
  return {
    id: rule.id.trim(),
    order,
    conditions: rule.conditions.map(conditionBody),
    serve: serveOf(rule.serve),
  };
}

export function environmentSettingsBody(draft: EnvironmentDraft): EnvironmentSettingsBody {
  return {
    enabled: draft.enabled,
    offVariant: draft.offVariant,
    rules: draft.rules.map((rule, index) => ruleBody(rule, index)),
    fallthrough: serveOf(draft.fallthrough),
  };
}

export function variantBodies(type: FlagType, drafts: readonly VariantDraft[]): VariantBody[] {
  return drafts.map((draft) => {
    const parsed = parseVariantValue(type, draft.valueText);
    return { key: draft.key, value: 'value' in parsed ? parsed.value : null };
  });
}

function attributeConditionOf(condition: ConditionDraft): AttributeCondition {
  const parsed = parseConditionValues(condition.operator, condition.scalarType, condition.valuesText);
  const values: readonly Scalar[] = 'values' in parsed ? parsed.values : [];
  return {
    attribute: condition.attribute.trim(),
    operator: condition.operator,
    values,
    negate: condition.negate,
  };
}

function conditionOf(condition: ConditionDraft): Condition {
  return condition.kind === 'segment'
    ? { segment: condition.segment, negate: condition.negate }
    : attributeConditionOf(condition);
}

export function flagConfigOf(
  flag: FlagModel,
  environment: EnvironmentConfigModel,
  draft: EnvironmentDraft,
): FlagConfig {
  const rules: Rule[] = draft.rules.map((rule) => ({
    id: rule.id.trim(),
    conditions: rule.conditions.map(conditionOf),
    serve: serveOf(rule.serve),
  }));
  const variants: readonly Variant[] = flag.variants;
  return {
    key: flag.key,
    type: flag.type,
    enabled: draft.enabled,
    killSwitch: environment.killSwitch,
    salt: environment.salt,
    variants,
    offVariant: draft.offVariant,
    rules,
    fallthrough: serveOf(draft.fallthrough),
  };
}
