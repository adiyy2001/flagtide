package dev.flagtide.bootstrap;

import static dev.flagtide.bootstrap.Requests.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.IdGenerator;
import dev.flagtide.application.port.out.ProjectRepository;
import dev.flagtide.application.port.out.TimeSource;
import dev.flagtide.application.usecase.CreateProject;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.ProjectKey;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

abstract class SeedingTest {

  @Inject CreateProject createProject;
  @Inject ProjectRepository projects;
  @Inject ApiKeyStore apiKeys;
  @Inject IdGenerator ids;
  @Inject TimeSource timeSource;
  @Inject SeedConfig config;

  private ProjectSeeder seeder() {
    return new ProjectSeeder(
        this.createProject, this.projects, this.apiKeys, this.ids, this.timeSource);
  }

  @Test
  void theSeededKeysWorkOnStartup() {
    as("fwa_demo_dev_admin_0000")
        .get("/api/v1/projects/demo")
        .then()
        .statusCode(200)
        .body("name", equalTo("Demo"));
    as("fws_demo_prod_sdk_0000").get("/sdk/v1/snapshot").then().statusCode(200);
  }

  @Test
  void seedingAgainChangesNothing() {
    int keysBefore =
        this.apiKeys
            .findByEnvironment(
                new EnvironmentRef(new ProjectKey("demo"), new EnvironmentKey("dev")))
            .size();

    this.seeder().seed(this.config);

    assertThat(
            this.apiKeys
                .findByEnvironment(
                    new EnvironmentRef(new ProjectKey("demo"), new EnvironmentKey("dev")))
                .size())
        .isEqualTo(keysBefore);
    assertThat(this.projects.find(new ProjectKey("demo"))).isPresent();
  }

  @Test
  void aSeedKeyWithTheWrongPrefixFailsFast() {
    SeedConfig wrong =
        new FixedSeed(
            "demo",
            Map.of("dev", new FixedKeys(Optional.of("fws_looks_like_sdk_key"), Optional.empty())));

    assertThatThrownBy(() -> this.seeder().seed(wrong))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("prefix");
  }

  @Test
  void aSeedKeyThatIsTooShortFailsFast() {
    SeedConfig wrong =
        new FixedSeed(
            "demo", Map.of("dev", new FixedKeys(Optional.empty(), Optional.of("fws_short"))));

    assertThatThrownBy(() -> this.seeder().seed(wrong)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aSeedKeyOwnedByAnotherEnvironmentFailsFast() {
    SeedConfig stolen =
        new FixedSeed(
            "demo",
            Map.of(
                "staging", new FixedKeys(Optional.empty(), Optional.of("fws_demo_dev_sdk_00000"))));

    assertThatThrownBy(() -> this.seeder().seed(stolen))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("another environment");
  }

  record FixedSeed(String project, Map<String, SeedConfig.Keys> keys) implements SeedConfig {

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public String name() {
      return "Demo";
    }
  }

  record FixedKeys(Optional<String> admin, Optional<String> sdk) implements SeedConfig.Keys {}
}
