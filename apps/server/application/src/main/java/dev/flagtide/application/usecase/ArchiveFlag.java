package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import java.util.Optional;

public final class ArchiveFlag {

  public record Command(FlagKey key, Optional<Revision> expectedRevision) {}

  private final Authorizer authorizer;
  private final FlagEditor editor;

  public ArchiveFlag(
      TransactionRunner transactions,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.authorizer = authorizer;
    this.editor =
        new FlagEditor(
            transactions, flags, new ChangeRecorder(changeLog, auditLog, timeSource, ids));
  }

  public CommandResult<Flag> execute(Principal principal, Command command) {
    this.authorizer.requireAdmin(principal);
    return this.editor.edit(
        principal, command.key(), command.expectedRevision(), (flag, stamp) -> flag.archive(stamp));
  }
}
