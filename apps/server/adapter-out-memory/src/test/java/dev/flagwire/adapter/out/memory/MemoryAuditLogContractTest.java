package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.contract.AuditLogContract;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.testing.TestAdapters;

class MemoryAuditLogContractTest extends AuditLogContract {

  @Override
  protected TestAdapters createAdapters(TimeSource timeSource, int changeLogRetention) {
    return new MemoryTestAdapters(timeSource, changeLogRetention);
  }
}
