import { describe, expect, it } from 'vitest';
import {
  definitionDraftOf,
  environmentDraftOf,
  fixedServeDraft,
  newCondition,
  newRuleDraft,
  rolloutServeDraft,
  variantDraftOf,
} from './drafts';
import type { EnvironmentDraft, RuleDraft } from './drafts';
import {
  conditionIssues,
  issuesAt,
  issuesUnder,
  validateDefinition,
  validateEnvironment,
  validateNewFlag,
  validateVariants,
} from './validation';
import { booleanFlag, environmentConfig } from '../../test-support/fixtures';

const context = { variantKeys: ['on', 'off'], segmentKeys: ['beta'] };

function draftWith(overrides: Partial<EnvironmentDraft> = {}): EnvironmentDraft {
  return { ...environmentDraftOf(environmentConfig()), ...overrides };
}

function ruleWith(overrides: Partial<RuleDraft> = {}): RuleDraft {
  const base = newRuleDraft([], 'on');
  return {
    ...base,
    conditions: [{ ...newCondition('attribute'), attribute: 'plan', valuesText: 'pro' }],
    ...overrides,
  };
}

describe('validateEnvironment', () => {
  it('accepts a complete configuration', () => {
    expect(validateEnvironment(draftWith({ rules: [ruleWith()] }), context)).toEqual([]);
  });

  it('wants known off and fallthrough variants', () => {
    const issues = validateEnvironment(
      draftWith({ offVariant: 'gone', fallthrough: fixedServeDraft('missing') }),
      context,
    );
    expect(issues.map((item) => item.path)).toEqual(['offVariant', 'fallthrough.variant']);
  });

  it('wants every rollout to add up to exactly 100 percent and says how far off it is', () => {
    const rollout = rolloutServeDraft(['on', 'off']);
    const short = {
      ...rollout,
      rollout: rollout.rollout.map((entry, index) => ({ ...entry, percent: index === 0 ? '40' : '50' })),
    };
    const long = { ...rollout, rollout: rollout.rollout.map((entry) => ({ ...entry, percent: '60' })) };
    expect(
      issuesAt(validateEnvironment(draftWith({ fallthrough: short }), context), 'fallthrough.rollout'),
    ).toEqual(['10% still to assign, it must be exactly 100%']);
    expect(
      issuesAt(validateEnvironment(draftWith({ fallthrough: long }), context), 'fallthrough.rollout'),
    ).toEqual(['Over by 20%, it must be exactly 100%']);
    expect(validateEnvironment(draftWith({ fallthrough: rollout }), context)).toEqual([]);
  });

  it('flags a rollout with no entries, an invalid percentage, an unknown or repeated variant', () => {
    const base = rolloutServeDraft(['on', 'off']);
    const [first, second] = base.rollout;
    if (first === undefined || second === undefined) {
      throw new Error('entries missing');
    }
    const empty = { ...base, rollout: [] };
    expect(
      issuesAt(validateEnvironment(draftWith({ fallthrough: empty }), context), 'fallthrough.rollout'),
    ).toEqual(['Add at least one variant to the rollout']);
    const broken = {
      ...base,
      rollout: [
        { ...first, percent: 'abc' },
        { ...second, variant: 'on' },
      ],
    };
    const issues = validateEnvironment(draftWith({ fallthrough: broken }), context);
    expect(issuesAt(issues, 'fallthrough.rollout.0.percent')).toHaveLength(1);
    expect(issuesAt(issues, 'fallthrough.rollout.1.variant')).toEqual(['Each variant can appear once']);
    const unknown = { ...base, rollout: [{ ...first, variant: 'nope' }, second] };
    expect(
      issuesAt(
        validateEnvironment(draftWith({ fallthrough: unknown }), context),
        'fallthrough.rollout.0.variant',
      ),
    ).toEqual(['Choose a variant']);
  });

  it('wants rule names that are present, short and unique', () => {
    const issues = validateEnvironment(
      draftWith({
        rules: [
          ruleWith({ id: 'same' }),
          ruleWith({ id: 'same' }),
          ruleWith({ id: '  ' }),
          ruleWith({ id: 'x'.repeat(65) }),
        ],
      }),
      context,
    );
    expect(issuesAt(issues, 'rules.0.id')).toEqual(['Another rule already uses this name']);
    expect(issuesAt(issues, 'rules.1.id')).toEqual(['Another rule already uses this name']);
    expect(issuesAt(issues, 'rules.2.id')).toEqual(['The rule name must be 1 to 64 characters']);
    expect(issuesAt(issues, 'rules.3.id')).toEqual(['The rule name must be 1 to 64 characters']);
  });

  it('collects issues of conditions and serve inside a rule', () => {
    const rule = ruleWith({
      conditions: [
        { ...newCondition('attribute'), attribute: '', valuesText: '' },
        newCondition('segment', 'ghost'),
      ],
      serve: fixedServeDraft('nope'),
    });
    const issues = validateEnvironment(draftWith({ rules: [rule] }), context);
    expect(issuesUnder(issues, 'rules.0').map((item) => item.path)).toEqual([
      'rules.0.conditions.0.attribute',
      'rules.0.conditions.0.values',
      'rules.0.conditions.1.segment',
      'rules.0.serve.variant',
    ]);
    expect(issuesUnder(issues, 'rules.1')).toEqual([]);
  });
});

describe('conditionIssues', () => {
  it('checks operator specific values', () => {
    const semver = {
      ...newCondition('attribute'),
      attribute: 'version',
      operator: 'semverGte' as const,
      valuesText: '2',
    };
    expect(issuesAt(conditionIssues(semver, 'c', []), 'c.values')[0]).toContain('is not a semantic version');
    const number = { ...semver, operator: 'gt' as const, valuesText: 'abc' };
    expect(issuesAt(conditionIssues(number, 'c', []), 'c.values')).toEqual(['abc is not a number']);
    expect(conditionIssues({ ...semver, valuesText: '2.0.0-rc.1' }, 'c', [])).toEqual([]);
  });

  it('accepts a segment that exists', () => {
    expect(conditionIssues(newCondition('segment', 'beta'), 'c', ['beta'])).toEqual([]);
  });
});

describe('validateVariants and validateDefinition', () => {
  const flag = booleanFlag('a');

  it('needs at least one variant', () => {
    expect(validateVariants('boolean', [])).toEqual([
      { path: 'variants', message: 'A flag needs at least one variant' },
    ]);
  });

  it('checks keys and values per variant', () => {
    const [on] = definitionDraftOf(flag).variants;
    if (on === undefined) {
      throw new Error('variant missing');
    }
    const issues = validateVariants('boolean', [
      { ...on, key: 'Bad Key' },
      { ...on, key: 'dup' },
      { ...on, key: 'dup', valueText: 'maybe' },
    ]);
    expect(issuesAt(issues, 'variants.0.key')).toHaveLength(1);
    expect(issuesAt(issues, 'variants.1.key')).toEqual(['Variant keys must be unique']);
    expect(issuesAt(issues, 'variants.2.value')).toEqual(['Choose true or false']);
  });

  it('refuses to drop a variant that an environment still serves', () => {
    const draft = definitionDraftOf(flag);
    const withoutOff = { ...draft, variants: draft.variants.filter((variant) => variant.key !== 'off') };
    const used = new Map([['off', 'dev']]);
    expect(validateDefinition('boolean', withoutOff, used)).toEqual([
      { path: 'variants', message: 'Variant off is still used in dev' },
    ]);
    expect(validateDefinition('boolean', draft, used)).toEqual([]);
  });
});

describe('validateNewFlag', () => {
  const variants = [
    variantDraftOf({ key: 'on', value: true }, 'boolean'),
    variantDraftOf({ key: 'off', value: false }, 'boolean'),
  ];
  const valid = {
    key: 'new-flag',
    type: 'boolean' as const,
    description: '',
    variants,
    offVariant: 'off',
    fallthroughVariant: 'on',
  };

  it('accepts a good flag', () => {
    expect(validateNewFlag(valid)).toEqual([]);
  });

  it('wants a slug key and known off and fallthrough variants', () => {
    const issues = validateNewFlag({ ...valid, key: 'new.flag', offVariant: 'x', fallthroughVariant: 'y' });
    expect(issues.map((item) => item.path).sort()).toEqual(['fallthroughVariant', 'key', 'offVariant']);
  });
});
