import { describe, expect, it } from 'vitest';
import {
  toApiKey,
  toAuditEntry,
  toCondition,
  toFlag,
  toPropagation,
  toProject,
  toProvisioned,
  toSegment,
  toServe,
} from './mappers';

describe('mappers', () => {
  it('turns an empty flag DTO into a strict model with defaults', () => {
    const flag = toFlag({});
    expect(flag).toMatchObject({
      key: '',
      type: 'boolean',
      archived: false,
      revision: 0,
      variants: [],
      environments: {},
    });
  });

  it('orders rules by their order field and keeps serve shapes', () => {
    const flag = toFlag({
      key: 'f',
      type: 'string',
      variants: [{ key: 'a', value: 'x' }, { key: 'b' }],
      environments: {
        dev: {
          enabled: true,
          rules: [
            { id: 'second', order: 1, conditions: [], serve: { variant: 'b' } },
            {
              id: 'first',
              order: 0,
              conditions: [{ attribute: 'plan', operator: 'in', values: ['pro', 2, true, null] }],
              serve: { rollout: [{ variant: 'a', weight: 60000 }, {}] },
            },
          ],
        },
      },
    });
    const dev = flag.environments['dev'];
    expect(dev?.rules.map((rule) => rule.id)).toEqual(['first', 'second']);
    expect(dev?.rules[0]?.conditions[0]).toMatchObject({ values: ['pro', 2, true] });
    expect(dev?.rules[0]?.serve).toEqual({
      rollout: [
        { variant: 'a', weight: 60000 },
        { variant: '', weight: 0 },
      ],
    });
    expect(flag.variants[1]?.value).toBeNull();
  });

  it('maps segment conditions and defaults', () => {
    expect(toCondition({ segment: 'beta', negate: true })).toEqual({ segment: 'beta', negate: true });
    expect(toCondition({})).toEqual({ attribute: '', operator: 'equals', values: [], negate: false });
    expect(toServe(undefined)).toEqual({ variant: '' });
  });

  it('maps segments and drops segment references inside groups', () => {
    const segment = toSegment({
      key: 's',
      rules: [[{ attribute: 'plan', operator: 'equals', values: ['pro'] }, { segment: 'other' }]],
    });
    expect(segment.rules[0]).toHaveLength(1);
    expect(segment).toMatchObject({ name: '', included: [], excluded: [], revision: 0 });
  });

  it('maps project, keys, provisioned environments, audit and propagation', () => {
    expect(toProject({ environments: [{ key: 'dev' }] }).environments).toEqual([{ key: 'dev', name: '' }]);
    expect(toApiKey({ kind: 'sdk', sdkKey: 'abc' })).toMatchObject({ kind: 'sdk', sdkKey: 'abc' });
    expect(toApiKey({})).toMatchObject({ kind: 'admin', sdkKey: null });
    expect(
      toProvisioned({ key: 'qa', keys: [{ id: '1', kind: 'admin', secret: 's' }] }).keys[0]?.secret,
    ).toBe('s');
    expect(toAuditEntry({ id: '1' })).toMatchObject({
      environment: null,
      environmentVersion: null,
      before: null,
    });
    expect(toPropagation({ connectedClients: 3 })).toMatchObject({ connectedClients: 3, p95Millis: 0 });
  });
});
