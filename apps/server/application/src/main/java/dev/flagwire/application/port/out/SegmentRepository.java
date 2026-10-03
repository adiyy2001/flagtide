package dev.flagwire.application.port.out;

import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.util.List;
import java.util.Optional;

public interface SegmentRepository {

  Optional<Segment> find(EnvironmentRef environment, SegmentKey key);

  List<Segment> findAll(EnvironmentRef environment);

  void save(EnvironmentRef environment, Segment segment, Revision expected);

  void delete(EnvironmentRef environment, SegmentKey key, Revision expected);
}
