package dev.flagtide.adapter.out.memory;

import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.propagation.LocalPropagationStats;

public record MemoryAdapters(
    MemoryDatabase transactions,
    MemoryFlagRepository flags,
    MemorySegmentRepository segments,
    MemoryProjectRepository projects,
    MemoryApiKeyStore apiKeys,
    MemoryAuditLog auditLog,
    MemoryChangeLog changeLog,
    MemoryChangeFeed changeFeed,
    LocalPropagationStats propagationStats) {

  public static final int DEFAULT_RETENTION = 1000;

  public static MemoryAdapters create(TimeSource timeSource) {
    return create(timeSource, DEFAULT_RETENTION);
  }

  public static MemoryAdapters create(TimeSource timeSource, int changeLogRetention) {
    MemoryDatabase database = new MemoryDatabase();
    MemoryChangeFeed feed = new MemoryChangeFeed();
    return new MemoryAdapters(
        database,
        new MemoryFlagRepository(database),
        new MemorySegmentRepository(database),
        new MemoryProjectRepository(database),
        new MemoryApiKeyStore(database),
        new MemoryAuditLog(database),
        new MemoryChangeLog(database, feed, changeLogRetention),
        feed,
        new LocalPropagationStats(timeSource));
  }
}
