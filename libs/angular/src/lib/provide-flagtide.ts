import { makeEnvironmentProviders, provideAppInitializer, inject } from '@angular/core';
import type { EnvironmentProviders } from '@angular/core';
import { FLAGTIDE_CONFIG } from './config';
import type { FlagtideConfig } from './config';
import { Flagtide } from './flagtide';
import { FlagtideStatus } from './flagtide-status';

/**
 * Registers the SDK. Add it to `ApplicationConfig.providers`.
 *
 * In the browser it opens the stream and starts from the last stored snapshot, or from the snapshot the server
 * rendered. On the server it opens no socket, touches no browser storage, fetches the snapshot over HTTP before
 * the first render and hands it to the browser through `TransferState`.
 */
export function provideFlagtide(config: FlagtideConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: FLAGTIDE_CONFIG, useValue: config },
    Flagtide,
    FlagtideStatus,
    provideAppInitializer(() => inject(Flagtide).initialize()),
  ]);
}
