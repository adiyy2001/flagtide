import { describe, expect, it } from 'vitest';
import { booleanFlag, environmentConfig, rule } from '../../test-support/fixtures';
import { newCondition, newRuleDraft } from '../domain/drafts';
import { environmentSection, FlagEditorState, usedVariantKeys } from './flag-editor-state';

function stateWith(flag = booleanFlag('beta')): FlagEditorState {
  const state = new FlagEditorState(flag);
  state.setSegmentKeys('dev', ['beta-users']);
  return state;
}

function withRule(state: FlagEditorState, environment: string): void {
  const draft = state.environments()[environment];
  if (draft === undefined) {
    throw new Error('environment missing');
  }
  const added = { ...newRuleDraft([], 'on'), conditions: [] };
  state.setEnvironment(environment, { ...draft, rules: [...draft.rules, added] });
}

describe('FlagEditorState', () => {
  it('starts clean with a draft per environment', () => {
    const state = stateWith();
    expect(state.dirty()).toBe(false);
    expect(Object.keys(state.environments())).toEqual(['dev', 'prod']);
    expect(state.variantKeys()).toEqual(['on', 'off']);
  });

  it('notices edits per section and forgets them when discarded', () => {
    const state = stateWith();
    withRule(state, 'dev');
    expect(state.isDirty(environmentSection('dev'))).toBe(true);
    expect(state.isDirty(environmentSection('prod'))).toBe(false);
    expect(state.isDirty('definition')).toBe(false);
    expect(state.dirty()).toBe(true);
    state.discard(environmentSection('dev'));
    expect(state.dirty()).toBe(false);

    state.setDefinition({ ...state.definition(), description: 'changed' });
    expect(state.isDirty('definition')).toBe(true);
    state.discard('definition');
    expect(state.definition().description).toBe('beta description');
    expect(state.dirty()).toBe(false);
  });

  it('is not dirty again when an edit is typed back to the saved value', () => {
    const state = stateWith();
    const original = state.definition();
    state.setDefinition({ ...original, description: 'x' });
    state.setDefinition({ ...original });
    expect(state.definitionDirty()).toBe(false);
  });

  it('adopts a saved flag for the saved section and keeps edits in the other sections', () => {
    const state = stateWith();
    withRule(state, 'dev');
    state.setDefinition({ ...state.definition(), description: 'mine' });

    const saved = booleanFlag('beta', {
      revision: 2,
      environments: {
        dev: environmentConfig({ rules: [rule('server-rule')] }),
        prod: environmentConfig({ enabled: false }),
      },
    });
    state.accept(saved, environmentSection('dev'));

    expect(state.flag().revision).toBe(2);
    expect(state.environments()['dev']?.rules.map((item) => item.id)).toEqual(['server-rule']);
    expect(state.isDirty(environmentSection('dev'))).toBe(false);
    expect(state.definition().description).toBe('mine');
    expect(state.isDirty('definition')).toBe(true);
  });

  it("replaces clean sections with what the server has after a reload of someone else's change", () => {
    const state = stateWith();
    const theirs = booleanFlag('beta', {
      revision: 5,
      description: 'theirs',
      environments: { dev: environmentConfig({ enabled: false }), prod: environmentConfig() },
    });
    state.accept(theirs);
    expect(state.definition().description).toBe('theirs');
    expect(state.environments()['dev']?.enabled).toBe(false);
    expect(state.dirty()).toBe(false);
  });

  it('keeps a dirty section and measures it against the new server value', () => {
    const state = stateWith();
    withRule(state, 'dev');
    const theirs = booleanFlag('beta', {
      revision: 3,
      environments: { dev: environmentConfig({ enabled: false }), prod: environmentConfig() },
    });
    state.accept(theirs);
    expect(state.environments()['dev']?.rules).toHaveLength(1);
    expect(state.environments()['dev']?.enabled).toBe(true);
    expect(state.isDirty(environmentSection('dev'))).toBe(true);
    state.discard(environmentSection('dev'));
    expect(state.environments()['dev']?.enabled).toBe(false);
  });

  it('picks up environments that appear later', () => {
    const state = stateWith();
    state.accept(
      booleanFlag('beta', {
        environments: { dev: environmentConfig(), prod: environmentConfig(), staging: environmentConfig() },
      }),
    );
    expect(Object.keys(state.environments()).sort()).toEqual(['dev', 'prod', 'staging']);
  });

  it('validates against the saved variants and the segments of the environment', () => {
    const state = stateWith();
    const draft = state.environments()['dev'];
    if (draft === undefined) {
      throw new Error('environment missing');
    }
    const broken = newRuleDraft([], 'on');
    state.setEnvironment('dev', {
      ...draft,
      rules: [{ ...broken, conditions: [newCondition('segment', 'ghost')] }],
    });
    expect(state.environmentIssues()['dev']?.map((issue) => issue.path)).toEqual([
      'rules.0.conditions.0.segment',
    ]);
    expect(state.environmentIssues()['prod']).toEqual([]);
    expect(state.context('dev').segmentKeys).toEqual(['beta-users']);
    expect(state.context('prod').segmentKeys).toEqual([]);
  });

  it('blocks removing a variant that an environment serves', () => {
    const state = stateWith();
    state.setDefinition({
      ...state.definition(),
      variants: state.definition().variants.filter((variant) => variant.key !== 'off'),
    });
    expect(state.definitionIssues().map((issue) => issue.message)).toEqual([
      'Variant off is still used in dev',
    ]);
  });
});

describe('usedVariantKeys', () => {
  it('collects off, fallthrough, rule and rollout variants with the first environment that uses them', () => {
    const flag = booleanFlag('beta', {
      environments: {
        dev: environmentConfig({ offVariant: 'off', fallthrough: { variant: 'on' } }),
        prod: environmentConfig({
          offVariant: 'off',
          rules: [rule('r', { rollout: [{ variant: 'x', weight: 100000 }] })],
        }),
      },
    });
    expect([...usedVariantKeys(flag)]).toEqual([
      ['off', 'dev'],
      ['on', 'dev'],
      ['x', 'prod'],
    ]);
  });
});
