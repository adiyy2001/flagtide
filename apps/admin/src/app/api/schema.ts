export interface paths {
  '/api/v1/projects/{project}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Project'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/audit': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: {
          entity?: string;
          environment?: string;
          limit?: number;
          offset?: number;
        };
        header?: never;
        path: {
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['AuditEntry'][];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/environments': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['CreateEnvironment'];
        };
      };
      responses: {
        201: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['ProvisionedEnvironment'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/environments/{environment}/keys': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['ApiKey'][];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/environments/{environment}/propagation': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Propagation'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/environments/{environment}/segments': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Segment'][];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/environments/{environment}/segments/{key}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Segment'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['SaveSegment'];
        };
      };
      responses: {
        200: {
          headers: {
            ETag?: unknown;
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Segment'];
          };
        };
        201: {
          headers: {
            ETag?: unknown;
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Segment'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    post?: never;
    delete: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        204: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: {
          includeArchived?: boolean;
          q?: string;
          type?: string;
        };
        header?: never;
        path: {
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'][];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['CreateFlag'];
        };
      };
      responses: {
        201: {
          headers: {
            ETag?: unknown;
            Location?: unknown;
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags/{key}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            ETag?: unknown;
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['UpdateFlag'];
        };
      };
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags/{key}/archive': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags/{key}/environments/{environment}': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['EnvironmentSettings'];
        };
      };
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags/{key}/environments/{environment}/enabled': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put: {
      parameters: {
        query?: never;
        header?: {
          'If-Match'?: string;
        };
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody: {
        content: {
          'application/json': components['schemas']['Enabled'];
        };
      };
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/api/v1/projects/{project}/flags/{key}/environments/{environment}/kill-switch': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get?: never;
    put?: never;
    post: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    delete: {
      parameters: {
        query?: never;
        header?: never;
        path: {
          environment: string;
          key: string;
          project: string;
        };
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Flag'];
          };
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        404: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        409: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
  '/sdk/v1/snapshot': {
    parameters: {
      query?: never;
      header?: never;
      path?: never;
      cookie?: never;
    };
    get: {
      parameters: {
        query?: never;
        header?: {
          'If-None-Match'?: string;
        };
        path?: never;
        cookie?: never;
      };
      requestBody?: never;
      responses: {
        200: {
          headers: {
            ETag?: unknown;
            [name: string]: unknown;
          };
          content: {
            'application/json': components['schemas']['Snapshot'];
          };
        };
        304: {
          headers: {
            [name: string]: unknown;
          };
          content?: never;
        };
        400: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        401: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
        403: {
          headers: {
            [name: string]: unknown;
          };
          content: {
            'application/problem+json': components['schemas']['Problem'];
          };
        };
      };
    };
    put?: never;
    post?: never;
    delete?: never;
    options?: never;
    head?: never;
    patch?: never;
    trace?: never;
  };
}
export type webhooks = Record<string, never>;
export interface components {
  schemas: {
    ApiKey: {
      environment?: string;
      id?: string;
      kind?: string;
      label?: string;
      sdkKey?: string;
    };
    AuditEntry: {
      action?: string;
      after?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'];
      at?: string;
      author?: string;
      before?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'];
      entityKey?: string;
      entityType?: string;
      environment?: string;
      environmentVersion?: number;
      id?: string;
    };
    Condition: {
      attribute?: string;
      negate?: boolean;
      negated?: boolean;
      operator?: string;
      segment?: string;
      values?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'][];
    };
    CreateEnvironment: {
      key?: string;
      name?: string;
    };
    CreateFlag: {
      description?: string;
      fallthroughVariant?: string;
      key?: string;
      offVariant?: string;
      type?: 'boolean' | 'string' | 'number' | 'json';
      variants?: components['schemas']['Variant'][];
    };
    Enabled: {
      enabled?: boolean;
    };
    Environment: {
      key?: string;
      name?: string;
    };
    EnvironmentConfig: {
      enabled?: boolean;
      fallthrough?: components['schemas']['Serve'];
      killSwitch?: boolean;
      offVariant?: string;
      rules?: components['schemas']['Rule'][];
      salt?: string;
    };
    EnvironmentSettings: {
      enabled?: boolean;
      fallthrough?: components['schemas']['Serve'];
      offVariant?: string;
      rules?: components['schemas']['Rule'][];
    };
    FieldProblem: {
      field?: string;
      message?: string;
    };
    Flag: {
      archived?: boolean;
      createdAt?: string;
      description?: string;
      environments?: {
        [key: string]: components['schemas']['EnvironmentConfig'];
      };
      key?: string;
      revision?: number;
      type?: string;
      updatedAt?: string;
      variants?: components['schemas']['Variant'][];
    };
    IssuedKey: {
      id?: string;
      kind?: string;
      label?: string;
      secret?: string;
    };
    Problem: {
      detail?: string;
      errors?: components['schemas']['FieldProblem'][];
      status?: number;
      title?: string;
      type?: string;
    };
    Project: {
      createdAt?: string;
      environments?: components['schemas']['Environment'][];
      key?: string;
      name?: string;
      revision?: number;
    };
    Propagation: {
      connectedClients?: number;
      p50Millis?: number;
      p95Millis?: number;
      p99Millis?: number;
      samples?: number;
    };
    ProvisionedEnvironment: {
      key?: string;
      keys?: components['schemas']['IssuedKey'][];
      name?: string;
    };
    RolloutEntry: {
      variant?: string;
      weight?: number;
    };
    Rule: {
      conditions?: components['schemas']['Condition'][];
      id?: string;
      order?: number;
      serve?: components['schemas']['Serve'];
    };
    SaveSegment: {
      excluded?: string[];
      included?: string[];
      name?: string;
      rules?: components['schemas']['Condition'][][];
    };
    Segment: {
      createdAt?: string;
      environment?: string;
      excluded?: string[];
      included?: string[];
      key?: string;
      name?: string;
      revision?: number;
      rules?: components['schemas']['Condition'][][];
      updatedAt?: string;
    };
    Serve: {
      rollout?: components['schemas']['RolloutEntry'][];
      variant?: string;
    };
    Snapshot: {
      committedAtMs?: number;
      flags?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'][];
      segments?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'][];
      v?: number;
    };
    UpdateFlag: {
      description?: string;
      variants?: components['schemas']['Variant'][];
    };
    Variant: {
      key?: string;
      value?: components['schemas']['com.fasterxml.jackson.databind.JsonNode'];
    };
    'com.fasterxml.jackson.databind.JsonNode': unknown;
  };
  responses: never;
  parameters: never;
  requestBodies: never;
  headers: never;
  pathItems: never;
}
export type SchemaApiKey = components['schemas']['ApiKey'];
export type SchemaAuditEntry = components['schemas']['AuditEntry'];
export type SchemaCondition = components['schemas']['Condition'];
export type SchemaCreateEnvironment = components['schemas']['CreateEnvironment'];
export type SchemaCreateFlag = components['schemas']['CreateFlag'];
export type SchemaEnabled = components['schemas']['Enabled'];
export type SchemaEnvironment = components['schemas']['Environment'];
export type SchemaEnvironmentConfig = components['schemas']['EnvironmentConfig'];
export type SchemaEnvironmentSettings = components['schemas']['EnvironmentSettings'];
export type SchemaFieldProblem = components['schemas']['FieldProblem'];
export type SchemaFlag = components['schemas']['Flag'];
export type SchemaIssuedKey = components['schemas']['IssuedKey'];
export type SchemaProblem = components['schemas']['Problem'];
export type SchemaProject = components['schemas']['Project'];
export type SchemaPropagation = components['schemas']['Propagation'];
export type SchemaProvisionedEnvironment = components['schemas']['ProvisionedEnvironment'];
export type SchemaRolloutEntry = components['schemas']['RolloutEntry'];
export type SchemaRule = components['schemas']['Rule'];
export type SchemaSaveSegment = components['schemas']['SaveSegment'];
export type SchemaSegment = components['schemas']['Segment'];
export type SchemaServe = components['schemas']['Serve'];
export type SchemaSnapshot = components['schemas']['Snapshot'];
export type SchemaUpdateFlag = components['schemas']['UpdateFlag'];
export type SchemaVariant = components['schemas']['Variant'];
export type SchemaComFasterxmlJacksonDatabindJsonNode =
  components['schemas']['com.fasterxml.jackson.databind.JsonNode'];
export type $defs = Record<string, never>;
export type operations = Record<string, never>;
