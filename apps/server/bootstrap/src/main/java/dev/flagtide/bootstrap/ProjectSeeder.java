package dev.flagtide.bootstrap;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.security.ApiKeys;
import dev.flagtide.application.usecase.CreateProject;
import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import java.util.Optional;
import java.util.TreeMap;
import org.jboss.logging.Logger;

public final class ProjectSeeder {

  private static final Logger LOG = Logger.getLogger(ProjectSeeder.class);
  private static final int MINIMUM_SECRET_LENGTH = 16;
  private static final String AUTHOR = "seed";

  private final CreateProject createProject;
  private final ProjectRepository projects;
  private final ApiKeyStore apiKeys;
  private final IdGenerator ids;
  private final TimeSource timeSource;

  public ProjectSeeder(
      CreateProject createProject,
      ProjectRepository projects,
      ApiKeyStore apiKeys,
      IdGenerator ids,
      TimeSource timeSource) {
    this.createProject = createProject;
    this.projects = projects;
    this.apiKeys = apiKeys;
    this.ids = ids;
    this.timeSource = timeSource;
  }

  public void seed(SeedConfig config) {
    ProjectKey key = new ProjectKey(config.project());
    Project project = this.ensureProject(key, config.name());
    new TreeMap<>(config.keys())
        .forEach(
            (environment, keys) -> {
              EnvironmentKey environmentKey = new EnvironmentKey(environment);
              if (project.environment(environmentKey).isEmpty()) {
                LOG.warnf(
                    "seed keys for environment %s skipped, project %s has no such environment",
                    environment, key.value());
                return;
              }
              keys.admin()
                  .ifPresent(
                      secret -> this.ensureKey(key, environmentKey, ApiKeyKind.ADMIN, secret));
              keys.sdk()
                  .ifPresent(secret -> this.ensureKey(key, environmentKey, ApiKeyKind.SDK, secret));
            });
  }

  private Project ensureProject(ProjectKey key, String name) {
    Optional<Project> existing = this.projects.find(key);
    if (existing.isPresent()) {
      return existing.get();
    }
    try {
      LOG.infof("seeding project %s", key.value());
      return this.createProject
          .execute(new CreateProject.Command(key, name, AUTHOR))
          .value()
          .project();
    } catch (FlagtideException raced) {
      if (raced.error() instanceof FlagtideError.Conflict) {
        return this.projects.find(key).orElseThrow(() -> raced);
      }
      throw raced;
    }
  }

  private void ensureKey(
      ProjectKey project, EnvironmentKey environment, ApiKeyKind kind, String secret) {
    if (secret.length() < MINIMUM_SECRET_LENGTH
        || ApiKeys.kindOf(secret).filter(found -> found == kind).isEmpty()) {
      throw new IllegalStateException(
          "seed "
              + kind.name().toLowerCase(java.util.Locale.ROOT)
              + " key for "
              + environment.value()
              + " must have the prefix of its kind and at least "
              + MINIMUM_SECRET_LENGTH
              + " characters");
    }
    String lookup = ApiKeys.lookupFor(kind, secret);
    Optional<ApiKey> existing = this.apiKeys.findByLookup(lookup);
    if (existing.isPresent()) {
      ApiKey found = existing.get();
      if (!found.project().equals(project) || !found.environment().equals(environment)) {
        throw new IllegalStateException(
            "a seed key for " + environment.value() + " already belongs to another environment");
      }
      return;
    }
    this.apiKeys.save(
        new ApiKey(
            this.ids.newId(),
            kind,
            project,
            environment,
            "seed-" + environment.value() + "-" + kind.name().toLowerCase(java.util.Locale.ROOT),
            lookup,
            this.timeSource.now()));
  }
}
