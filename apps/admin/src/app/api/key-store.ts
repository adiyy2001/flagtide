import { Injectable, computed, inject, signal } from '@angular/core';
import { LOCAL_STORE } from './tokens';
import { RUNTIME_CONFIG } from './runtime-config';

const STORAGE_KEY = 'flagtide.admin.keys';

function readStored(raw: string | null): Record<string, string> {
  if (raw === null) {
    return {};
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      return {};
    }
    return Object.fromEntries(
      Object.entries(parsed).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
    );
  } catch {
    return {};
  }
}

@Injectable({ providedIn: 'root' })
export class KeyStore {
  private readonly config = inject(RUNTIME_CONFIG);
  private readonly storage = inject(LOCAL_STORE);
  private readonly remembered = signal<Record<string, string>>(readStored(this.storage.get(STORAGE_KEY)));

  readonly keys = computed(() => ({ ...this.config.adminKeys, ...this.remembered() }));

  keyFor(environment: string): string | null {
    return this.keys()[environment] ?? null;
  }

  firstKey(preferred: string | null): string | null {
    const keys = this.keys();
    const own = preferred === null ? undefined : keys[preferred];
    return own ?? Object.values(keys)[0] ?? null;
  }

  remember(environment: string, key: string): void {
    this.remembered.update((current) => ({ ...current, [environment]: key }));
    this.persist();
  }

  forget(environment: string): void {
    this.remembered.update((current) =>
      Object.fromEntries(Object.entries(current).filter(([name]) => name !== environment)),
    );
    this.persist();
  }

  isRemembered(environment: string): boolean {
    return environment in this.remembered();
  }

  private persist(): void {
    this.storage.set(STORAGE_KEY, JSON.stringify(this.remembered()));
  }
}
