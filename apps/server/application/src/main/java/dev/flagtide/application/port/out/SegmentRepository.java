package dev.flagtide.application.port.out;

import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Optional;

public interface SegmentRepository {

  Optional<Segment> find(EnvironmentRef environment, SegmentKey key);

  List<Segment> findAll(EnvironmentRef environment);

  void save(EnvironmentRef environment, Segment segment, Revision expected);

  void delete(EnvironmentRef environment, SegmentKey key, Revision expected);
}
