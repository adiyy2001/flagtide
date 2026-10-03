package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.contract.FlagRepositoryContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryFlagRepositoryContractTest extends FlagRepositoryContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
