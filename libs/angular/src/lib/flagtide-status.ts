import { Injectable, inject } from '@angular/core';
import type { Signal } from '@angular/core';
import type { ConnectionStatus } from '@flagtide/core';
import { Flagtide } from './flagtide';

/**
 * The connection status as a signal.
 *
 * - `connecting`: the first attempt is in progress and nothing is confirmed yet
 * - `live`: the stream is open and the data is confirmed current
 * - `stale`: flags are served but cannot be confirmed current, for example from storage or after a dropped connection
 * - `offline`: the browser has no network or the server has been unreachable for too long
 */
@Injectable()
export class FlagtideStatus {
  readonly status: Signal<ConnectionStatus> = inject(Flagtide).status;
}
