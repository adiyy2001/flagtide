package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.contract.ChangeFeedContract;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.testing.TestAdapters;

class MemoryChangeFeedContractTest extends ChangeFeedContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
