package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.AuditLog;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.domain.audit.EntityType;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.project.Environment;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CreateProject {

  private static final List<Environment> DEFAULT_ENVIRONMENTS =
      List.of(
          new Environment(new EnvironmentKey("dev"), "Development"),
          new Environment(new EnvironmentKey("staging"), "Staging"),
          new Environment(new EnvironmentKey("prod"), "Production"));

  public record Command(ProjectKey key, String name, String author) {}

  private final TransactionRunner transactions;
  private final ProjectRepository projects;
  private final ChangeRecorder recorder;
  private final KeyIssuer keyIssuer;

  public CreateProject(
      TransactionRunner transactions,
      ProjectRepository projects,
      ApiKeyStore apiKeys,
      ChangeLog changeLog,
      AuditLog auditLog,
      TimeSource timeSource,
      IdGenerator ids) {
    this.transactions = transactions;
    this.projects = projects;
    this.recorder = new ChangeRecorder(changeLog, auditLog, timeSource, ids);
    this.keyIssuer = new KeyIssuer(apiKeys, ids, timeSource);
  }

  public CommandResult<ProvisionedProject> execute(Command command) {
    return this.transactions.inTransaction(
        () -> {
          if (this.projects.find(command.key()).isPresent()) {
            throw FlagtideException.conflict(
                "project " + command.key().value() + " already exists");
          }
          Stamp stamp = this.recorder.stamp(command.author());
          Transition<Project> created =
              Project.create(command.key(), command.name(), DEFAULT_ENVIRONMENTS, stamp);
          Project project = created.next();
          this.projects.save(project, Revision.NONE);
          List<IssuedKey> keys =
              project.environments().stream()
                  .flatMap(
                      environment ->
                          this.keyIssuer.issueFor(project.key(), environment.key()).stream())
                  .toList();
          this.recorder.recordProvisioning(
              project.key(),
              Optional.empty(),
              EntityType.PROJECT,
              project.key().value(),
              created.events().get(0),
              Json.object()
                  .text("key", project.key().value())
                  .text("name", project.name())
                  .build());
          return new CommandResult<>(
              new ProvisionedProject(project, keys), created.events(), Map.of());
        });
  }
}
