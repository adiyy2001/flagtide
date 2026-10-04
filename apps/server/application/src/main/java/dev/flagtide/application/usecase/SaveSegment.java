package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SaveSegment {

  public record Command(
      EnvironmentKey environment,
      SegmentKey key,
      Optional<Revision> expectedRevision,
      String name,
      Set<String> included,
      Set<String> excluded,
      List<List<Condition.Attribute>> rules) {

    public Command {
      included = Set.copyOf(included);
      excluded = Set.copyOf(excluded);
      rules = rules.stream().map(List::copyOf).toList();
    }
  }

  private final TransactionRunner transactions;
  private final SegmentRepository segments;
  private final Authorizer authorizer;
  private final ChangeRecorder recorder;

  public SaveSegment(
      TransactionRunner transactions,
      SegmentRepository segments,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.transactions = transactions;
    this.segments = segments;
    this.authorizer = authorizer;
    this.recorder = new ChangeRecorder(changeLog, auditLog, timeSource, ids);
  }

  public CommandResult<Segment> execute(Principal principal, Command command) {
    this.authorizer.requireEnvironmentWrite(principal, command.environment());
    EnvironmentRef scope = new EnvironmentRef(principal.project(), command.environment());
    return this.transactions.inTransaction(
        () -> {
          Optional<Segment> existing = this.segments.find(scope, command.key());
          Stamp stamp = this.recorder.stamp(principal);
          Transition<Segment> transition =
              existing
                  .map(current -> this.update(current, command, stamp))
                  .orElseGet(() -> this.create(command, stamp));
          if (!transition.changed()) {
            return CommandResult.unchanged(transition.next());
          }
          Revision expected = existing.map(Segment::revision).orElse(Revision.NONE);
          this.segments.save(scope, transition.next(), expected);
          return new CommandResult<>(
              transition.next(),
              transition.events(),
              this.recorder.recordSegment(
                  principal.project(),
                  existing,
                  Optional.of(transition.next()),
                  transition.events().get(0),
                  command.key().value()));
        });
  }

  private Transition<Segment> create(Command command, Stamp stamp) {
    command
        .expectedRevision()
        .filter(revision -> !revision.equals(Revision.NONE))
        .ifPresent(
            revision -> {
              throw FlagtideException.notFound("segment", command.key().value());
            });
    return Segment.create(
        command.environment(),
        command.key(),
        command.name(),
        command.included(),
        command.excluded(),
        command.rules(),
        stamp);
  }

  private Transition<Segment> update(Segment current, Command command, Stamp stamp) {
    command
        .expectedRevision()
        .filter(revision -> !revision.equals(current.revision()))
        .ifPresent(
            revision -> {
              throw FlagtideException.conflict(
                  "segment "
                      + current.key().value()
                      + " is at revision "
                      + current.revision().value()
                      + " but the edit was based on revision "
                      + revision.value());
            });
    return current.update(
        command.name(), command.included(), command.excluded(), command.rules(), stamp);
  }
}
