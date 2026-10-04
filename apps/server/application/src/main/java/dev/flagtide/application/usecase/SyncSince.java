package dev.flagtide.application.usecase;

import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.sync.SyncResult;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.util.List;
import java.util.Optional;

public final class SyncSince {

  private final TransactionRunner transactions;
  private final ChangeLog changeLog;
  private final BuildSnapshot buildSnapshot;

  public SyncSince(
      TransactionRunner transactions, ChangeLog changeLog, BuildSnapshot buildSnapshot) {
    this.transactions = transactions;
    this.changeLog = changeLog;
    this.buildSnapshot = buildSnapshot;
  }

  public SyncResult execute(Principal principal, Optional<EnvironmentVersion> since) {
    EnvironmentRef scope = principal.environmentRef();
    return this.transactions.inReadOnlyTransaction(() -> this.plan(scope, since));
  }

  private SyncResult plan(EnvironmentRef scope, Optional<EnvironmentVersion> since) {
    EnvironmentVersion current = this.changeLog.currentVersion(scope);
    if (since.isEmpty()
        || since.get().isAfter(current)
        || !this.covers(scope, since.get(), current)) {
      return new SyncResult.FullSnapshot(this.buildSnapshot.build(scope));
    }
    List<ChangeLogEntry> entries = this.changeLog.entriesAfter(scope, since.get());
    return new SyncResult.Deltas(since.get(), current, entries);
  }

  private boolean covers(
      EnvironmentRef scope, EnvironmentVersion since, EnvironmentVersion current) {
    if (since.equals(current)) {
      return true;
    }
    return this.changeLog
        .oldestRetained(scope)
        .map(oldest -> oldest.value() <= since.value() + 1)
        .orElse(false);
  }
}
