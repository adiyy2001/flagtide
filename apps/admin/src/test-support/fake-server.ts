import type { components } from '../app/api/schema';

type Schemas = components['schemas'];

export interface RecordedCall {
  readonly method: string;
  readonly path: string;
  readonly search: string;
  readonly authorization: string | null;
  readonly ifMatch: string | null;
  readonly body: unknown;
}

export function wireFlag(key: string, overrides: Partial<Schemas['Flag']> = {}): Schemas['Flag'] {
  return {
    key,
    type: 'boolean',
    description: `${key} description`,
    archived: false,
    revision: 1,
    createdAt: '2026-10-03T10:00:00Z',
    updatedAt: '2026-10-03T10:00:00Z',
    variants: [
      { key: 'on', value: true },
      { key: 'off', value: false },
    ],
    environments: {
      dev: {
        enabled: true,
        killSwitch: false,
        offVariant: 'off',
        salt: 'ab12cd',
        rules: [],
        fallthrough: { variant: 'on' },
      },
      prod: {
        enabled: false,
        killSwitch: false,
        offVariant: 'off',
        salt: 'ef34ab',
        rules: [],
        fallthrough: { variant: 'off' },
      },
    },
    ...overrides,
  };
}

export class FakeServer {
  readonly calls: RecordedCall[] = [];
  flags: Schemas['Flag'][] = [wireFlag('checkout'), wireFlag('beta-banner', { revision: 3 })];
  segments: Schemas['Segment'][] = [];
  audit: Schemas['AuditEntry'][] = [];
  propagation: Schemas['Propagation'] = {
    connectedClients: 0,
    samples: 0,
    p50Millis: 0,
    p95Millis: 0,
    p99Millis: 0,
  };
  environments = [
    { key: 'dev', name: 'Development' },
    { key: 'prod', name: 'Production' },
  ];
  failNext: { status: number; problem?: Schemas['Problem'] } | null = null;
  conflictOnNextWrite = false;

  readonly fetch: typeof fetch = async (input, init) => {
    const url = new URL(String(input));
    const method = init?.method ?? 'GET';
    const headers = new Headers(init?.headers);
    const body: unknown = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
    this.calls.push({
      method,
      path: url.pathname,
      search: url.search,
      authorization: headers.get('Authorization'),
      ifMatch: headers.get('If-Match'),
      body,
    });
    if (this.failNext !== null) {
      const failure = this.failNext;
      this.failNext = null;
      return this.respond(failure.status, failure.problem ?? { title: 'Failure' });
    }
    if (method !== 'GET' && this.conflictOnNextWrite) {
      this.conflictOnNextWrite = false;
      return this.respond(409, { title: 'Conflict', detail: 'The flag changed since you loaded it' });
    }
    return this.route(method, url, body);
  };

  callsTo(method: string, pathPart: string): RecordedCall[] {
    return this.calls.filter((call) => call.method === method && call.path.includes(pathPart));
  }

  private respond(status: number, body: unknown): Response {
    if (status === 204) {
      return new Response(null, { status });
    }
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
    });
  }

  private route(method: string, url: URL, body: unknown): Response {
    const parts = url.pathname.replace('/api/v1/projects/', '').split('/').map(decodeURIComponent);
    const [, section, key, sub, subKey, action] = parts;
    if (section === undefined) {
      return this.respond(200, { key: 'demo', name: 'Demo', environments: this.environments });
    }
    if (section === 'flags') {
      return this.routeFlags(method, url, key, sub, subKey, action, body);
    }
    if (section === 'audit') {
      const environment = url.searchParams.get('environment');
      const entity = url.searchParams.get('entity');
      const offset = Number(url.searchParams.get('offset') ?? 0);
      const limit = Number(url.searchParams.get('limit') ?? 50);
      const matching = this.audit
        .filter((entry) => environment === null || entry.environment === environment)
        .filter((entry) => entity === null || entry.entityKey === entity);
      return this.respond(200, matching.slice(offset, offset + limit));
    }
    if (section === 'environments' && key === undefined) {
      const created = body as { key: string; name: string };
      this.environments.push({ key: created.key, name: created.name });
      return this.respond(201, {
        key: created.key,
        name: created.name,
        keys: [
          { id: 'k1', kind: 'admin', label: 'admin', secret: 'fw_admin_secret' },
          { id: 'k2', kind: 'sdk', label: 'sdk', secret: 'fw_sdk_secret' },
        ],
      });
    }
    if (section === 'environments' && sub === 'keys') {
      return this.respond(200, [
        { id: 'a', kind: 'admin', label: 'admin', environment: key },
        { id: 's', kind: 'sdk', label: 'sdk', environment: key, sdkKey: `sdk-${key}` },
      ]);
    }
    if (section === 'environments' && sub === 'propagation') {
      return this.respond(200, this.propagation);
    }
    if (section === 'environments' && sub === 'segments') {
      return this.routeSegments(method, key ?? '', subKey, body);
    }
    return this.respond(404, { title: 'Not found' });
  }

  private routeSegments(
    method: string,
    environment: string,
    key: string | undefined,
    body: unknown,
  ): Response {
    if (method === 'GET') {
      return this.respond(
        200,
        this.segments.filter((segment) => segment.environment === environment),
      );
    }
    if (method === 'DELETE') {
      this.segments = this.segments.filter((segment) => segment.key !== key);
      return this.respond(204, null);
    }
    const saved: Schemas['Segment'] = {
      ...(body as Schemas['Segment']),
      key,
      environment,
      revision: 1,
      updatedAt: '2026-10-03T11:00:00Z',
    };
    this.segments = [...this.segments.filter((segment) => segment.key !== key), saved];
    return this.respond(200, saved);
  }

  private routeFlags(
    method: string,
    url: URL,
    key: string | undefined,
    sub: string | undefined,
    environment: string | undefined,
    action: string | undefined,
    body: unknown,
  ): Response {
    if (key === undefined) {
      if (method === 'POST') {
        const created = wireFlag((body as { key: string }).key, body as Partial<Schemas['Flag']>);
        this.flags = [...this.flags, created];
        return this.respond(201, created);
      }
      const text = url.searchParams.get('q')?.toLowerCase() ?? '';
      return this.respond(
        200,
        this.flags.filter((flag) => `${flag.key} ${flag.description}`.toLowerCase().includes(text)),
      );
    }
    const current = this.flags.find((flag) => flag.key === key);
    if (current === undefined) {
      return this.respond(404, { title: 'Flag not found' });
    }
    if (method === 'GET') {
      return this.respond(200, current);
    }
    const updated = this.mutate(current, method, sub, environment, action, body);
    this.flags = this.flags.map((flag) => (flag.key === key ? updated : flag));
    return this.respond(200, updated);
  }

  private mutate(
    current: Schemas['Flag'],
    method: string,
    sub: string | undefined,
    environment: string | undefined,
    action: string | undefined,
    body: unknown,
  ): Schemas['Flag'] {
    const next: Schemas['Flag'] = { ...current, revision: (current.revision ?? 0) + 1 };
    if (sub === 'archive') {
      return { ...next, archived: true };
    }
    if (sub === undefined) {
      return { ...next, ...(body as Partial<Schemas['Flag']>) };
    }
    const existing = current.environments?.[environment ?? ''] ?? {};
    let config: Schemas['EnvironmentConfig'];
    if (action === 'enabled') {
      config = { ...existing, enabled: (body as { enabled: boolean }).enabled };
    } else if (action === 'kill-switch') {
      config = { ...existing, killSwitch: method === 'POST' };
    } else {
      config = { ...existing, ...(body as Schemas['EnvironmentConfig']) };
    }
    return { ...next, environments: { ...current.environments, [environment ?? '']: config } };
  }
}
