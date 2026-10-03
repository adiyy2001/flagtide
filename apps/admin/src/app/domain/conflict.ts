import type { JsonValue } from '@flagwire/core';
import { environmentDraftOf } from './drafts';
import type { DefinitionDraft, EnvironmentDraft } from './drafts';
import type { EnvironmentConfigModel, FlagModel } from './models';
import { environmentSettingsBody, variantBodies } from './wire';

export function asJson(value: unknown): JsonValue {
  return JSON.parse(JSON.stringify(value ?? null)) as JsonValue;
}

export function draftDefinitionSnapshot(flag: FlagModel, draft: DefinitionDraft): JsonValue {
  return asJson({ description: draft.description, variants: variantBodies(flag.type, draft.variants) });
}

export function savedDefinitionSnapshot(flag: FlagModel): JsonValue {
  return asJson({ description: flag.description, variants: flag.variants });
}

export function draftEnvironmentSnapshot(draft: EnvironmentDraft | undefined): JsonValue {
  return draft === undefined ? null : asJson(environmentSettingsBody(draft));
}

export function savedEnvironmentSnapshot(config: EnvironmentConfigModel | undefined): JsonValue {
  return config === undefined ? null : draftEnvironmentSnapshot(environmentDraftOf(config));
}
