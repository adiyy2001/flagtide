import type { EnvironmentProviders } from '@angular/core';
import { createMemoryStore } from '@flagtide/core';
import type { FlagtideConfig } from '../src/lib/config';
import { provideFlagtide } from '../src/lib/provide-flagtide';
import { FakeSockets } from '../../core/test-support/fake-socket';

export { FakeSockets };

export function testFlagtide(
  sockets: FakeSockets,
  overrides: Partial<FlagtideConfig> = {},
): EnvironmentProviders {
  return provideFlagtide({
    sdkKey: 'fws_test',
    streamUrl: 'ws://test/sdk/v1/stream',
    context: { key: 'user-1', attributes: {} },
    storage: createMemoryStore(),
    socketFactory: sockets.factory,
    ...overrides,
  });
}
