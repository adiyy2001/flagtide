import type { AttributeCondition, Condition, FlagType, Operator, Rule, Serve, Variant } from '@flagtide/core';
import type { EnvironmentConfigModel, FlagModel } from './models';
import { formatValues, inferScalarType } from './operators';
import type { ScalarType } from './operators';
import { formatPercent, splitEvenly } from './rollout';
import { formatVariantValue } from './variants';

let counter = 0;

export function nextUid(prefix: string): string {
  counter += 1;
  return `${prefix}-${counter}`;
}

export interface VariantDraft {
  readonly uid: string;
  readonly key: string;
  readonly valueText: string;
}

export interface DefinitionDraft {
  readonly description: string;
  readonly variants: readonly VariantDraft[];
}

export interface ConditionDraft {
  readonly uid: string;
  readonly kind: 'attribute' | 'segment';
  readonly attribute: string;
  readonly operator: Operator;
  readonly scalarType: ScalarType;
  readonly valuesText: string;
  readonly segment: string;
  readonly negate: boolean;
}

export interface RolloutEntryDraft {
  readonly uid: string;
  readonly variant: string;
  readonly percent: string;
}

export interface ServeDraft {
  readonly mode: 'variant' | 'rollout';
  readonly variant: string;
  readonly rollout: readonly RolloutEntryDraft[];
}

export interface RuleDraft {
  readonly uid: string;
  readonly id: string;
  readonly conditions: readonly ConditionDraft[];
  readonly serve: ServeDraft;
}

export interface EnvironmentDraft {
  readonly enabled: boolean;
  readonly offVariant: string;
  readonly rules: readonly RuleDraft[];
  readonly fallthrough: ServeDraft;
}

export function variantDraftOf(variant: Variant, type: FlagType): VariantDraft {
  return { uid: nextUid('variant'), key: variant.key, valueText: formatVariantValue(type, variant.value) };
}

export function definitionDraftOf(flag: FlagModel): DefinitionDraft {
  return {
    description: flag.description,
    variants: flag.variants.map((variant) => variantDraftOf(variant, flag.type)),
  };
}

export function newVariantDraft(type: FlagType, taken: readonly string[]): VariantDraft {
  let index = taken.length + 1;
  while (taken.includes(`variant-${index}`)) {
    index += 1;
  }
  const valueText = { boolean: 'true', string: '', number: '0', json: '{}' }[type];
  return { uid: nextUid('variant'), key: `variant-${index}`, valueText };
}

export function serveDraftOf(serve: Serve): ServeDraft {
  if ('rollout' in serve) {
    return {
      mode: 'rollout',
      variant: serve.rollout[0]?.variant ?? '',
      rollout: serve.rollout.map((entry) => ({
        uid: nextUid('entry'),
        variant: entry.variant,
        percent: formatPercent(entry.weight),
      })),
    };
  }
  return { mode: 'variant', variant: serve.variant, rollout: [] };
}

export function fixedServeDraft(variant: string): ServeDraft {
  return { mode: 'variant', variant, rollout: [] };
}

export function rolloutServeDraft(variants: readonly string[]): ServeDraft {
  const chosen = variants.slice(0, 2);
  const shares = splitEvenly(chosen.length);
  return {
    mode: 'rollout',
    variant: chosen[0] ?? '',
    rollout: chosen.map((variant, index) => ({
      uid: nextUid('entry'),
      variant,
      percent: shares[index] ?? '0',
    })),
  };
}

function conditionDraftOf(condition: Condition): ConditionDraft {
  if ('segment' in condition) {
    return {
      uid: nextUid('condition'),
      kind: 'segment',
      attribute: '',
      operator: 'equals',
      scalarType: 'string',
      valuesText: '',
      segment: condition.segment,
      negate: condition.negate ?? false,
    };
  }
  return attributeConditionDraftOf(condition);
}

export function attributeConditionDraftOf(condition: AttributeCondition): ConditionDraft {
  return {
    uid: nextUid('condition'),
    kind: 'attribute',
    attribute: condition.attribute,
    operator: condition.operator,
    scalarType: inferScalarType(condition.values),
    valuesText: formatValues(condition.values),
    segment: '',
    negate: condition.negate ?? false,
  };
}

export function newCondition(kind: 'attribute' | 'segment', segment = ''): ConditionDraft {
  return {
    uid: nextUid('condition'),
    kind,
    attribute: '',
    operator: 'equals',
    scalarType: 'string',
    valuesText: '',
    segment,
    negate: false,
  };
}

function ruleDraftOf(rule: Rule): RuleDraft {
  return {
    uid: nextUid('rule'),
    id: rule.id,
    conditions: rule.conditions.map(conditionDraftOf),
    serve: serveDraftOf(rule.serve),
  };
}

export function newRuleDraft(existingIds: readonly string[], variant: string): RuleDraft {
  let index = 1;
  while (existingIds.includes(`rule-${index}`)) {
    index += 1;
  }
  return {
    uid: nextUid('rule'),
    id: `rule-${index}`,
    conditions: [newCondition('attribute')],
    serve: fixedServeDraft(variant),
  };
}

export function environmentDraftOf(config: EnvironmentConfigModel): EnvironmentDraft {
  return {
    enabled: config.enabled,
    offVariant: config.offVariant,
    rules: config.rules.map(ruleDraftOf),
    fallthrough: serveDraftOf(config.fallthrough),
  };
}

export function moveItem<T>(items: readonly T[], from: number, to: number): T[] {
  const copy = [...items];
  if (from < 0 || from >= copy.length || to < 0 || to >= copy.length || from === to) {
    return copy;
  }
  const [moved] = copy.splice(from, 1);
  if (moved !== undefined) {
    copy.splice(to, 0, moved);
  }
  return copy;
}

export function replaceAt<T>(items: readonly T[], index: number, item: T): T[] {
  return items.map((current, position) => (position === index ? item : current));
}

export function removeAt<T>(items: readonly T[], index: number): T[] {
  return items.filter((_, position) => position !== index);
}

export function serializeDefinition(draft: DefinitionDraft): string {
  return JSON.stringify({
    description: draft.description,
    variants: draft.variants.map((variant) => [variant.key, variant.valueText]),
  });
}

function serializeServe(serve: ServeDraft): unknown {
  return serve.mode === 'variant'
    ? { variant: serve.variant }
    : { rollout: serve.rollout.map((entry) => [entry.variant, entry.percent]) };
}

export function serializeEnvironment(draft: EnvironmentDraft): string {
  return JSON.stringify({
    enabled: draft.enabled,
    offVariant: draft.offVariant,
    rules: draft.rules.map((rule) => ({
      id: rule.id,
      serve: serializeServe(rule.serve),
      conditions: rule.conditions.map((condition) => ({
        kind: condition.kind,
        attribute: condition.attribute,
        operator: condition.operator,
        scalarType: condition.scalarType,
        valuesText: condition.valuesText,
        segment: condition.segment,
        negate: condition.negate,
      })),
    })),
    fallthrough: serializeServe(draft.fallthrough),
  });
}
