package dev.flagwire.application.stream;

import dev.flagwire.application.sync.Snapshot;
import dev.flagwire.domain.value.EnvironmentRef;

@FunctionalInterface
public interface SnapshotSource {

  Snapshot forEnvironment(EnvironmentRef environment);
}
