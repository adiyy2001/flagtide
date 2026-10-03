import type { ConditionDraft } from './drafts';
import { attributeConditionDraftOf, newCondition, nextUid } from './drafts';
import type { Issue, SegmentModel } from './models';
import { conditionIssues } from './validation';
import { conditionBody } from './wire';
import type { SaveSegmentBody } from './wire';
import { isSlug } from './variants';

export interface GroupDraft {
  readonly uid: string;
  readonly conditions: readonly ConditionDraft[];
}

export interface SegmentDraft {
  readonly key: string;
  readonly name: string;
  readonly included: string;
  readonly excluded: string;
  readonly groups: readonly GroupDraft[];
}

export function splitContextKeys(text: string): string[] {
  return [
    ...new Set(
      text
        .split(/[\n,]+/u)
        .map((token) => token.trim())
        .filter((token) => token !== ''),
    ),
  ];
}

export function emptySegmentDraft(): SegmentDraft {
  return { key: '', name: '', included: '', excluded: '', groups: [] };
}

export function newGroup(): GroupDraft {
  return { uid: nextUid('group'), conditions: [newCondition('attribute')] };
}

export function segmentDraftOf(segment: SegmentModel): SegmentDraft {
  return {
    key: segment.key,
    name: segment.name,
    included: segment.included.join('\n'),
    excluded: segment.excluded.join('\n'),
    groups: segment.rules.map((group) => ({
      uid: nextUid('group'),
      conditions: group.map(attributeConditionDraftOf),
    })),
  };
}

export function segmentBody(draft: SegmentDraft): SaveSegmentBody {
  return {
    name: draft.name.trim(),
    included: splitContextKeys(draft.included),
    excluded: splitContextKeys(draft.excluded),
    rules: draft.groups.map((group) => group.conditions.map(conditionBody)),
  };
}

export function validateSegment(
  draft: SegmentDraft,
  isNew: boolean,
  existingKeys: readonly string[],
): Issue[] {
  const issues: Issue[] = [];
  if (isNew) {
    if (!isSlug(draft.key)) {
      issues.push({
        path: 'key',
        message: 'Use lowercase letters, digits, hyphens and underscores, up to 64, no dots',
      });
    } else if (existingKeys.includes(draft.key)) {
      issues.push({ path: 'key', message: 'A segment with this key already exists' });
    }
  }
  if (draft.name.trim() === '') {
    issues.push({ path: 'name', message: 'Give the segment a name' });
  }
  const included = new Set(splitContextKeys(draft.included));
  const overlap = splitContextKeys(draft.excluded).filter((key) => included.has(key));
  if (overlap.length > 0) {
    issues.push({ path: 'excluded', message: `${overlap.join(', ')} is both included and excluded` });
  }
  draft.groups.forEach((group, groupIndex) =>
    group.conditions.forEach((condition, index) =>
      issues.push(...conditionIssues(condition, `groups.${groupIndex}.conditions.${index}`, [])),
    ),
  );
  return issues;
}

export function serializeSegment(draft: SegmentDraft): string {
  return JSON.stringify({
    key: draft.key,
    name: draft.name,
    included: splitContextKeys(draft.included),
    excluded: splitContextKeys(draft.excluded),
    groups: draft.groups.map((group) =>
      group.conditions.map((condition) => [
        condition.attribute,
        condition.operator,
        condition.scalarType,
        condition.valuesText,
        condition.negate,
      ]),
    ),
  });
}
