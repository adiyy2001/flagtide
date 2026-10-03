import type { EnvironmentProviders } from '@angular/core';
import { createMemoryStore } from '@flagwire/core';
import type { FlagwireConfig } from '../src/lib/config';
import { provideFlagwire } from '../src/lib/provide-flagwire';
import { FakeSockets } from '../../core/test-support/fake-socket';

export { FakeSockets };

export function testFlagwire(
  sockets: FakeSockets,
  overrides: Partial<FlagwireConfig> = {},
): EnvironmentProviders {
  return provideFlagwire({
    sdkKey: 'fws_test',
    streamUrl: 'ws://test/sdk/v1/stream',
    context: { key: 'user-1', attributes: {} },
    storage: createMemoryStore(),
    socketFactory: sockets.factory,
    ...overrides,
  });
}
