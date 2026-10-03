package dev.flagwire.adapter.memory;

import dev.flagwire.application.contract.ApiKeyStoreContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryApiKeyStoreContractTest extends ApiKeyStoreContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
