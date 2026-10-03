export { BUCKET_SPACE, bucketOf } from './lib/bucket.js';
export { createFlagwireClient, FlagwireClient } from './lib/client.js';
export type { FlagwireClientOptions } from './lib/client.js';
export {
  backoffDelay,
  ConnectionManager,
  createBrowserEnvironment,
  createBrowserSocketFactory,
  DEFAULT_TIMING,
} from './lib/connection-manager.js';
export type {
  ConnectionManagerOptions,
  ConnectionStatus,
  ConnectionTiming,
  NetworkEnvironment,
  SocketControl,
  SocketFactory,
  SocketHandlers,
  StreamError,
} from './lib/connection-manager.js';
export { InvalidFlagConfigError, evaluate, indexSegments } from './lib/evaluate.js';
export { fetchSnapshot } from './lib/fetch-snapshot.js';
export type { FetchLike } from './lib/fetch-snapshot.js';
export { FlagStore } from './lib/flag-store.js';
export type { ApplyOutcome, FlagSnapshot } from './lib/flag-store.js';
export { murmur3x86_32, murmur3x86_32OfText } from './lib/murmur3.js';
export { FlagOverrides } from './lib/overrides.js';
export { parseServerFrame, parseSnapshotBody } from './lib/protocol.js';
export type * from './lib/protocol.js';
export { flagTypeOf, jsonEquals, resolveFlag } from './lib/resolve.js';
export type { Resolution, ResolutionReason } from './lib/resolve.js';
export { compareSemanticVersions, parseSemanticVersion } from './lib/semver.js';
export type { SemanticVersion } from './lib/semver.js';
export {
  createLocalStore,
  createMemoryStore,
  createSnapshotStorage,
  createWebStore,
  snapshotStorageKey,
} from './lib/storage.js';
export type { KeyValueStore, SnapshotStorage } from './lib/storage.js';
export { encodeUtf8, utf8Length } from './lib/utf8.js';
export type * from './lib/types.js';
