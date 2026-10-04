package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.audit.EntityType;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.project.Environment;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.EnvironmentKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CreateEnvironment {

  public record Command(EnvironmentKey key, String name) {}

  private final TransactionRunner transactions;
  private final ProjectRepository projects;
  private final FlagRepository flags;
  private final IdGenerator ids;
  private final TimeSource timeSource;
  private final Authorizer authorizer;
  private final ChangeRecorder recorder;
  private final KeyIssuer keyIssuer;

  public CreateEnvironment(
      TransactionRunner transactions,
      ProjectRepository projects,
      FlagRepository flags,
      ApiKeyStore apiKeys,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids,
      Authorizer authorizer) {
    this.transactions = transactions;
    this.projects = projects;
    this.flags = flags;
    this.ids = ids;
    this.timeSource = timeSource;
    this.authorizer = authorizer;
    this.recorder = new ChangeRecorder(changeLog, auditLog, timeSource, ids);
    this.keyIssuer = new KeyIssuer(apiKeys, ids, timeSource);
  }

  public CommandResult<ProvisionedEnvironment> execute(Principal principal, Command command) {
    this.authorizer.requireAdmin(principal);
    return this.transactions.inTransaction(
        () -> {
          Project project =
              this.projects
                  .find(principal.project())
                  .orElseThrow(
                      () -> FlagtideException.notFound("project", principal.project().value()));
          Stamp stamp = this.recorder.stamp(principal);
          Environment environment = new Environment(command.key(), command.name());
          Transition<Project> extended = project.addEnvironment(environment, stamp);
          this.projects.save(extended.next(), project.revision());
          this.flags
              .findAll(project.key())
              .forEach(
                  flag ->
                      this.flags.save(
                          project.key(),
                          flag.withEnvironment(
                              command.key(), this.ids.newSalt(), this.timeSource.now()),
                          flag.revision()));
          List<IssuedKey> keys = this.keyIssuer.issueFor(project.key(), command.key());
          this.recorder.recordProvisioning(
              project.key(),
              Optional.of(command.key()),
              EntityType.ENVIRONMENT,
              command.key().value(),
              extended.events().get(0),
              Json.object()
                  .text("key", command.key().value())
                  .text("name", command.name())
                  .build());
          return new CommandResult<>(
              new ProvisionedEnvironment(environment, keys), extended.events(), Map.of());
        });
  }
}
