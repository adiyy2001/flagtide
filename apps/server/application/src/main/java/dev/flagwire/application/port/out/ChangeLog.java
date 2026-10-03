package dev.flagwire.application.port.out;

import dev.flagwire.application.change.Change;
import dev.flagwire.application.change.ChangeLogEntry;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
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
