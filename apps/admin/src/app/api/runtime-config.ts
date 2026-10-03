import { InjectionToken } from '@angular/core';

export interface RuntimeConfig {
  readonly apiUrl: string;
  readonly project: string;
  readonly adminKeys: Readonly<Record<string, string>>;
}

export const RUNTIME_CONFIG = new InjectionToken<RuntimeConfig>('RUNTIME_CONFIG');

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function parseRuntimeConfig(raw: unknown): RuntimeConfig {
  if (!isRecord(raw)) {
    throw new Error('config.json must hold an object');
  }
  const { apiUrl, project, adminKeys } = raw;
  if (typeof apiUrl !== 'string' || apiUrl === '') {
    throw new Error('config.json needs apiUrl');
  }
  if (typeof project !== 'string' || project === '') {
    throw new Error('config.json needs project');
  }
  const keys: Record<string, string> = {};
  if (isRecord(adminKeys)) {
    for (const [environment, key] of Object.entries(adminKeys)) {
      if (typeof key === 'string' && key !== '') {
        keys[environment] = key;
      }
    }
  }
  return { apiUrl: apiUrl.replace(/\/+$/u, ''), project, adminKeys: keys };
}

export async function loadRuntimeConfig(
  url = 'config.json',
  fetcher: typeof fetch = (input, init) => fetch(input, init),
): Promise<RuntimeConfig> {
  const response = await fetcher(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`config.json answered ${response.status}`);
  }
  return parseRuntimeConfig(await response.json());
}
