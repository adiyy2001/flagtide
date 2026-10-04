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
import dev.flagtide.domain.flag.FlagVariant;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Optional;

public final class UpdateFlagDefinition {

  public record Command(
      FlagKey key,
      Optional<Revision> expectedRevision,
      String description,
      List<FlagVariant> variants) {

    public Command {
      variants = List.copyOf(variants);
    }
  }

  private final Authorizer authorizer;
  private final FlagEditor editor;

  public UpdateFlagDefinition(
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
        principal,
        command.key(),
        command.expectedRevision(),
        (flag, stamp) -> flag.updateDefinition(command.description(), command.variants(), stamp));
  }
}
