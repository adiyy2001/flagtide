package dev.flagtide.application.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.project.Environment;
import dev.flagtide.domain.project.Project;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.ProjectKey;
import dev.flagtide.domain.value.Revision;
import org.junit.jupiter.api.Test;

public abstract class ProjectRepositoryContract extends AdapterContract {

  @Test
  void savesFindsAndListsProjectsInKeyOrder() {
    Project shop = Samples.project("shop");
    Project blog = Samples.project("blog");

    this.adapters.projects().save(shop, Revision.NONE);
    this.adapters.projects().save(blog, Revision.NONE);

    assertThat(this.adapters.projects().find(shop.key())).contains(shop);
    assertThat(this.adapters.projects().find(new ProjectKey("none"))).isEmpty();
    assertThat(this.adapters.projects().findAll()).containsExactly(blog, shop);
  }

  @Test
  void checksTheExpectedRevision() {
    Project first = Samples.project("shop");
    this.adapters.projects().save(first, Revision.NONE);
    Project second =
        first
            .addEnvironment(new Environment(new EnvironmentKey("qa"), "QA"), Samples.stamp("a"))
            .next();

    assertThatThrownBy(() -> this.adapters.projects().save(first, Revision.NONE))
        .isInstanceOf(FlagtideException.class);
    assertThatThrownBy(() -> this.adapters.projects().save(second, Revision.of(5)))
        .isInstanceOf(FlagtideException.class);
    this.adapters.projects().save(second, first.revision());
    assertThat(this.adapters.projects().find(first.key())).contains(second);
  }

  @Test
  void rollsBackASave() {
    Project shop = Samples.project("shop");

    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.projects().save(shop, Revision.NONE);
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.adapters.projects().findAll()).isEmpty();
  }
}
