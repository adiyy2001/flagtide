package dev.flagtide.application.usecase;

import dev.flagtide.application.change.Change;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.audit.AuditEntry;
import dev.flagtide.domain.audit.EntityType;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.flag.FlagCompiler;
import dev.flagtide.domain.flag.FlagDocument;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.segment.SegmentDocument;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.ProjectKey;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ChangeRecorder {

  private final ChangeLog changeLog;
  private final AuditLog auditLog;
  private final TimeSource timeSource;
  private final IdGenerator ids;

  ChangeRecorder(ChangeLog changeLog, AuditLog auditLog, TimeSource timeSource, IdGenerator ids) {
    this.changeLog = changeLog;
    this.auditLog = auditLog;
    this.timeSource = timeSource;
    this.ids = ids;
  }

  Stamp stamp(Principal principal) {
    return this.stamp(principal.author());
  }

  Stamp stamp(String author) {
    return new Stamp(this.ids.newId(), this.timeSource.now(), author);
  }

  Map<EnvironmentKey, EnvironmentVersion> recordFlag(
      ProjectKey project, Optional<Flag> before, Flag after, DomainEvent event) {
    Map<EnvironmentKey, EnvironmentVersion> versions = new HashMap<>();
    event.environments().stream()
        .sorted(Comparator.naturalOrder())
        .forEach(
            environment -> {
              Change change =
                  after.archived()
                      ? new Change.FlagRemoved(after.key().value())
                      : new Change.FlagUpserted(FlagCompiler.compile(after, environment));
              EnvironmentVersion version =
                  this.appendChange(new EnvironmentRef(project, environment), change);
              versions.put(environment, version);
              this.audit(
                  project,
                  Optional.of(environment),
                  Optional.of(version),
                  EntityType.FLAG,
                  after.key().value(),
                  event,
                  before.map(flag -> this.flagView(flag, event, environment)),
                  Optional.of(this.flagView(after, event, environment)));
            });
    return versions;
  }

  Map<EnvironmentKey, EnvironmentVersion> recordSegment(
      ProjectKey project,
      Optional<Segment> before,
      Optional<Segment> after,
      DomainEvent event,
      String segmentKey) {
    EnvironmentKey environment = event.environments().iterator().next();
    Change change =
        after
            .<Change>map(
                segment -> new Change.SegmentUpserted(FlagCompiler.compileSegment(segment)))
            .orElseGet(() -> new Change.SegmentRemoved(segmentKey));
    EnvironmentVersion version =
        this.appendChange(new EnvironmentRef(project, environment), change);
    this.audit(
        project,
        Optional.of(environment),
        Optional.of(version),
        EntityType.SEGMENT,
        segmentKey,
        event,
        before.map(SegmentDocument::toJson),
        after.map(SegmentDocument::toJson));
    return Map.of(environment, version);
  }

  void recordProvisioning(
      ProjectKey project,
      Optional<EnvironmentKey> environment,
      EntityType entityType,
      String entityKey,
      DomainEvent event,
      JsonValue after) {
    this.audit(
        project,
        environment,
        Optional.empty(),
        entityType,
        entityKey,
        event,
        Optional.empty(),
        Optional.of(after));
  }

  private EnvironmentVersion appendChange(EnvironmentRef environment, Change change) {
    return this.changeLog.append(environment, this.timeSource.now(), List.of(change));
  }

  private void audit(
      ProjectKey project,
      Optional<EnvironmentKey> environment,
      Optional<EnvironmentVersion> version,
      EntityType entityType,
      String entityKey,
      DomainEvent event,
      Optional<JsonValue> before,
      Optional<JsonValue> after) {
    this.auditLog.append(
        new AuditEntry(
            this.ids.newId(),
            project,
            environment,
            version,
            entityType,
            entityKey,
            event.getClass().getSimpleName(),
            event.stamp().author(),
            event.stamp().occurredAt(),
            before,
            after));
  }

  private JsonValue flagView(Flag flag, DomainEvent event, EnvironmentKey environment) {
    boolean definitionLevel =
        event instanceof DomainEvent.FlagCreated
            || event instanceof DomainEvent.FlagDefinitionChanged
            || event instanceof DomainEvent.FlagArchived;
    return definitionLevel
        ? FlagDocument.definitionToJson(flag)
        : FlagDocument.environmentToJson(flag.requireEnvironment(environment));
  }
}
