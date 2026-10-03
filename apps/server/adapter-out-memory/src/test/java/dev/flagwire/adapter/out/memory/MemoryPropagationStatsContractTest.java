package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.contract.PropagationStatsContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryPropagationStatsContractTest extends PropagationStatsContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
