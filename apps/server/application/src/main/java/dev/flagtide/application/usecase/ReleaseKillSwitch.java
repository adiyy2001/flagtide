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
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import java.util.Optional;

public final class ReleaseKillSwitch {

  public record Command(FlagKey key, EnvironmentKey environment) {}

  private final Authorizer authorizer;
  private final FlagEditor editor;

  public ReleaseKillSwitch(
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
    this.authorizer.requireEnvironmentWrite(principal, command.environment());
    return this.editor.edit(
        principal,
        command.key(),
        Optional.empty(),
        (flag, stamp) -> flag.releaseKillSwitch(command.environment(), stamp));
  }
}
