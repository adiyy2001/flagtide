package dev.flagwire.adapter.memory;

import dev.flagwire.application.contract.ChangeFeedContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryChangeFeedContractTest extends ChangeFeedContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
