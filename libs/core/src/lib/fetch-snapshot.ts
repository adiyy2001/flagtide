import type { FlagSnapshot } from './flag-store.js';
import { parseSnapshotBody } from './protocol.js';

/** The part of `fetch` the SDK uses. Servers pass their own, tests pass a fake. */
export type FetchLike = (
  url: string,
  init: { readonly headers: Readonly<Record<string, string>> },
) => Promise<{ readonly ok: boolean; readonly status: number; text(): Promise<string> }>;

/**
 * Loads the current snapshot over HTTP. Server rendering uses this instead of a socket, and the result is
 * handed to the browser so the first client render matches the server render.
 *
 * @param baseUrl the server address without a trailing slash, for example `http://server:8080`
 * @throws when the server answers with an error status or with a body that is not a snapshot
 */
export async function fetchSnapshot(
  baseUrl: string,
  sdkKey: string,
  fetchImplementation: FetchLike = (url, init) => fetch(url, init),
): Promise<FlagSnapshot> {
  const response = await fetchImplementation(`${baseUrl}/sdk/v1/snapshot`, {
    headers: { Authorization: `Bearer ${sdkKey}`, Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Snapshot request failed with status ${response.status}`);
  }
  const frame = parseSnapshotBody(await response.text());
  if (frame === null) {
    throw new Error('The server did not answer with a snapshot');
  }
  return { version: frame.v, flags: frame.flags, segments: frame.segments };
}
