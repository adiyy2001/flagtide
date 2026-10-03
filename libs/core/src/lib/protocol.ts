import type { FlagConfig, Segment } from './types.js';

/** A change to one flag or segment inside a `deltas` entry. */
export type StreamChange =
  | { readonly op: 'upsert'; readonly kind: 'flag'; readonly key: string; readonly config: FlagConfig }
  | { readonly op: 'upsert'; readonly kind: 'segment'; readonly key: string; readonly config: Segment }
  | { readonly op: 'remove'; readonly kind: 'flag' | 'segment'; readonly key: string };

/** One committed change set of an environment. */
export interface StreamEntry {
  readonly v: number;
  readonly committedAtMs: number;
  readonly changes: readonly StreamChange[];
}

/** Full state of an environment at a version. A snapshot replaces everything the client holds. */
export interface SnapshotFrame {
  readonly t: 'snapshot';
  readonly v: number;
  readonly committedAtMs?: number;
  readonly flags: readonly FlagConfig[];
  readonly segments: readonly Segment[];
}

/** Ordered entries that move a client from version `from` to version `to`. */
export interface DeltasFrame {
  readonly t: 'deltas';
  readonly from: number;
  readonly to: number;
  readonly entries: readonly StreamEntry[];
}

export interface HeartbeatFrame {
  readonly t: 'hb';
  readonly ts: number;
  readonly v?: number;
}

export interface ErrorFrame {
  readonly t: 'error';
  readonly code: number;
  readonly message: string;
}

export type ServerFrame = SnapshotFrame | DeltasFrame | HeartbeatFrame | ErrorFrame;

export interface HelloFrame {
  readonly t: 'hello';
  readonly sdkKey: string;
  readonly version?: number;
  readonly clientId?: string;
  readonly sdk?: string;
}

export interface AckFrame {
  readonly t: 'ack';
  readonly v: number;
}

export type ClientFrame = HelloFrame | AckFrame;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isVersion(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function isChange(value: unknown): boolean {
  if (!isRecord(value) || typeof value['key'] !== 'string') {
    return false;
  }
  const kind = value['kind'];
  if (kind !== 'flag' && kind !== 'segment') {
    return false;
  }
  if (value['op'] === 'remove') {
    return true;
  }
  return value['op'] === 'upsert' && isRecord(value['config']);
}

function isEntry(value: unknown): value is StreamEntry {
  return (
    isRecord(value) &&
    isVersion(value['v']) &&
    Array.isArray(value['changes']) &&
    value['changes'].every(isChange)
  );
}

function isNamedRecordList(value: unknown): boolean {
  return Array.isArray(value) && value.every((item) => isRecord(item) && typeof item['key'] === 'string');
}

function isSnapshotFrame(value: Record<string, unknown>): boolean {
  return isVersion(value['v']) && isNamedRecordList(value['flags']) && isNamedRecordList(value['segments']);
}

function isDeltasFrame(value: Record<string, unknown>): boolean {
  return (
    isVersion(value['from']) &&
    isVersion(value['to']) &&
    Array.isArray(value['entries']) &&
    value['entries'].every(isEntry)
  );
}

function isErrorFrame(value: Record<string, unknown>): boolean {
  return typeof value['code'] === 'number' && typeof value['message'] === 'string';
}

function isShapeOf(type: unknown, value: Record<string, unknown>): boolean {
  switch (type) {
    case 'snapshot':
      return isSnapshotFrame(value);
    case 'deltas':
      return isDeltasFrame(value);
    case 'hb':
      return typeof value['ts'] === 'number';
    case 'error':
      return isErrorFrame(value);
    default:
      return false;
  }
}

/**
 * Parses one text frame sent by the server.
 *
 * @returns the frame, or `null` when the text is not valid JSON or does not have the shape of a known frame
 */
export function parseServerFrame(text: string): ServerFrame | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (!isRecord(parsed) || !isShapeOf(parsed['t'], parsed)) {
    return null;
  }
  return parsed as unknown as ServerFrame;
}

/** Parses the body of `GET /sdk/v1/snapshot`, which has the fields of a `snapshot` frame without `t`. */
export function parseSnapshotBody(text: string): SnapshotFrame | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return null;
  }
  if (!isRecord(parsed) || !isSnapshotFrame(parsed)) {
    return null;
  }
  return { ...(parsed as unknown as SnapshotFrame), t: 'snapshot' };
}
