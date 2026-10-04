import type { Rule, Serve } from '@flagtide/core';
import type { EnvironmentConfigModel, FlagModel } from '../app/domain/models';

export function environmentConfig(overrides: Partial<EnvironmentConfigModel> = {}): EnvironmentConfigModel {
  return {
    enabled: true,
    killSwitch: false,
    offVariant: 'off',
    salt: 'ab12cd',
    rules: [],
    fallthrough: { variant: 'on' },
    ...overrides,
  };
}

export function booleanFlag(key: string, overrides: Partial<FlagModel> = {}): FlagModel {
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
    environments: { dev: environmentConfig(), prod: environmentConfig({ enabled: false }) },
    ...overrides,
  };
}

export function rule(id: string, serve: Serve = { variant: 'on' }, attribute = 'plan'): Rule {
  return { id, conditions: [{ attribute, operator: 'equals', values: ['pro'] }], serve };
}
