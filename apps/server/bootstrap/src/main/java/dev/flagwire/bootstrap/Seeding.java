package dev.flagwire.bootstrap;

import dev.flagwire.application.port.out.ApiKeyStore;
import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.application.port.out.ProjectRepository;
import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.usecase.CreateProject;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class Seeding {

  private final SeedConfig config;
  private final ProjectSeeder seeder;

  @Inject
  public Seeding(
      SeedConfig config,
      CreateProject createProject,
      ProjectRepository projects,
      ApiKeyStore apiKeys,
      IdGenerator ids,
      TimeSource timeSource) {
    this.config = config;
    this.seeder = new ProjectSeeder(createProject, projects, apiKeys, ids, timeSource);
  }

  void onStart(@Observes StartupEvent startup) {
    if (this.config.enabled()) {
      this.seeder.seed(this.config);
    }
  }
}
