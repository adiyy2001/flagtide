import { makeEnvironmentProviders, provideAppInitializer, inject } from '@angular/core';
import type { EnvironmentProviders } from '@angular/core';
import { FLAGWIRE_CONFIG } from './config';
import type { FlagwireConfig } from './config';
import { Flagwire } from './flagwire';
import { FlagwireStatus } from './flagwire-status';

/**
 * Registers the SDK. Add it to `ApplicationConfig.providers`.
 *
 * In the browser it opens the stream and starts from the last stored snapshot, or from the snapshot the server
 * rendered. On the server it opens no socket, touches no browser storage, fetches the snapshot over HTTP before
 * the first render and hands it to the browser through `TransferState`.
 */
export function provideFlagwire(config: FlagwireConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: FLAGWIRE_CONFIG, useValue: config },
    Flagwire,
    FlagwireStatus,
    provideAppInitializer(() => inject(Flagwire).initialize()),
  ]);
}
