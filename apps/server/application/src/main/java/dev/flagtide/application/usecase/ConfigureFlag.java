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
import dev.flagtide.domain.flag.EnvironmentSettings;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.SegmentKey;
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
