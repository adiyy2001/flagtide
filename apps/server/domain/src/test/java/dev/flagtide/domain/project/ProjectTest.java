package dev.flagtide.domain.project;

import static dev.flagtide.domain.flag.FlagFixtures.DEV;
import static dev.flagtide.domain.flag.FlagFixtures.NOW;
import static dev.flagtide.domain.flag.FlagFixtures.PROD;
import static dev.flagtide.domain.flag.FlagFixtures.STAGING;
import static dev.flagtide.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectTest {

  private static final ProjectKey KEY = new ProjectKey("shop");

  private static Project project() {
    return Project.create(
            KEY,
            "Shop",
            List.of(new Environment(DEV, "Development"), new Environment(PROD, "Production")),
            stamp("ann"))
        .next();
  }

  @Test
  void createsAProjectAndEmitsOneEvent() {
    Transition<Project> created =
        Project.create(KEY, "Shop", List.of(new Environment(DEV, "Development")), stamp("ann"));

    assertThat(created.next().revision()).isEqualTo(Revision.FIRST);
    assertThat(created.next().createdAt()).isEqualTo(NOW);
    assertThat(created.events()).containsExactly(new DomainEvent.ProjectCreated(stamp("ann"), KEY));
  }

  @Test
  void needsAtLeastOneUniqueEnvironment() {
    assertThatThrownBy(() -> Project.create(KEY, "Shop", List.of(), stamp("a")))
        .hasMessageContaining("at least one environment");
    assertThatThrownBy(
            () ->
                Project.create(
                    KEY,
                    "Shop",
                    List.of(new Environment(DEV, "a"), new Environment(DEV, "b")),
                    stamp("a")))
        .hasMessageContaining("listed twice");
  }

  @Test
  void rejectsBlankNames() {
    assertThatThrownBy(
            () -> Project.create(KEY, " ", List.of(new Environment(DEV, "a")), stamp("a")))
        .hasMessageContaining("project name");
    assertThatThrownBy(() -> new Environment(DEV, "")).hasMessageContaining("environment name");
  }

  @Test
  void addsAnEnvironmentWithOneEvent() {
    Project project = project();

    Transition<Project> result =
        project.addEnvironment(new Environment(STAGING, "Staging"), stamp("bob"));

    assertThat(result.next().environmentKeys()).containsExactlyInAnyOrder(DEV, PROD, STAGING);
    assertThat(result.next().revision()).isEqualTo(Revision.of(2));
    assertThat(result.events())
        .containsExactly(new DomainEvent.EnvironmentCreated(stamp("bob"), KEY, STAGING));
    assertThat(result.events().get(0).environments()).isEqualTo(Set.of());
    assertThat(new DomainEvent.ProjectCreated(stamp("a"), KEY).environments()).isEmpty();
  }

  @Test
  void rejectsAnEnvironmentThatExists() {
    assertThatThrownBy(() -> project().addEnvironment(new Environment(DEV, "again"), stamp("a")))
        .hasMessageContaining("already exists");
  }

  @Test
  void findsEnvironmentsByKey() {
    assertThat(project().environment(DEV)).isPresent();
    assertThat(project().environment(new EnvironmentKey("none"))).isEmpty();
  }

  @Test
  void rejectsAStoredProjectWithoutARevision() {
    assertThatThrownBy(
            () -> new Project(KEY, "Shop", List.of(new Environment(DEV, "a")), Revision.NONE, NOW))
        .hasMessageContaining("revision");
  }
}
