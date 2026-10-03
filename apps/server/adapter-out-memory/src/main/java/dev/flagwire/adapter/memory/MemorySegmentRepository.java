package dev.flagwire.adapter.memory;

import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MemorySegmentRepository implements SegmentRepository {

  private final MemoryDatabase database;
  private final Map<EnvironmentRef, Map<SegmentKey, Segment>> segments = new HashMap<>();

  public MemorySegmentRepository(MemoryDatabase database) {
    this.database = database;
  }

  @Override
  public Optional<Segment> find(EnvironmentRef environment, SegmentKey key) {
    return this.database.read(
        () -> Optional.ofNullable(this.segments.getOrDefault(environment, Map.of()).get(key)));
  }

  @Override
  public List<Segment> findAll(EnvironmentRef environment) {
    return this.database.read(
        () ->
            this.segments.getOrDefault(environment, Map.of()).values().stream()
                .sorted(Comparator.comparing(Segment::key))
                .toList());
  }

  @Override
  public void save(EnvironmentRef environment, Segment segment, Revision expected) {
    this.database.write(
        () -> {
          Map<SegmentKey, Segment> ofEnvironment =
              this.segments.computeIfAbsent(environment, key -> new HashMap<>());
          Optional<Segment> previous = Optional.ofNullable(ofEnvironment.get(segment.key()));
          Revisions.requireMatch(
              "segment", segment.key().value(), previous.map(Segment::revision), expected);
          ofEnvironment.put(segment.key(), segment);
          this.database.onRollback(
              () ->
                  previous.ifPresentOrElse(
                      old -> ofEnvironment.put(segment.key(), old),
                      () -> ofEnvironment.remove(segment.key())));
          return null;
        });
  }

  @Override
  public void delete(EnvironmentRef environment, SegmentKey key, Revision expected) {
    this.database.write(
        () -> {
          Map<SegmentKey, Segment> ofEnvironment =
              this.segments.getOrDefault(environment, Map.of());
          Segment previous = ofEnvironment.get(key);
          if (previous == null) {
            throw FlagwireException.notFound("segment", key.value());
          }
          Revisions.requireMatch(
              "segment", key.value(), Optional.of(previous.revision()), expected);
          ofEnvironment.remove(key);
          this.database.onRollback(() -> ofEnvironment.put(key, previous));
          return null;
        });
  }
}
