import { InjectionToken } from '@angular/core';
import type { ConnectionTiming, EvaluationContext, KeyValueStore, SocketFactory } from '@flagwire/core';

/** Settings for {@link provideFlagwire}. */
export interface FlagwireConfig {
  /** The read-only SDK key of one environment. */
  readonly sdkKey: string;
  /** The stream endpoint the browser connects to, for example `ws://localhost:18081/sdk/v1/stream`. */
  readonly streamUrl: string;
  /**
   * The HTTP address the server side render fetches the snapshot from, without a trailing slash. Needed when
   * the server reaches the API under another address than the browser does, for example inside a container
   * network. Defaults to the stream URL with the scheme changed to `http` and the path removed.
   */
  readonly snapshotUrl?: string;
  /**
   * Who flags are evaluated for. A function runs in an injection context, so it can read a cookie or a request.
   * With server side rendering, use a context key that is the same on the server and in the browser, otherwise
   * a percentage rollout can render differently on both sides. Without a context the browser uses an anonymous
   * id kept in storage.
   */
  readonly context?: EvaluationContext | (() => EvaluationContext);
  /** Where the last snapshot and the overrides are kept in the browser. Defaults to `localStorage`. */
  readonly storage?: KeyValueStore;
  /** Backoff and heartbeat limits. The defaults are the constants of the stream protocol. */
  readonly timing?: Partial<ConnectionTiming>;
  /** Replaces how sockets are opened. Meant for tests and for runtimes without a global `WebSocket`. */
  readonly socketFactory?: SocketFactory;
}

export const FLAGWIRE_CONFIG = new InjectionToken<FlagwireConfig>('FLAGWIRE_CONFIG');

const STREAM_PATH = /\/sdk\/v1\/stream\/?$/u;

export function deriveSnapshotUrl(streamUrl: string): string {
  return streamUrl.replace(/^ws/u, 'http').replace(STREAM_PATH, '');
}
