import type { Observable } from 'rxjs';
import { Subject } from 'rxjs';
import { indexSegments } from './evaluate.js';
import type { DeltasFrame, SnapshotFrame, StreamChange } from './protocol.js';
import type { FlagConfig, Segment, SegmentIndex } from './types.js';

/** The complete state of an environment at one version. */
export interface FlagSnapshot {
  readonly version: number;
  readonly flags: readonly FlagConfig[];
  readonly segments: readonly Segment[];
}

/**
 * What happened when a frame reached the store.
 *
 * `gap` means the frame does not continue from the held version, so the caller has to ask for a snapshot.
 */
export type ApplyOutcome = 'applied' | 'unchanged' | 'gap';

function continuesFrom(held: number, frame: DeltasFrame): boolean {
  let expected = held + 1;
  for (const entry of frame.entries) {
    if (entry.v !== expected) {
      return false;
    }
    expected += 1;
  }
  return expected - 1 === frame.to;
}

/**
 * Holds the flags and segments of one environment and the version they are at.
 *
 * The store is the single place where snapshots and deltas are applied. It never opens a connection.
 */
export class FlagStore {
  private readonly flagsByKey = new Map<string, FlagConfig>();
  private readonly segmentsByKey = new Map<string, Segment>();
  private currentVersion: number | null = null;
  private segmentIndex: SegmentIndex | null = null;
  private readonly changed = new Subject<number>();

  constructor(initial?: FlagSnapshot) {
    if (initial !== undefined) {
      this.replace(initial);
    }
  }

  /** Emits the new version after every snapshot or delta that changed the held state. */
  get changes$(): Observable<number> {
    return this.changed.asObservable();
  }

  /** The version held, or `null` while the store has never received data. */
  get version(): number | null {
    return this.currentVersion;
  }

  get hasData(): boolean {
    return this.currentVersion !== null;
  }

  get segments(): SegmentIndex {
    this.segmentIndex ??= indexSegments([...this.segmentsByKey.values()]);
    return this.segmentIndex;
  }

  flag(key: string): FlagConfig | undefined {
    return this.flagsByKey.get(key);
  }

  flagKeys(): readonly string[] {
    return [...this.flagsByKey.keys()];
  }

  snapshot(): FlagSnapshot | null {
    if (this.currentVersion === null) {
      return null;
    }
    return {
      version: this.currentVersion,
      flags: [...this.flagsByKey.values()],
      segments: [...this.segmentsByKey.values()],
    };
  }

  /** Replaces everything with the state of a stored or server rendered snapshot. */
  hydrate(snapshot: FlagSnapshot): void {
    this.replace(snapshot);
    this.changed.next(snapshot.version);
  }

  applySnapshot(frame: SnapshotFrame): ApplyOutcome {
    this.replace({ version: frame.v, flags: frame.flags, segments: frame.segments });
    this.changed.next(frame.v);
    return 'applied';
  }

  applyDeltas(frame: DeltasFrame): ApplyOutcome {
    const held = this.currentVersion;
    if (held === null || frame.from !== held || !continuesFrom(held, frame)) {
      return 'gap';
    }
    if (frame.entries.length === 0) {
      return 'unchanged';
    }
    frame.entries.forEach((entry) => entry.changes.forEach((change) => this.applyChange(change)));
    return this.finish(frame.to);
  }

  private finish(version: number): ApplyOutcome {
    this.currentVersion = version;
    this.segmentIndex = null;
    this.changed.next(version);
    return 'applied';
  }

  private replace(snapshot: FlagSnapshot): void {
    this.flagsByKey.clear();
    this.segmentsByKey.clear();
    snapshot.flags.forEach((flag) => this.flagsByKey.set(flag.key, flag));
    snapshot.segments.forEach((segment) => this.segmentsByKey.set(segment.key, segment));
    this.currentVersion = snapshot.version;
    this.segmentIndex = null;
  }

  private applyChange(change: StreamChange): void {
    if (change.op === 'remove') {
      (change.kind === 'flag' ? this.flagsByKey : this.segmentsByKey).delete(change.key);
      return;
    }
    if (change.kind === 'flag') {
      this.flagsByKey.set(change.key, change.config);
    } else {
      this.segmentsByKey.set(change.key, change.config);
    }
  }
}
