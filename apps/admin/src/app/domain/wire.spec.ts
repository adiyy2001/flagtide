import { evaluate } from '@flagwire/core';
import { describe, expect, it } from 'vitest';
import { booleanFlag, environmentConfig, rule } from '../../test-support/fixtures';
import { environmentDraftOf, newCondition, rolloutServeDraft } from './drafts';
import type { ConditionDraft } from './drafts';
import { conditionBody, environmentSettingsBody, flagConfigOf, variantBodies } from './wire';

describe('wire bodies', () => {
  it('numbers rules by position and turns percentages into thousandths', () => {
    const rollout = rolloutServeDraft(['on', 'off']);
    const draft = {
      ...environmentDraftOf(environmentConfig({ rules: [rule('first'), rule('second')] })),
      fallthrough: {
        ...rollout,
        rollout: rollout.rollout.map((entry, index) => ({
          ...entry,
          percent: index === 0 ? '12.5' : '87.5',
        })),
      },
    };
    const body = environmentSettingsBody(draft);
    expect(body.rules?.map((item) => [item.id, item.order])).toEqual([
      ['first', 0],
      ['second', 1],
    ]);
    expect(body.fallthrough).toEqual({
      rollout: [
        { variant: 'on', weight: 12500 },
        { variant: 'off', weight: 87500 },
      ],
    });
    expect(body.enabled).toBe(true);
    expect(body.offVariant).toBe('off');
  });

  it('sends typed condition values and keeps segment conditions apart', () => {
    const attribute: ConditionDraft = {
      ...newCondition('attribute'),
      attribute: ' age ',
      operator: 'in',
      scalarType: 'number',
      valuesText: '18, 21',
      negate: true,
    };
    expect(conditionBody(attribute)).toEqual({
      attribute: 'age',
      operator: 'in',
      values: [18, 21],
      negate: true,
    });
    expect(conditionBody(newCondition('segment', 'beta'))).toEqual({ segment: 'beta', negate: false });
  });

  it('serializes variant values by flag type', () => {
    expect(
      variantBodies('number', [
        { uid: 'a', key: 'low', valueText: '10' },
        { uid: 'b', key: 'bad', valueText: 'x' },
      ]),
    ).toEqual([
      { key: 'low', value: 10 },
      { key: 'bad', value: null },
    ]);
  });

  it('compiles a draft into a config the shared evaluator accepts', () => {
    const flag = booleanFlag('beta', {
      environments: {
        dev: environmentConfig({ rules: [rule('pro', { variant: 'on' })], fallthrough: { variant: 'off' } }),
      },
    });
    const config = flag.environments['dev'] ?? environmentConfig();
    const draft = environmentDraftOf(config);
    const compiled = flagConfigOf(flag, config, draft);
    const match = evaluate(compiled, { key: 'u1', attributes: { plan: 'pro' } });
    const miss = evaluate(compiled, { key: 'u1', attributes: { plan: 'free' } });
    expect([match.variantKey, match.reason, match.ruleId]).toEqual(['on', 'RULE_MATCH', 'pro']);
    expect([miss.variantKey, miss.reason]).toEqual(['off', 'FALLTHROUGH']);
  });

  it('keeps the kill switch of the saved configuration but the enabled state of the draft', () => {
    const flag = booleanFlag('beta');
    const config = environmentConfig({ killSwitch: true });
    const draft = { ...environmentDraftOf(config), enabled: true };
    const result = evaluate(flagConfigOf(flag, config, draft), { key: 'u', attributes: {} });
    expect(result.reason).toBe('KILL_SWITCH');
  });
});
