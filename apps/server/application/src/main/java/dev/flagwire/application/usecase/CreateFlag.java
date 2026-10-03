package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.AuditLog;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.port.out.TransactionRunner;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.event.Transition;
import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.flag.FlagVariant;
import dev.flagwire.domain.project.Project;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.Revision;
import dev.flagwire.domain.value.Salt;
import dev.flagwire.domain.value.VariantKey;
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
                      () -> FlagwireException.notFound("project", principal.project().value()));
          if (this.flags.find(project.key(), command.key()).isPresent()) {
            throw FlagwireException.conflict("flag " + command.key().value() + " already exists");
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
