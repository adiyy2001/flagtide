package dev.flagwire.adapter.out.memory;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.AuditQuery;
import dev.flagwire.domain.audit.AuditEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MemoryAuditLog implements AuditLog {

  private final MemoryDatabase database;
  private final List<AuditEntry> entries = new ArrayList<>();

  public MemoryAuditLog(MemoryDatabase database) {
    this.database = database;
  }

  @Override
  public void append(AuditEntry entry) {
    this.database.write(
        () -> {
          this.entries.add(entry);
          this.database.onRollback(() -> this.entries.remove(this.entries.size() - 1));
          return null;
        });
  }

  @Override
  public List<AuditEntry> find(AuditQuery query) {
    return this.database.read(
        () -> {
          List<AuditEntry> newestFirst = new ArrayList<>(this.entries);
          Collections.reverse(newestFirst);
          return newestFirst.stream()
              .filter(entry -> entry.project().equals(query.project()))
              .filter(
                  entry ->
                      query
                          .environment()
                          .map(env -> entry.environment().equals(Optional.of(env)))
                          .orElse(true))
              .filter(
                  entry -> query.entityKey().map(key -> entry.entityKey().equals(key)).orElse(true))
              .skip(query.offset())
              .limit(query.limit())
              .toList();
        });
  }
}
