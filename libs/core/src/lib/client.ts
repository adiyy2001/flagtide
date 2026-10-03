import { Subject, map, merge } from 'rxjs';
import type { SchedulerLike, Observable } from 'rxjs';
import {
  ConnectionManager,
  createBrowserEnvironment,
  createBrowserSocketFactory,
} from './connection-manager.js';
import type {
  ConnectionStatus,
  ConnectionTiming,
  NetworkEnvironment,
  SocketFactory,
  StreamError,
} from './connection-manager.js';
import { FlagStore } from './flag-store.js';
import type { FlagSnapshot } from './flag-store.js';
import { FlagOverrides } from './overrides.js';
import { resolveFlag } from './resolve.js';
import type { Resolution } from './resolve.js';
import { createLocalStore, createSnapshotStorage, snapshotStorageKey } from './storage.js';
import type { KeyValueStore, SnapshotStorage } from './storage.js';
import type { EvaluationContext, FlagType, JsonValue } from './types.js';

const SDK_NAME = 'flagwire-core/0.1.0';
const ANONYMOUS_ID_KEY = 'flagwire:anonymous-id';

/** Everything {@link createFlagwireClient} accepts. Only `streamUrl` and `sdkKey` are required. */
export interface FlagwireClientOptions {
  /** The stream endpoint, for example `ws://localhost:18081/sdk/v1/stream`. */
  readonly streamUrl: string;
  /** The read-only SDK key of one environment. */
  readonly sdkKey: string;
  /** Who flags are evaluated for. Without it the client uses an anonymous id that is kept in storage. */
  readonly context?: EvaluationContext;
  /** A snapshot to start from, for example one rendered on the server. It wins over the stored snapshot. */
  readonly initialSnapshot?: FlagSnapshot;
  /** Where the last snapshot, the overrides and the anonymous id live. Defaults to `localStorage` when there is one. */
  readonly store?: KeyValueStore;
  readonly socketFactory?: SocketFactory;
  readonly environment?: NetworkEnvironment;
  readonly scheduler?: SchedulerLike;
  readonly random?: () => number;
  readonly timing?: Partial<ConnectionTiming>;
}

function randomIdentifier(): string {
  const generator = (globalThis as { crypto?: { randomUUID?: () => string } }).crypto;
  return generator?.randomUUID?.() ?? Math.random().toString(36).slice(2) + Date.now().toString(36);
}

function anonymousContext(store: KeyValueStore): EvaluationContext {
  const known = store.get(ANONYMOUS_ID_KEY);
  const key = known ?? randomIdentifier();
  if (known === null) {
    store.set(ANONYMOUS_ID_KEY, key);
  }
  return { key, attributes: {} };
}

/**
 * Evaluates flags locally and keeps them current over a stream connection.
 *
 * Evaluation never waits for the network: it reads the snapshot the client holds. Create one client per
 * environment with {@link createFlagwireClient}.
 */
export class FlagwireClient {
  /** Local overrides. A resolution reports `OVERRIDE` when one applies. */
  readonly overrides: FlagOverrides;
  private readonly flagStore: FlagStore;
  private readonly manager: ConnectionManager;
  private readonly storage: SnapshotStorage;
  private readonly contextChanged = new Subject<void>();
  private currentContext: EvaluationContext;

  constructor(options: FlagwireClientOptions) {
    const store = options.store ?? createLocalStore();
    this.storage = createSnapshotStorage(store, options.sdkKey);
    this.currentContext = options.context ?? anonymousContext(store);
    this.overrides = new FlagOverrides(store, `${snapshotStorageKey(options.sdkKey)}:overrides`);
    this.flagStore = new FlagStore(options.initialSnapshot ?? this.storage.load() ?? undefined);
    if (options.initialSnapshot !== undefined) {
      this.storage.save(options.initialSnapshot);
    }
    this.flagStore.changes$.subscribe(() => {
      const snapshot = this.flagStore.snapshot();
      if (snapshot !== null) {
        this.storage.save(snapshot);
      }
    });
    this.manager = new ConnectionManager({
      url: options.streamUrl,
      sdkKey: options.sdkKey,
      store: this.flagStore,
      clientId: this.currentContext.key,
      sdk: SDK_NAME,
      socketFactory: options.socketFactory ?? createBrowserSocketFactory(),
      environment: options.environment ?? createBrowserEnvironment(),
      ...(options.scheduler === undefined ? {} : { scheduler: options.scheduler }),
      ...(options.random === undefined ? {} : { random: options.random }),
      ...(options.timing === undefined ? {} : { timing: options.timing }),
    });
  }

  get status$(): Observable<ConnectionStatus> {
    return this.manager.status$;
  }

  get status(): ConnectionStatus {
    return this.manager.status;
  }

  get errors$(): Observable<StreamError> {
    return this.manager.errors$;
  }

  /** Emits after anything that can change a flag value: a snapshot, a delta, an override or a new context. */
  get changes$(): Observable<void> {
    return merge(
      this.flagStore.changes$.pipe(map(() => undefined)),
      this.overrides.changes$,
      this.contextChanged,
    );
  }

  /** The version of the environment the client holds, or `null` before it has any data. */
  get version(): number | null {
    return this.flagStore.version;
  }

  get context(): EvaluationContext {
    return this.currentContext;
  }

  setContext(context: EvaluationContext): void {
    this.currentContext = context;
    this.contextChanged.next();
  }

  /** The held state, for handing to the browser after server rendering. `null` before any data. */
  snapshot(): FlagSnapshot | null {
    return this.flagStore.snapshot();
  }

  /** Replaces the held state, for example with a snapshot fetched on the server or one handed over by the server. */
  hydrate(snapshot: FlagSnapshot): void {
    this.flagStore.hydrate(snapshot);
  }

  flagKeys(): readonly string[] {
    return this.flagStore.flagKeys();
  }

  /** The declared type of a flag, or `undefined` when the flag is unknown. */
  flagType(key: string): FlagType | undefined {
    return this.flagStore.flag(key)?.type;
  }

  /** Evaluates a flag with its reason. The fallback is returned for unknown flags and for a type mismatch. */
  resolve<T extends JsonValue>(key: string, fallback: T): Resolution<T> {
    return resolveFlag(
      this.flagStore.flag(key),
      this.currentContext,
      this.flagStore.segments,
      fallback,
      this.overrides.get(key),
    );
  }

  /** Evaluates a flag and returns only its value. */
  value<T extends JsonValue>(key: string, fallback: T): T {
    return this.resolve(key, fallback).value;
  }

  /** Opens the stream. Call it in browsers only: servers should use {@link fetchSnapshot} instead. */
  start(): void {
    this.manager.start();
  }

  stop(): void {
    this.manager.stop();
  }
}

/** Creates a client. It does nothing until `start()` is called. */
export function createFlagwireClient(options: FlagwireClientOptions): FlagwireClient {
  return new FlagwireClient(options);
}
