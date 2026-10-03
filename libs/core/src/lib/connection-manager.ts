import {
  BehaviorSubject,
  Observable,
  Subject,
  Subscription,
  asyncScheduler,
  distinctUntilChanged,
} from 'rxjs';
import type { SchedulerLike } from 'rxjs';
import type { FlagStore } from './flag-store.js';
import { parseServerFrame } from './protocol.js';
import type { AckFrame, HelloFrame, ServerFrame } from './protocol.js';

/**
 * What the SDK can say about the flags it serves.
 *
 * - `connecting`: the first attempt is in progress and nothing is confirmed yet
 * - `live`: the stream is open and the data is confirmed current
 * - `stale`: flags are served but cannot be confirmed current
 * - `offline`: the browser has no network or the server has been unreachable for too long
 */
export type ConnectionStatus = 'connecting' | 'live' | 'offline' | 'stale';

/** Callbacks a socket implementation reports to the connection manager. */
export interface SocketHandlers {
  open(): void;
  message(text: string): void;
  close(code: number): void;
}

/** The part of a socket the connection manager drives. */
export interface SocketControl {
  send(text: string): void;
  close(code?: number): void;
}

/** Opens a socket to `url` and reports its events to `handlers`. */
export type SocketFactory = (url: string, handlers: SocketHandlers) => SocketControl;

/** The browser facts the connection manager reacts to. Inject a fake in tests, or use `createBrowserEnvironment`. */
export interface NetworkEnvironment {
  isOnline(): boolean;
  readonly online$: Observable<boolean>;
  readonly visible$: Observable<boolean>;
}

/** An error reported by the server or found in what it sent. `code` follows the close codes of the stream protocol. */
export interface StreamError {
  readonly code: number;
  readonly message: string;
}

/** Backoff, heartbeat and offline limits. The defaults are the constants of the stream protocol. */
export interface ConnectionTiming {
  readonly backoffBaseMs: number;
  readonly backoffCapMs: number;
  readonly staleAfterMs: number;
  readonly connectTimeoutMs: number;
  readonly offlineAfterMs: number;
}

export const DEFAULT_TIMING: ConnectionTiming = {
  backoffBaseMs: 500,
  backoffCapMs: 30_000,
  staleAfterMs: 40_000,
  connectTimeoutMs: 10_000,
  offlineAfterMs: 60_000,
};

export const CLOSE_UNAUTHORIZED = 4401;
export const CLOSE_BAD_FRAME = 4400;
const CLOSE_NORMAL = 1000;
const CLOSE_ABNORMAL = 1006;

export interface ConnectionManagerOptions {
  readonly url: string;
  readonly sdkKey: string;
  readonly store: FlagStore;
  readonly clientId?: string;
  readonly sdk?: string;
  readonly socketFactory: SocketFactory;
  readonly environment?: NetworkEnvironment;
  readonly scheduler?: SchedulerLike;
  readonly random?: () => number;
  readonly timing?: Partial<ConnectionTiming>;
}

const ALWAYS_ONLINE: NetworkEnvironment = {
  isOnline: () => true,
  online$: new Observable<boolean>(),
  visible$: new Observable<boolean>(),
};

/**
 * The delay before reconnect attempt number `attempt` (zero based): `random(0, min(cap, base * 2^attempt))`.
 * Full jitter keeps thousands of clients that lost the same server from reconnecting in step.
 */
export function backoffDelay(attempt: number, random: () => number, baseMs: number, capMs: number): number {
  return Math.floor(random() * Math.min(capMs, baseMs * 2 ** attempt));
}

type BrowserSocket = {
  onopen: (() => void) | null;
  onmessage: ((event: { data: unknown }) => void) | null;
  onclose: ((event: { code: number }) => void) | null;
  onerror: (() => void) | null;
  send(data: string): void;
  close(code?: number): void;
};

type SocketConstructor = new (url: string) => BrowserSocket;

/** A socket factory on top of the `WebSocket` constructor of the runtime (browsers, Node 22 and later). */
export function createBrowserSocketFactory(
  constructor: SocketConstructor | undefined = (globalThis as { WebSocket?: SocketConstructor }).WebSocket,
): SocketFactory {
  return (url, handlers) => {
    if (constructor === undefined) {
      throw new Error('WebSocket is not available in this runtime');
    }
    const socket = new constructor(url);
    socket.onopen = () => handlers.open();
    socket.onmessage = (event) => {
      if (typeof event.data === 'string') {
        handlers.message(event.data);
      }
    };
    socket.onclose = (event) => handlers.close(event.code);
    socket.onerror = () => undefined;
    return { send: (text) => socket.send(text), close: (code) => socket.close(code) };
  };
}

/** Online and visibility state from `window` and `document`. Outside a browser it reports online and never changes. */
export function createBrowserEnvironment(): NetworkEnvironment {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return ALWAYS_ONLINE;
  }
  return {
    isOnline: () => navigator.onLine,
    online$: new Observable<boolean>((subscriber) => {
      const online = (): void => subscriber.next(true);
      const offline = (): void => subscriber.next(false);
      window.addEventListener('online', online);
      window.addEventListener('offline', offline);
      return () => {
        window.removeEventListener('online', online);
        window.removeEventListener('offline', offline);
      };
    }),
    visible$: new Observable<boolean>((subscriber) => {
      const changed = (): void => subscriber.next(document.visibilityState === 'visible');
      document.addEventListener('visibilitychange', changed);
      return () => document.removeEventListener('visibilitychange', changed);
    }),
  };
}

/**
 * Keeps a stream connection to the server and feeds a {@link FlagStore}.
 *
 * It reconnects with exponential backoff and full jitter, resumes from the version the store holds, treats a
 * silent connection as dead by comparing timestamps (background tabs throttle timers), and acknowledges
 * every applied frame. All time comes from the injected scheduler and all randomness from the injected
 * random source, so the behavior can be tested with a virtual clock.
 */
export class ConnectionManager {
  private readonly options: ConnectionManagerOptions;
  private readonly timing: ConnectionTiming;
  private readonly scheduler: SchedulerLike;
  private readonly random: () => number;
  private readonly environment: NetworkEnvironment;
  private readonly statusSubject: BehaviorSubject<ConnectionStatus>;
  private readonly errorSubject = new Subject<StreamError>();
  private environmentSubscription = new Subscription();
  private socket: SocketControl | null = null;
  private generation = 0;
  private attempt = 0;
  private failingSince: number | null = null;
  private lastFrameAt = 0;
  private silenceLimitMs = 0;
  private forceSnapshot = false;
  private running = false;
  private halted = false;
  private retryTimer: Subscription | null = null;
  private watchdogTimer: Subscription | null = null;

  constructor(options: ConnectionManagerOptions) {
    this.options = options;
    this.timing = { ...DEFAULT_TIMING, ...options.timing };
    this.scheduler = options.scheduler ?? asyncScheduler;
    this.random = options.random ?? Math.random;
    this.environment = options.environment ?? ALWAYS_ONLINE;
    this.statusSubject = new BehaviorSubject<ConnectionStatus>(this.initialStatus());
  }

  get status$(): Observable<ConnectionStatus> {
    return this.statusSubject.pipe(distinctUntilChanged());
  }

  get status(): ConnectionStatus {
    return this.statusSubject.value;
  }

  get errors$(): Observable<StreamError> {
    return this.errorSubject.asObservable();
  }

  start(): void {
    if (this.running) {
      return;
    }
    this.running = true;
    this.halted = false;
    this.environmentSubscription.add(
      this.environment.online$.subscribe((online) => this.networkChanged(online)),
    );
    this.environmentSubscription.add(
      this.environment.visible$.subscribe((visible) => {
        if (visible) {
          this.checkSilence();
        }
      }),
    );
    if (this.environment.isOnline()) {
      this.connect();
    } else {
      this.setStatus('offline');
    }
  }

  stop(): void {
    this.running = false;
    this.environmentSubscription.unsubscribe();
    this.environmentSubscription = new Subscription();
    this.dropSocket();
    this.clearRetry();
  }

  private initialStatus(): ConnectionStatus {
    return this.options.store.hasData ? 'stale' : 'connecting';
  }

  private setStatus(status: ConnectionStatus): void {
    this.statusSubject.next(status);
  }

  private now(): number {
    return this.scheduler.now();
  }

  private connect(): void {
    this.clearRetry();
    const generation = ++this.generation;
    this.lastFrameAt = this.now();
    this.armWatchdog(this.timing.connectTimeoutMs);
    const run = (action: () => void): void => {
      if (generation === this.generation) {
        action();
      }
    };
    try {
      this.socket = this.options.socketFactory(this.options.url, {
        open: () => run(() => this.sendHello()),
        message: (text) => run(() => this.receive(text)),
        close: (code) => run(() => this.connectionLost(code)),
      });
    } catch {
      this.socket = null;
      this.connectionLost(CLOSE_ABNORMAL);
    }
  }

  private sendHello(): void {
    const version = this.forceSnapshot ? null : this.options.store.version;
    this.forceSnapshot = false;
    const hello: HelloFrame = {
      t: 'hello',
      sdkKey: this.options.sdkKey,
      ...(version === null ? {} : { version }),
      ...(this.options.clientId === undefined ? {} : { clientId: this.options.clientId }),
      ...(this.options.sdk === undefined ? {} : { sdk: this.options.sdk }),
    };
    this.socket?.send(JSON.stringify(hello));
  }

  private receive(text: string): void {
    const frame = parseServerFrame(text);
    if (frame === null) {
      this.errorSubject.next({
        code: CLOSE_BAD_FRAME,
        message: 'The server sent a frame the SDK does not understand',
      });
      this.dropSocket();
      this.scheduleReconnect();
      return;
    }
    this.lastFrameAt = this.now();
    this.armWatchdog(this.timing.staleAfterMs);
    this.handle(frame);
  }

  private handle(frame: ServerFrame): void {
    switch (frame.t) {
      case 'snapshot':
        this.options.store.applySnapshot(frame);
        this.confirmed();
        this.acknowledge();
        return;
      case 'deltas':
        this.handleDeltas(frame);
        return;
      case 'hb':
        this.confirmed();
        return;
      case 'error':
        this.errorSubject.next({ code: frame.code, message: frame.message });
        return;
    }
  }

  private handleDeltas(frame: Extract<ServerFrame, { t: 'deltas' }>): void {
    const outcome = this.options.store.applyDeltas(frame);
    if (outcome === 'gap') {
      this.forceSnapshot = true;
      this.dropSocket();
      this.reconnectNow();
      return;
    }
    this.confirmed();
    if (outcome === 'applied') {
      this.acknowledge();
    }
  }

  private confirmed(): void {
    this.attempt = 0;
    this.failingSince = null;
    this.setStatus('live');
  }

  private acknowledge(): void {
    const version = this.options.store.version;
    if (version !== null) {
      const ack: AckFrame = { t: 'ack', v: version };
      this.socket?.send(JSON.stringify(ack));
    }
  }

  private connectionLost(code: number): void {
    this.socket = null;
    this.clearWatchdog();
    if (!this.running) {
      return;
    }
    if (code === CLOSE_UNAUTHORIZED) {
      this.halted = true;
      this.errorSubject.next({ code, message: 'The SDK key was rejected by the server' });
      this.setStatus('offline');
      return;
    }
    this.scheduleReconnect();
  }

  private scheduleReconnect(): void {
    if (!this.running || this.halted) {
      return;
    }
    this.failingSince ??= this.now();
    if (!this.environment.isOnline()) {
      this.setStatus('offline');
      return;
    }
    const delay = backoffDelay(
      this.attempt,
      this.random,
      this.timing.backoffBaseMs,
      this.timing.backoffCapMs,
    );
    this.attempt += 1;
    this.setStatus(this.statusWhileRetrying());
    this.clearRetry();
    this.retryTimer = this.scheduler.schedule(() => this.connect(), delay);
  }

  private reconnectNow(): void {
    this.setStatus(this.statusWhileRetrying());
    this.clearRetry();
    this.retryTimer = this.scheduler.schedule(() => this.connect(), 0);
  }

  private statusWhileRetrying(): ConnectionStatus {
    const failingFor = this.failingSince === null ? 0 : this.now() - this.failingSince;
    if (failingFor >= this.timing.offlineAfterMs) {
      return 'offline';
    }
    return this.options.store.hasData ? 'stale' : 'connecting';
  }

  private networkChanged(online: boolean): void {
    if (!this.running || this.halted) {
      return;
    }
    this.clearRetry();
    this.dropSocket();
    if (!online) {
      this.setStatus('offline');
      return;
    }
    this.attempt = 0;
    this.failingSince = null;
    this.setStatus(this.initialStatus());
    this.connect();
  }

  private armWatchdog(limitMs: number): void {
    this.silenceLimitMs = limitMs;
    this.clearWatchdog();
    this.watchdogTimer = this.scheduler.schedule(() => this.checkSilence(), limitMs);
  }

  private checkSilence(): void {
    if (this.socket === null || !this.running) {
      return;
    }
    const silentFor = this.now() - this.lastFrameAt;
    if (silentFor >= this.silenceLimitMs) {
      this.dropSocket();
      this.scheduleReconnect();
    } else {
      this.clearWatchdog();
      this.watchdogTimer = this.scheduler.schedule(
        () => this.checkSilence(),
        this.silenceLimitMs - silentFor,
      );
    }
  }

  private dropSocket(): void {
    this.generation += 1;
    this.clearWatchdog();
    const socket = this.socket;
    this.socket = null;
    try {
      socket?.close(CLOSE_NORMAL);
    } catch {
      return;
    }
  }

  private clearRetry(): void {
    this.retryTimer?.unsubscribe();
    this.retryTimer = null;
  }

  private clearWatchdog(): void {
    this.watchdogTimer?.unsubscribe();
    this.watchdogTimer = null;
  }
}
