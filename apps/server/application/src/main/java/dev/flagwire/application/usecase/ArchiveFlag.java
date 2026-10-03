package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Revision;
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
