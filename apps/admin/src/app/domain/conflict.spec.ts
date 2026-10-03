import { describe, expect, it } from 'vitest';
import { booleanFlag, environmentConfig } from '../../test-support/fixtures';
import {
  asJson,
  draftDefinitionSnapshot,
  draftEnvironmentSnapshot,
  savedDefinitionSnapshot,
  savedEnvironmentSnapshot,
} from './conflict';
import { definitionDraftOf, environmentDraftOf } from './drafts';

describe('conflict snapshots', () => {
  it('strips undefined values', () => {
    expect(asJson({ a: undefined, b: 1 })).toEqual({ b: 1 });
    expect(asJson(undefined)).toBeNull();
  });

  it('gives equal snapshots for a clean draft and the saved flag', () => {
    const flag = booleanFlag('beta');
    expect(draftDefinitionSnapshot(flag, definitionDraftOf(flag))).toEqual(savedDefinitionSnapshot(flag));
  });

  it('gives equal environment snapshots for a clean draft and the saved config', () => {
    const config = environmentConfig();
    expect(draftEnvironmentSnapshot(environmentDraftOf(config))).toEqual(savedEnvironmentSnapshot(config));
  });

  it('uses null for a missing environment', () => {
    expect(draftEnvironmentSnapshot(undefined)).toBeNull();
    expect(savedEnvironmentSnapshot(undefined)).toBeNull();
  });
});
