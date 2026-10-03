package dev.flagwire.application.change;

import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.List;

public record ChangeLogEntry(
    EnvironmentVersion version, ZonedDateTime committedAt, List<Change> changes) {

  public ChangeLogEntry {
    changes = List.copyOf(changes);
  }
}
