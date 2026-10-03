import type { FlagType } from '@flagwire/core';
import type {
  ConditionDraft,
  DefinitionDraft,
  EnvironmentDraft,
  RuleDraft,
  ServeDraft,
  VariantDraft,
} from './drafts';
import type { Issue } from './models';
import { parseConditionValues } from './operators';
import { describeRemaining, parsePercent, remainingUnits } from './rollout';
import { isSlug, parseVariantValue } from './variants';

export interface EnvironmentContext {
  readonly variantKeys: readonly string[];
  readonly segmentKeys: readonly string[];
}

const MAX_RULE_ID = 64;

function issue(path: string, message: string): Issue {
  return { path, message };
}

function serveIssues(serve: ServeDraft, path: string, context: EnvironmentContext): Issue[] {
  if (serve.mode === 'variant') {
    return context.variantKeys.includes(serve.variant) ? [] : [issue(`${path}.variant`, 'Choose a variant')];
  }
  return rolloutIssues(serve, path, context);
}

function rolloutIssues(serve: ServeDraft, path: string, context: EnvironmentContext): Issue[] {
  const issues: Issue[] = [];
  if (serve.rollout.length === 0) {
    return [issue(`${path}.rollout`, 'Add at least one variant to the rollout')];
  }
  const seen = new Set<string>();
  serve.rollout.forEach((entry, index) => {
    const entryPath = `${path}.rollout.${index}`;
    if (!context.variantKeys.includes(entry.variant)) {
      issues.push(issue(`${entryPath}.variant`, 'Choose a variant'));
    } else if (seen.has(entry.variant)) {
      issues.push(issue(`${entryPath}.variant`, 'Each variant can appear once'));
    }
    seen.add(entry.variant);
    if (parsePercent(entry.percent) === null) {
      issues.push(issue(`${entryPath}.percent`, 'Enter a percentage from 0 to 100 with up to 3 decimals'));
    }
  });
  const remaining = remainingUnits(serve.rollout.map((entry) => entry.percent));
  if (remaining !== 0) {
    issues.push(issue(`${path}.rollout`, `${describeRemaining(remaining)}, it must be exactly 100%`));
  }
  return issues;
}

export function conditionIssues(
  condition: ConditionDraft,
  path: string,
  segmentKeys: readonly string[],
): Issue[] {
  if (condition.kind === 'segment') {
    return segmentKeys.includes(condition.segment)
      ? []
      : [issue(`${path}.segment`, 'Choose a segment that exists in this environment')];
  }
  const issues: Issue[] = [];
  if (condition.attribute.trim() === '') {
    issues.push(issue(`${path}.attribute`, 'Enter the attribute name'));
  }
  const parsed = parseConditionValues(condition.operator, condition.scalarType, condition.valuesText);
  if ('error' in parsed) {
    issues.push(issue(`${path}.values`, parsed.error));
  }
  return issues;
}

function ruleIssues(rule: RuleDraft, index: number, ids: readonly string[], context: EnvironmentContext) {
  const path = `rules.${index}`;
  const issues: Issue[] = [];
  const id = rule.id.trim();
  if (id === '' || id.length > MAX_RULE_ID) {
    issues.push(issue(`${path}.id`, 'The rule name must be 1 to 64 characters'));
  } else if (ids.filter((other) => other.trim() === id).length > 1) {
    issues.push(issue(`${path}.id`, 'Another rule already uses this name'));
  }
  rule.conditions.forEach((condition, position) =>
    issues.push(...conditionIssues(condition, `${path}.conditions.${position}`, context.segmentKeys)),
  );
  issues.push(...serveIssues(rule.serve, `${path}.serve`, context));
  return issues;
}

export function validateEnvironment(draft: EnvironmentDraft, context: EnvironmentContext): Issue[] {
  const issues: Issue[] = [];
  if (!context.variantKeys.includes(draft.offVariant)) {
    issues.push(issue('offVariant', 'Choose the variant served while the flag is off'));
  }
  const ids = draft.rules.map((rule) => rule.id);
  draft.rules.forEach((rule, index) => issues.push(...ruleIssues(rule, index, ids, context)));
  issues.push(...serveIssues(draft.fallthrough, 'fallthrough', context));
  return issues;
}

function variantIssues(type: FlagType, variant: VariantDraft, index: number, keys: readonly string[]) {
  const issues: Issue[] = [];
  const path = `variants.${index}`;
  if (!isSlug(variant.key)) {
    issues.push(issue(`${path}.key`, 'Use lowercase letters, digits, hyphens and underscores, up to 64'));
  } else if (keys.filter((key) => key === variant.key).length > 1) {
    issues.push(issue(`${path}.key`, 'Variant keys must be unique'));
  }
  const parsed = parseVariantValue(type, variant.valueText);
  if ('error' in parsed) {
    issues.push(issue(`${path}.value`, parsed.error));
  }
  return issues;
}

export function validateVariants(
  type: FlagType,
  variants: readonly VariantDraft[],
  usedKeys: ReadonlyMap<string, string> = new Map(),
): Issue[] {
  if (variants.length === 0) {
    return [issue('variants', 'A flag needs at least one variant')];
  }
  const keys = variants.map((variant) => variant.key);
  const issues = variants.flatMap((variant, index) => variantIssues(type, variant, index, keys));
  usedKeys.forEach((where, key) => {
    if (!keys.includes(key)) {
      issues.push(issue('variants', `Variant ${key} is still used in ${where}`));
    }
  });
  return issues;
}

export function validateDefinition(
  type: FlagType,
  draft: DefinitionDraft,
  usedKeys: ReadonlyMap<string, string>,
): Issue[] {
  return validateVariants(type, draft.variants, usedKeys);
}

export interface NewFlagDraft {
  readonly key: string;
  readonly type: FlagType;
  readonly description: string;
  readonly variants: readonly VariantDraft[];
  readonly offVariant: string;
  readonly fallthroughVariant: string;
}

export function validateNewFlag(draft: NewFlagDraft): Issue[] {
  const issues = validateVariants(draft.type, draft.variants);
  if (!isSlug(draft.key)) {
    issues.push(issue('key', 'Use lowercase letters, digits, hyphens and underscores, up to 64, no dots'));
  }
  const keys = draft.variants.map((variant) => variant.key);
  if (!keys.includes(draft.offVariant)) {
    issues.push(issue('offVariant', 'Choose the variant served while the flag is off'));
  }
  if (!keys.includes(draft.fallthroughVariant)) {
    issues.push(issue('fallthroughVariant', 'Choose the variant served when no rule matches'));
  }
  return issues;
}

export function issuesAt(issues: readonly Issue[], path: string): string[] {
  return issues.filter((item) => item.path === path).map((item) => item.message);
}

export function issuesUnder(issues: readonly Issue[], prefix: string): Issue[] {
  return issues.filter((item) => item.path === prefix || item.path.startsWith(`${prefix}.`));
}

const LOCATION_PATTERNS: readonly [RegExp, (match: RegExpMatchArray) => string][] = [
  [
    /^rules\.(\d+)\.conditions\.(\d+)/u,
    (match) => `Rule ${Number(match[1]) + 1}, condition ${Number(match[2]) + 1}`,
  ],
  [/^rules\.(\d+)\.id/u, (match) => `Rule ${Number(match[1]) + 1}, name`],
  [/^rules\.(\d+)\.serve/u, (match) => `Rule ${Number(match[1]) + 1}, serve`],
  [/^rules\.(\d+)/u, (match) => `Rule ${Number(match[1]) + 1}`],
  [/^fallthroughVariant/u, () => 'Fallthrough variant'],
  [/^fallthrough/u, () => 'Default rule'],
  [/^offVariant/u, () => 'Off variant'],
  [/^variants\.(\d+)\.key/u, (match) => `Variant ${Number(match[1]) + 1}, key`],
  [/^variants\.(\d+)\.value/u, (match) => `Variant ${Number(match[1]) + 1}, value`],
  [/^variants/u, () => 'Variants'],
  [/^key/u, () => 'Key'],
];

export function describeIssue(item: Issue): string {
  for (const [pattern, describe] of LOCATION_PATTERNS) {
    const match = pattern.exec(item.path);
    if (match !== null) {
      return `${describe(match)}: ${item.message}`;
    }
  }
  return item.message;
}
