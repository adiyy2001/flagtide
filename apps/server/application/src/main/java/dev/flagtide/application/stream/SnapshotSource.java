package dev.flagtide.application.stream;

import dev.flagtide.application.sync.Snapshot;
import dev.flagtide.domain.value.EnvironmentRef;

@FunctionalInterface
public interface SnapshotSource {

  Snapshot forEnvironment(EnvironmentRef environment);
}
