package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemoryChangeLog implements ChangeLog {

  private static final class EnvironmentLog {

    private long version;
    private final Deque<ChangeLogEntry> entries = new ArrayDeque<>();
  }

  private final MemoryDatabase database;
  private final MemoryChangeFeed feed;
  private final int retention;
  private final Map<EnvironmentRef, EnvironmentLog> logs = new HashMap<>();

  public MemoryChangeLog(MemoryDatabase database, MemoryChangeFeed feed, int retention) {
    if (retention < 1) {
      throw new IllegalArgumentException("the change log keeps at least one entry");
    }
    this.database = database;
    this.feed = feed;
    this.retention = retention;
  }

  @Override
  public EnvironmentVersion append(
      EnvironmentRef environment, ZonedDateTime committedAt, List<Change> changes) {
    return this.database.write(
        () -> {
          EnvironmentLog log = this.logs.computeIfAbsent(environment, key -> new EnvironmentLog());
          long previousVersion = log.version;
          log.version++;
          ChangeLogEntry entry =
              new ChangeLogEntry(EnvironmentVersion.of(log.version), committedAt, changes);
          log.entries.addLast(entry);
          List<ChangeLogEntry> trimmed = new ArrayList<>();
          while (log.entries.size() > this.retention) {
            trimmed.add(log.entries.removeFirst());
          }
          this.database.onRollback(
              () -> {
                log.version = previousVersion;
                log.entries.removeLast();
                trimmed.reversed().forEach(log.entries::addFirst);
              });
          this.database.afterCommit(
              () -> this.feed.publish(new ChangeNotification(environment, entry.version())));
          return entry.version();
        });
  }

  @Override
  public EnvironmentVersion currentVersion(EnvironmentRef environment) {
    return this.database.read(
        () ->
            Optional.ofNullable(this.logs.get(environment))
                .map(log -> EnvironmentVersion.of(log.version))
                .orElse(EnvironmentVersion.ZERO));
  }

  @Override
  public List<ChangeLogEntry> entriesAfter(EnvironmentRef environment, EnvironmentVersion version) {
    return this.database.read(
        () ->
            Optional.ofNullable(this.logs.get(environment)).stream()
                .flatMap(log -> log.entries.stream())
                .filter(entry -> entry.version().isAfter(version))
                .toList());
  }

  @Override
  public Optional<EnvironmentVersion> oldestRetained(EnvironmentRef environment) {
    return this.database.read(
        () ->
            Optional.ofNullable(this.logs.get(environment))
                .map(log -> log.entries.peekFirst())
                .map(ChangeLogEntry::version));
  }
}
