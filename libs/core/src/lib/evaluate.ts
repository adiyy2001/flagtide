import { bucketOf } from './bucket';
import { compareSemanticVersions, parseSemanticVersion } from './semver';
import type { SemanticVersion } from './semver';
import type {
  AttributeCondition,
  Condition,
  EvaluationContext,
  EvaluationResult,
  FlagConfig,
  Operator,
  Reason,
  Rule,
  Scalar,
  Segment,
  SegmentCondition,
  SegmentIndex,
  Serve,
  WeightedVariant,
} from './types';

const NO_SEGMENTS: SegmentIndex = new Map();

const NOT_APPLICABLE = 0;
const MISS = 1;
const HIT = 2;
type ElementOutcome = typeof NOT_APPLICABLE | typeof MISS | typeof HIT;

export class InvalidFlagConfigError extends Error {}

export function indexSegments(segments: readonly Segment[]): SegmentIndex {
  return new Map(segments.map((segment) => [segment.key, segment]));
}

function isScalar(value: unknown): value is Scalar {
  const kind = typeof value;
  return kind === 'string' || kind === 'boolean' || (kind === 'number' && Number.isFinite(value));
}

function isScalarList(value: unknown): value is readonly Scalar[] {
  if (!Array.isArray(value)) {
    return false;
  }
  for (const element of value) {
    if (!isScalar(element)) {
      return false;
    }
  }
  return true;
}

function outcomeOf(hit: boolean): ElementOutcome {
  return hit ? HIT : MISS;
}

function equalsAny(element: Scalar, values: readonly Scalar[]): boolean {
  for (const value of values) {
    if (typeof value === typeof element && value === element) {
      return true;
    }
  }
  return false;
}

function textMatchesAny(operator: Operator, element: string, values: readonly Scalar[]): boolean {
  for (const value of values) {
    if (typeof value !== 'string') {
      continue;
    }
    if (operator === 'contains' ? element.includes(value) : element.startsWith(value)) {
      return true;
    }
  }
  return false;
}

function compareNumber(operator: Operator, element: number, value: number): boolean {
  switch (operator) {
    case 'lt':
      return element < value;
    case 'lte':
      return element <= value;
    case 'gt':
      return element > value;
    default:
      return element >= value;
  }
}

function numberMatchesAny(operator: Operator, element: number, values: readonly Scalar[]): boolean {
  for (const value of values) {
    if (typeof value === 'number' && compareNumber(operator, element, value)) {
      return true;
    }
  }
  return false;
}

function satisfiesOrder(operator: Operator, order: number): boolean {
  switch (operator) {
    case 'semverEquals':
      return order === 0;
    case 'semverLt':
      return order < 0;
    case 'semverLte':
      return order <= 0;
    case 'semverGt':
      return order > 0;
    default:
      return order >= 0;
  }
}

function semverMatchesAny(operator: Operator, element: SemanticVersion, values: readonly Scalar[]): boolean {
  for (const value of values) {
    if (typeof value !== 'string') {
      continue;
    }
    const bound = parseSemanticVersion(value);
    if (bound !== null && satisfiesOrder(operator, compareSemanticVersions(element, bound))) {
      return true;
    }
  }
  return false;
}

function evaluateElement(operator: Operator, element: Scalar, values: readonly Scalar[]): ElementOutcome {
  switch (operator) {
    case 'equals':
    case 'in':
      return outcomeOf(equalsAny(element, values));
    case 'contains':
    case 'startsWith':
      return typeof element === 'string'
        ? outcomeOf(textMatchesAny(operator, element, values))
        : NOT_APPLICABLE;
    case 'lt':
    case 'lte':
    case 'gt':
    case 'gte':
      return typeof element === 'number'
        ? outcomeOf(numberMatchesAny(operator, element, values))
        : NOT_APPLICABLE;
    default: {
      const version = typeof element === 'string' ? parseSemanticVersion(element) : null;
      return version === null ? NOT_APPLICABLE : outcomeOf(semverMatchesAny(operator, version, values));
    }
  }
}

function matchesAttribute(condition: AttributeCondition, context: EvaluationContext): boolean {
  const raw = context.attributes[condition.attribute];
  let outcome: ElementOutcome = NOT_APPLICABLE;
  if (isScalar(raw)) {
    outcome = evaluateElement(condition.operator, raw, condition.values);
  } else if (isScalarList(raw)) {
    for (const element of raw) {
      const elementOutcome = evaluateElement(condition.operator, element, condition.values);
      if (elementOutcome === HIT) {
        outcome = HIT;
        break;
      }
      if (elementOutcome === MISS) {
        outcome = MISS;
      }
    }
  }
  if (outcome === NOT_APPLICABLE) {
    return false;
  }
  return (outcome === HIT) !== (condition.negate === true);
}

function isMember(segment: Segment, context: EvaluationContext): boolean {
  if (segment.excluded.includes(context.key)) {
    return false;
  }
  if (segment.included.includes(context.key)) {
    return true;
  }
  return segment.rules.some((group) => group.every((condition) => matchesAttribute(condition, context)));
}

function matchesSegment(
  condition: SegmentCondition,
  context: EvaluationContext,
  segments: SegmentIndex,
): boolean {
  const segment = segments.get(condition.segment);
  if (segment === undefined) {
    return false;
  }
  return isMember(segment, context) !== (condition.negate === true);
}

function matchesCondition(condition: Condition, context: EvaluationContext, segments: SegmentIndex): boolean {
  return 'segment' in condition
    ? matchesSegment(condition, context, segments)
    : matchesAttribute(condition, context);
}

function matchesRule(rule: Rule, context: EvaluationContext, segments: SegmentIndex): boolean {
  for (const condition of rule.conditions) {
    if (!matchesCondition(condition, context, segments)) {
      return false;
    }
  }
  return true;
}

function pickWeighted(rollout: readonly WeightedVariant[], bucket: number): string {
  let running = 0;
  for (const entry of rollout) {
    running += entry.weight;
    if (running > bucket) {
      return entry.variant;
    }
  }
  throw new InvalidFlagConfigError('rollout weights do not sum to 100000');
}

function result(
  flag: FlagConfig,
  variantKey: string,
  reason: Reason,
  ruleIndex: number | null,
  ruleId: string | null,
  bucket: number | null,
): EvaluationResult {
  const variant = flag.variants.find((candidate) => candidate.key === variantKey);
  if (variant === undefined) {
    throw new InvalidFlagConfigError(`unknown variant ${variantKey}`);
  }
  return { variantKey, value: variant.value, reason, ruleIndex, ruleId, bucket };
}

function serveResult(
  flag: FlagConfig,
  serve: Serve,
  context: EvaluationContext,
  reason: Reason,
  ruleIndex: number | null,
  ruleId: string | null,
): EvaluationResult {
  if ('variant' in serve) {
    return result(flag, serve.variant, reason, ruleIndex, ruleId, null);
  }
  const bucket = bucketOf(flag.key, flag.salt, context.key);
  return result(flag, pickWeighted(serve.rollout, bucket), reason, ruleIndex, ruleId, bucket);
}

export function evaluate(
  flag: FlagConfig,
  context: EvaluationContext,
  segments: SegmentIndex = NO_SEGMENTS,
): EvaluationResult {
  if (flag.killSwitch) {
    return result(flag, flag.offVariant, 'KILL_SWITCH', null, null, null);
  }
  if (!flag.enabled) {
    return result(flag, flag.offVariant, 'OFF', null, null, null);
  }
  for (let index = 0; index < flag.rules.length; index++) {
    const rule = flag.rules[index];
    if (matchesRule(rule, context, segments)) {
      return serveResult(flag, rule.serve, context, 'RULE_MATCH', index, rule.id);
    }
  }
  return serveResult(flag, flag.fallthrough, context, 'FALLTHROUGH', null, null);
}
