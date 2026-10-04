package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.contract.FlagRepositoryContract;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.testing.TestAdapters;

class MemoryFlagRepositoryContractTest extends FlagRepositoryContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
