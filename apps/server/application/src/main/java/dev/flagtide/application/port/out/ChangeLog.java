package dev.flagtide.application.port.out;

import dev.flagtide.application.change.Change;
import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ChangeLog {

  EnvironmentVersion append(
      EnvironmentRef environment, ZonedDateTime committedAt, List<Change> changes);

  EnvironmentVersion currentVersion(EnvironmentRef environment);

  List<ChangeLogEntry> entriesAfter(EnvironmentRef environment, EnvironmentVersion version);

  Optional<EnvironmentVersion> oldestRetained(EnvironmentRef environment);
}
