import { Injectable, inject } from '@angular/core';
import type { FlagType } from '@flagtide/core';
import type {
  ApiKeyModel,
  AuditEntryModel,
  FlagModel,
  ProjectModel,
  PropagationModel,
  ProvisionedEnvironmentModel,
  SegmentModel,
} from '../domain/models';
import type { EnvironmentSettingsBody, SaveSegmentBody, VariantBody } from '../domain/wire';
import { KeyStore } from './key-store';
import {
  toApiKey,
  toAuditEntry,
  toFlag,
  toProject,
  toPropagation,
  toProvisioned,
  toSegment,
} from './mappers';
import { ApiError, MissingKeyError } from './problem';
import type { Problem } from './problem';
import { RUNTIME_CONFIG } from './runtime-config';
import type { components } from './schema';
import { FETCH } from './tokens';

type Schemas = components['schemas'];

interface RequestOptions {
  readonly via: string | null;
  readonly read?: boolean;
  readonly body?: unknown;
  readonly revision?: number | null;
  readonly query?: Readonly<Record<string, string | number | boolean | undefined>>;
}

export interface FlagQuery {
  readonly text?: string;
  readonly type?: FlagType;
  readonly includeArchived?: boolean;
}

export interface CreateFlagBody {
  readonly key: string;
  readonly type: FlagType;
  readonly description: string;
  readonly variants: readonly VariantBody[];
  readonly offVariant: string;
  readonly fallthroughVariant: string;
}

export interface AuditQuery {
  readonly environment?: string;
  readonly entity?: string;
  readonly limit?: number;
  readonly offset?: number;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly keys = inject(KeyStore);
  private readonly fetcher = inject(FETCH);

  get project(): string {
    return this.config.project;
  }

  async loadProject(): Promise<ProjectModel> {
    return toProject(await this.send<Schemas['Project']>('GET', this.projectPath(), { via: null }));
  }

  async listFlags(query: FlagQuery = {}): Promise<FlagModel[]> {
    const body = await this.send<Schemas['Flag'][]>('GET', `${this.projectPath()}/flags`, {
      via: null,
      query: { q: query.text, type: query.type, includeArchived: query.includeArchived ?? true },
    });
    return body.map(toFlag);
  }

  async getFlag(key: string): Promise<FlagModel> {
    return toFlag(await this.send<Schemas['Flag']>('GET', this.flagPath(key), { via: null }));
  }

  async createFlag(via: string, body: CreateFlagBody): Promise<FlagModel> {
    return toFlag(await this.send<Schemas['Flag']>('POST', `${this.projectPath()}/flags`, { via, body }));
  }

  async updateDefinition(
    via: string,
    key: string,
    body: { description: string; variants: readonly VariantBody[] },
    revision: number | null,
  ): Promise<FlagModel> {
    return toFlag(await this.send<Schemas['Flag']>('PUT', this.flagPath(key), { via, body, revision }));
  }

  async configureEnvironment(
    environment: string,
    key: string,
    body: EnvironmentSettingsBody,
    revision: number | null,
  ): Promise<FlagModel> {
    const path = `${this.flagPath(key)}/environments/${encodeURIComponent(environment)}`;
    return toFlag(await this.send<Schemas['Flag']>('PUT', path, { via: environment, body, revision }));
  }

  async setEnabled(
    environment: string,
    key: string,
    enabled: boolean,
    revision: number | null,
  ): Promise<FlagModel> {
    const path = `${this.flagPath(key)}/environments/${encodeURIComponent(environment)}/enabled`;
    return toFlag(
      await this.send<Schemas['Flag']>('PUT', path, { via: environment, body: { enabled }, revision }),
    );
  }

  async setKillSwitch(environment: string, key: string, engaged: boolean): Promise<FlagModel> {
    const path = `${this.flagPath(key)}/environments/${encodeURIComponent(environment)}/kill-switch`;
    return toFlag(await this.send<Schemas['Flag']>(engaged ? 'POST' : 'DELETE', path, { via: environment }));
  }

  async archiveFlag(via: string, key: string, revision: number | null): Promise<FlagModel> {
    return toFlag(
      await this.send<Schemas['Flag']>('POST', `${this.flagPath(key)}/archive`, { via, revision }),
    );
  }

  async listSegments(environment: string): Promise<SegmentModel[]> {
    const body = await this.send<Schemas['Segment'][]>('GET', this.segmentsPath(environment), {
      via: environment,
      read: true,
    });
    return body.map(toSegment);
  }

  async saveSegment(
    environment: string,
    key: string,
    body: SaveSegmentBody,
    revision: number | null,
  ): Promise<SegmentModel> {
    const path = `${this.segmentsPath(environment)}/${encodeURIComponent(key)}`;
    return toSegment(await this.send<Schemas['Segment']>('PUT', path, { via: environment, body, revision }));
  }

  async deleteSegment(environment: string, key: string, revision: number | null): Promise<void> {
    const path = `${this.segmentsPath(environment)}/${encodeURIComponent(key)}`;
    await this.send<null>('DELETE', path, { via: environment, revision });
  }

  async listKeys(environment: string): Promise<ApiKeyModel[]> {
    const body = await this.send<Schemas['ApiKey'][]>(
      'GET',
      `${this.projectPath()}/environments/${encodeURIComponent(environment)}/keys`,
      { via: environment, read: true },
    );
    return body.map(toApiKey);
  }

  async createEnvironment(via: string, key: string, name: string): Promise<ProvisionedEnvironmentModel> {
    return toProvisioned(
      await this.send<Schemas['ProvisionedEnvironment']>('POST', `${this.projectPath()}/environments`, {
        via,
        body: { key, name },
      }),
    );
  }

  async readAudit(query: AuditQuery = {}): Promise<AuditEntryModel[]> {
    const body = await this.send<Schemas['AuditEntry'][]>('GET', `${this.projectPath()}/audit`, {
      via: query.environment ?? null,
      read: true,
      query: {
        environment: query.environment,
        entity: query.entity,
        limit: query.limit ?? 50,
        offset: query.offset ?? 0,
      },
    });
    return body.map(toAuditEntry);
  }

  async readPropagation(environment: string): Promise<PropagationModel> {
    const body = await this.send<Schemas['Propagation']>(
      'GET',
      `${this.projectPath()}/environments/${encodeURIComponent(environment)}/propagation`,
      { via: environment, read: true },
    );
    return toPropagation(body);
  }

  private projectPath(): string {
    return `/api/v1/projects/${encodeURIComponent(this.config.project)}`;
  }

  private flagPath(key: string): string {
    return `${this.projectPath()}/flags/${encodeURIComponent(key)}`;
  }

  private segmentsPath(environment: string): string {
    return `${this.projectPath()}/environments/${encodeURIComponent(environment)}/segments`;
  }

  private bearer(via: string | null, read: boolean): string {
    const own = via === null ? null : this.keys.keyFor(via);
    const key = own ?? (via === null || read ? this.keys.firstKey(via) : null);
    if (key === null) {
      throw new MissingKeyError(via ?? 'any environment');
    }
    return `Bearer ${key}`;
  }

  private async send<T>(method: string, path: string, options: RequestOptions): Promise<T> {
    const headers: Record<string, string> = {
      Accept: 'application/json, application/problem+json',
      Authorization: this.bearer(options.via, options.read ?? false),
    };
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (options.revision !== undefined && options.revision !== null) {
      headers['If-Match'] = `"${options.revision}"`;
    }
    const response = await this.fetcher(this.url(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    if (!response.ok) {
      throw new ApiError(response.status, await readProblem(response));
    }
    if (response.status === 204) {
      return null as T;
    }
    return (await response.json()) as T;
  }

  private url(path: string, query: RequestOptions['query']): string {
    const params = new URLSearchParams();
    Object.entries(query ?? {}).forEach(([name, value]) => {
      if (value !== undefined && value !== '') {
        params.set(name, String(value));
      }
    });
    const suffix = params.size > 0 ? `?${params.toString()}` : '';
    return `${this.config.apiUrl}${path}${suffix}`;
  }
}

async function readProblem(response: Response): Promise<Problem | null> {
  try {
    return (await response.json()) as Problem;
  } catch {
    return null;
  }
}
