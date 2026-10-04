import type { Condition, JsonValue, Operator, Rule, Scalar, Serve } from '@flagtide/core';
import type {
  ApiKeyModel,
  AuditEntryModel,
  EnvironmentConfigModel,
  FlagModel,
  IssuedKeyModel,
  ProjectModel,
  PropagationModel,
  ProvisionedEnvironmentModel,
  SegmentModel,
} from '../domain/models';
import type { components } from './schema';

type Schemas = components['schemas'];

function isScalar(value: unknown): value is Scalar {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean';
}

function toJson(value: unknown): JsonValue {
  return value === undefined ? null : (value as JsonValue);
}

export function toServe(dto: Schemas['Serve'] | undefined): Serve {
  if (dto?.rollout !== undefined) {
    return {
      rollout: dto.rollout.map((entry) => ({ variant: entry.variant ?? '', weight: entry.weight ?? 0 })),
    };
  }
  return { variant: dto?.variant ?? '' };
}

export function toCondition(dto: Schemas['Condition']): Condition {
  const negate = dto.negate ?? false;
  if (dto.segment !== undefined) {
    return { segment: dto.segment, negate };
  }
  return {
    attribute: dto.attribute ?? '',
    operator: (dto.operator ?? 'equals') as Operator,
    values: (dto.values ?? []).filter(isScalar),
    negate,
  };
}

function toRule(dto: Schemas['Rule']): Rule {
  return {
    id: dto.id ?? '',
    conditions: (dto.conditions ?? []).map(toCondition),
    serve: toServe(dto.serve),
  };
}

function toEnvironmentConfig(dto: Schemas['EnvironmentConfig']): EnvironmentConfigModel {
  return {
    enabled: dto.enabled ?? false,
    killSwitch: dto.killSwitch ?? false,
    offVariant: dto.offVariant ?? '',
    salt: dto.salt ?? '',
    rules: [...(dto.rules ?? [])].sort((a, b) => (a.order ?? 0) - (b.order ?? 0)).map(toRule),
    fallthrough: toServe(dto.fallthrough),
  };
}

export function toFlag(dto: Schemas['Flag']): FlagModel {
  const environments = Object.fromEntries(
    Object.entries(dto.environments ?? {}).map(([key, config]) => [key, toEnvironmentConfig(config)]),
  );
  return {
    key: dto.key ?? '',
    type: (dto.type ?? 'boolean') as FlagModel['type'],
    description: dto.description ?? '',
    archived: dto.archived ?? false,
    revision: dto.revision ?? 0,
    createdAt: dto.createdAt ?? '',
    updatedAt: dto.updatedAt ?? '',
    variants: (dto.variants ?? []).map((variant) => ({
      key: variant.key ?? '',
      value: toJson(variant.value),
    })),
    environments,
  };
}

export function toSegment(dto: Schemas['Segment']): SegmentModel {
  return {
    key: dto.key ?? '',
    name: dto.name ?? '',
    revision: dto.revision ?? 0,
    environment: dto.environment ?? '',
    updatedAt: dto.updatedAt ?? '',
    included: dto.included ?? [],
    excluded: dto.excluded ?? [],
    rules: (dto.rules ?? []).map((group) =>
      group
        .map(toCondition)
        .filter(
          (condition): condition is Exclude<Condition, { segment: string }> => 'attribute' in condition,
        ),
    ),
  };
}

export function toProject(dto: Schemas['Project']): ProjectModel {
  return {
    key: dto.key ?? '',
    name: dto.name ?? '',
    environments: (dto.environments ?? []).map((environment) => ({
      key: environment.key ?? '',
      name: environment.name ?? '',
    })),
  };
}

function toKind(kind: string | undefined): 'admin' | 'sdk' {
  return kind === 'sdk' ? 'sdk' : 'admin';
}

export function toApiKey(dto: Schemas['ApiKey']): ApiKeyModel {
  return {
    id: dto.id ?? '',
    kind: toKind(dto.kind),
    label: dto.label ?? '',
    environment: dto.environment ?? '',
    sdkKey: dto.sdkKey ?? null,
  };
}

function toIssuedKey(dto: Schemas['IssuedKey']): IssuedKeyModel {
  return { id: dto.id ?? '', kind: toKind(dto.kind), label: dto.label ?? '', secret: dto.secret ?? '' };
}

export function toProvisioned(dto: Schemas['ProvisionedEnvironment']): ProvisionedEnvironmentModel {
  return { key: dto.key ?? '', name: dto.name ?? '', keys: (dto.keys ?? []).map(toIssuedKey) };
}

export function toAuditEntry(dto: Schemas['AuditEntry']): AuditEntryModel {
  return {
    id: dto.id ?? '',
    at: dto.at ?? '',
    author: dto.author ?? '',
    action: dto.action ?? '',
    entityType: dto.entityType ?? '',
    entityKey: dto.entityKey ?? '',
    environment: dto.environment ?? null,
    environmentVersion: dto.environmentVersion ?? null,
    before: toJson(dto.before),
    after: toJson(dto.after),
  };
}

export function toPropagation(dto: Schemas['Propagation']): PropagationModel {
  return {
    connectedClients: dto.connectedClients ?? 0,
    samples: dto.samples ?? 0,
    p50Millis: dto.p50Millis ?? 0,
    p95Millis: dto.p95Millis ?? 0,
    p99Millis: dto.p99Millis ?? 0,
  };
}
