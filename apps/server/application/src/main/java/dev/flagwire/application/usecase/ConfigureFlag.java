package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.flag.EnvironmentSettings;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.SegmentKey;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ConfigureFlag {

  public record Command(
      FlagKey key,
      Optional<Revision> expectedRevision,
      EnvironmentKey environment,
      EnvironmentSettings settings) {}

  private final Authorizer authorizer;
  private final TransactionRunner transactions;
  private final SegmentRepository segments;
  private final FlagEditor editor;

  public ConfigureFlag(
      TransactionRunner transactions,
      FlagRepository flags,
      SegmentRepository segments,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.authorizer = authorizer;
    this.transactions = transactions;
    this.segments = segments;
    this.editor =
        new FlagEditor(
            transactions, flags, new ChangeRecorder(changeLog, auditLog, timeSource, ids));
  }

  public CommandResult<Flag> execute(Principal principal, Command command) {
    this.authorizer.requireEnvironmentWrite(principal, command.environment());
    return this.transactions.inTransaction(
        () -> {
          Set<SegmentKey> known = this.segmentKeys(principal.project(), command.environment());
          return this.editor.edit(
              principal,
              command.key(),
              command.expectedRevision(),
              (flag, stamp) ->
                  flag.configure(command.environment(), command.settings(), known, stamp));
        });
  }

  private Set<SegmentKey> segmentKeys(ProjectKey project, EnvironmentKey environment) {
    return this.segments.findAll(new EnvironmentRef(project, environment)).stream()
        .map(Segment::key)
        .collect(Collectors.toSet());
  }
}
