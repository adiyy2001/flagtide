package dev.flagtide.application.sync;

import dev.flagtide.domain.evaluation.FlagConfig;
import dev.flagtide.domain.evaluation.Segment;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public record Snapshot(
    EnvironmentVersion version,
    Optional<ZonedDateTime> committedAt,
    List<FlagConfig> flags,
    List<Segment> segments) {

  public Snapshot {
    flags = List.copyOf(flags);
    segments = List.copyOf(segments);
  }
}
