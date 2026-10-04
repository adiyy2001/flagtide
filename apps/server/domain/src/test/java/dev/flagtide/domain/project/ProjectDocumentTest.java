package dev.flagtide.domain.project;

import static dev.flagtide.domain.flag.FlagFixtures.DEV;
import static dev.flagtide.domain.flag.FlagFixtures.PROD;
import static dev.flagtide.domain.flag.FlagFixtures.stamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.json.JsonText;
import dev.flagtide.domain.value.ProjectKey;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectDocumentTest {

  @Test
  void roundTripsAProjectThroughItsDocument() {
    Project project =
        Project.create(
                new ProjectKey("shop"),
                "Shop",
                List.of(new Environment(DEV, "Development"), new Environment(PROD, "Production")),
                stamp("ann"))
            .next();

    String text = JsonText.write(ProjectDocument.toJson(project));

    assertThat(ProjectDocument.fromJson(JsonText.parse(text))).isEqualTo(project);
  }

  @Test
  void rejectsADocumentWithoutEnvironments() {
    assertThatThrownBy(
            () ->
                ProjectDocument.fromJson(
                    JsonText.parse(
                        """
                        {"key":"shop","name":"Shop","revision":1,"createdAt":"2026-10-03T12:00Z",
                         "environments":[]}
                        """)))
        .isInstanceOf(FlagtideException.class);
  }
}
