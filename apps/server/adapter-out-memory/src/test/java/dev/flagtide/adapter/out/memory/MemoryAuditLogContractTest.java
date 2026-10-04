package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.contract.AuditLogContract;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.testing.TestAdapters;

class MemoryAuditLogContractTest extends AuditLogContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
