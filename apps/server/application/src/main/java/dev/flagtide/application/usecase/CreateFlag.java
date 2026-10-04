package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.flag.FlagVariant;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.Salt;
import dev.flagtide.domain.value.VariantKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CreateFlag {

  public record Command(
      FlagKey key,
      String description,
      FlagType type,
      List<FlagVariant> variants,
      VariantKey offVariant,
      VariantKey fallthroughVariant) {

    public Command {
      variants = List.copyOf(variants);
    }
  }

  private final TransactionRunner transactions;
  private final ProjectRepository projects;
  private final FlagRepository flags;
  private final IdGenerator ids;
  private final Authorizer authorizer;
  private final ChangeRecorder recorder;

  public CreateFlag(
      TransactionRunner transactions,
      ProjectRepository projects,
      FlagRepository flags,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.transactions = transactions;
    this.projects = projects;
    this.flags = flags;
    this.ids = ids;
    this.authorizer = authorizer;
    this.recorder = new ChangeRecorder(changeLog, auditLog, timeSource, ids);
  }

  public CommandResult<Flag> execute(Principal principal, Command command) {
    this.authorizer.requireAdmin(principal);
    return this.transactions.inTransaction(
        () -> {
          Project project =
              this.projects
                  .find(principal.project())
                  .orElseThrow(
                      () -> FlagtideException.notFound("project", principal.project().value()));
          if (this.flags.find(project.key(), command.key()).isPresent()) {
            throw FlagtideException.conflict("flag " + command.key().value() + " already exists");
          }
          Map<EnvironmentKey, Salt> salts = new HashMap<>();
          project.environmentKeys().forEach(key -> salts.put(key, this.ids.newSalt()));
          Transition<Flag> created =
              Flag.create(
                  command.key(),
                  command.description(),
                  command.type(),
                  command.variants(),
                  command.offVariant(),
                  command.fallthroughVariant(),
                  salts,
                  this.recorder.stamp(principal));
          this.flags.save(project.key(), created.next(), Revision.NONE);
          return new CommandResult<>(
              created.next(),
              created.events(),
              this.recorder.recordFlag(
                  project.key(), Optional.empty(), created.next(), created.events().get(0)));
        });
  }
}
