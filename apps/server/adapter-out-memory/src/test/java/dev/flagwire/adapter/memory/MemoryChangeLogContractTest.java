package dev.flagwire.adapter.memory;

import dev.flagwire.application.contract.ChangeLogContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryChangeLogContractTest extends ChangeLogContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
