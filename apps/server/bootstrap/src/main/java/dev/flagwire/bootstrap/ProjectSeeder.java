package dev.flagwire.bootstrap;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.ApiKeys;
import dev.flagwire.application.usecase.CreateProject;
import dev.flagwire.domain.access.ApiKey;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.project.Project;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.ProjectKey;
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
    } catch (FlagwireException raced) {
      if (raced.error() instanceof FlagwireError.Conflict) {
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
