package dev.flagtide.application.sync;

import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.util.List;

public sealed interface SyncResult {

  record Deltas(EnvironmentVersion from, EnvironmentVersion to, List<ChangeLogEntry> entries)
      implements SyncResult {
    public Deltas {
      entries = List.copyOf(entries);
    }
  }

  record FullSnapshot(Snapshot snapshot) implements SyncResult {}
}
