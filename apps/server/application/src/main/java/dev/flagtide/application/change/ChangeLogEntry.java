package dev.flagtide.application.change;

import dev.flagtide.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.List;

public record ChangeLogEntry(
    EnvironmentVersion version, ZonedDateTime committedAt, List<Change> changes) {

  public ChangeLogEntry {
    changes = List.copyOf(changes);
  }
}
