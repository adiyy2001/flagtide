package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class DeleteSegment {

  public record Command(
      EnvironmentKey environment, SegmentKey key, Optional<Revision> expectedRevision) {}

  private final TransactionRunner transactions;
  private final SegmentRepository segments;
  private final FlagRepository flags;
  private final Authorizer authorizer;
  private final ChangeRecorder recorder;

  public DeleteSegment(
      TransactionRunner transactions,
      SegmentRepository segments,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.transactions = transactions;
    this.segments = segments;
    this.flags = flags;
    this.authorizer = authorizer;
    this.recorder = new ChangeRecorder(changeLog, auditLog, timeSource, ids);
  }

  public CommandResult<SegmentKey> execute(Principal principal, Command command) {
    this.authorizer.requireEnvironmentWrite(principal, command.environment());
    EnvironmentRef scope = new EnvironmentRef(principal.project(), command.environment());
    return this.transactions.inTransaction(
        () -> {
          Segment segment =
              this.segments
                  .find(scope, command.key())
                  .orElseThrow(() -> FlagtideException.notFound("segment", command.key().value()));
          command
              .expectedRevision()
              .filter(revision -> !revision.equals(segment.revision()))
              .ifPresent(
                  revision -> {
                    throw FlagtideException.conflict(
                        "segment "
                            + segment.key().value()
                            + " is at revision "
                            + segment.revision().value()
                            + " but the edit was based on revision "
                            + revision.value());
                  });
          this.requireUnused(principal, command);
          DomainEvent event = segment.delete(this.recorder.stamp(principal));
          this.segments.delete(scope, command.key(), segment.revision());
          return new CommandResult<>(
              command.key(),
              List.of(event),
              this.recorder.recordSegment(
                  principal.project(),
                  Optional.of(segment),
                  Optional.empty(),
                  event,
                  command.key().value()));
        });
  }

  private void requireUnused(Principal principal, Command command) {
    List<String> users =
        this.flags.findAll(principal.project()).stream()
            .filter(flag -> !flag.archived())
            .filter(flag -> flag.referencedSegments(command.environment()).contains(command.key()))
            .map(flag -> flag.key().value())
            .sorted()
            .toList();
    if (!users.isEmpty()) {
      throw FlagtideException.conflict(
          "segment "
              + command.key().value()
              + " is used by "
              + users.stream().collect(Collectors.joining(", ")));
    }
  }
}
