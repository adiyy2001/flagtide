import type { FlagType, JsonValue, Rule, Segment, Serve, Variant } from '@flagtide/core';

export interface Issue {
  readonly path: string;
  readonly message: string;
}

export interface EnvironmentConfigModel {
  readonly enabled: boolean;
  readonly killSwitch: boolean;
  readonly offVariant: string;
  readonly salt: string;
  readonly rules: readonly Rule[];
  readonly fallthrough: Serve;
}

export interface FlagModel {
  readonly key: string;
  readonly type: FlagType;
  readonly description: string;
  readonly archived: boolean;
  readonly revision: number;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly variants: readonly Variant[];
  readonly environments: Readonly<Record<string, EnvironmentConfigModel>>;
}

export interface SegmentModel extends Segment {
  readonly name: string;
  readonly revision: number;
  readonly environment: string;
  readonly updatedAt: string;
}

export interface EnvironmentModel {
  readonly key: string;
  readonly name: string;
}

export interface ProjectModel {
  readonly key: string;
  readonly name: string;
  readonly environments: readonly EnvironmentModel[];
}

export interface ApiKeyModel {
  readonly id: string;
  readonly kind: 'admin' | 'sdk';
  readonly label: string;
  readonly environment: string;
  readonly sdkKey: string | null;
}

export interface IssuedKeyModel {
  readonly id: string;
  readonly kind: 'admin' | 'sdk';
  readonly label: string;
  readonly secret: string;
}

export interface ProvisionedEnvironmentModel extends EnvironmentModel {
  readonly keys: readonly IssuedKeyModel[];
}

export interface AuditEntryModel {
  readonly id: string;
  readonly at: string;
  readonly author: string;
  readonly action: string;
  readonly entityType: string;
  readonly entityKey: string;
  readonly environment: string | null;
  readonly environmentVersion: number | null;
  readonly before: JsonValue;
  readonly after: JsonValue;
}

export interface PropagationModel {
  readonly connectedClients: number;
  readonly samples: number;
  readonly p50Millis: number;
  readonly p95Millis: number;
  readonly p99Millis: number;
}

export interface FlagFilter {
  readonly text: string;
  readonly type: FlagType | 'all';
  readonly state: 'all' | 'enabled' | 'disabled' | 'killed' | 'archived';
}
