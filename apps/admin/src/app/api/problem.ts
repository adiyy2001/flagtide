import type { components } from './schema';

export type Problem = components['schemas']['Problem'];

export interface FieldIssue {
  readonly field: string;
  readonly message: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly fieldIssues: readonly FieldIssue[];

  constructor(status: number, problem: Problem | null) {
    super(problem?.detail ?? problem?.title ?? `The server answered ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.fieldIssues = (problem?.errors ?? []).map((item) => ({
      field: item.field ?? '',
      message: item.message ?? '',
    }));
  }

  get isConflict(): boolean {
    return this.status === 409;
  }

  get isUnauthorized(): boolean {
    return this.status === 401 || this.status === 403;
  }
}

export class MissingKeyError extends Error {
  readonly environment: string;

  constructor(environment: string) {
    super(`No admin key is set for ${environment}. Add one on the Environments page.`);
    this.name = 'MissingKeyError';
    this.environment = environment;
  }
}

export function describeFailure(failure: unknown): string {
  if (failure instanceof ApiError && failure.fieldIssues.length > 0) {
    return failure.fieldIssues.map((item) => `${item.field}: ${item.message}`).join('; ');
  }
  return failure instanceof Error ? failure.message : 'Something went wrong';
}
