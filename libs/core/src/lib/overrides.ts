import type { Observable } from 'rxjs';
import { Subject } from 'rxjs';
import type { KeyValueStore } from './storage.js';
import type { JsonValue } from './types.js';

/** Local values that win over what the server says, for development and demos. They never leave the browser. */
export class FlagOverrides {
  private readonly values = new Map<string, JsonValue>();
  private readonly changed = new Subject<void>();
  private readonly store: KeyValueStore | null;
  private readonly storageKey: string;

  constructor(store: KeyValueStore | null = null, storageKey = 'flagwire:overrides') {
    this.store = store;
    this.storageKey = storageKey;
    this.restore();
  }

  get changes$(): Observable<void> {
    return this.changed.asObservable();
  }

  get(key: string): JsonValue | undefined {
    return this.values.get(key);
  }

  entries(): ReadonlyMap<string, JsonValue> {
    return new Map(this.values);
  }

  set(key: string, value: JsonValue): void {
    this.values.set(key, value);
    this.commit();
  }

  clear(key: string): void {
    if (this.values.delete(key)) {
      this.commit();
    }
  }

  clearAll(): void {
    if (this.values.size > 0) {
      this.values.clear();
      this.commit();
    }
  }

  private commit(): void {
    this.store?.set(this.storageKey, JSON.stringify(Object.fromEntries(this.values)));
    this.changed.next();
  }

  private restore(): void {
    const text = this.store?.get(this.storageKey) ?? null;
    if (text === null) {
      return;
    }
    try {
      const parsed: unknown = JSON.parse(text);
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        Object.entries(parsed).forEach(([key, value]) => this.values.set(key, value as JsonValue));
      }
    } catch {
      this.store?.remove(this.storageKey);
    }
  }
}
