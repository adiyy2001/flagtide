package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.contract.SegmentRepositoryContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemorySegmentRepositoryContractTest extends SegmentRepositoryContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
