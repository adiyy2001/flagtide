package dev.flagtide.adapter.out.postgres;

import dev.flagtide.application.port.out.ChangeNotification;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.json.JsonText;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.ProjectKey;

public final class ChangeNotifications {

  public static final String CHANNEL = "flagtide_changes";

  private ChangeNotifications() {}

  public static String payload(ChangeNotification notification) {
    JsonValue pointer =
        Json.object()
            .text("project", notification.environment().project().value())
            .text("environment", notification.environment().environment().value())
            .number("version", notification.version().value())
            .build();
    return JsonText.write(pointer);
  }

  public static ChangeNotification parse(String payload) {
    JsonFields fields = JsonFields.of("notification", JsonText.parse(payload));
    return new ChangeNotification(
        new EnvironmentRef(
            new ProjectKey(fields.text("project")), new EnvironmentKey(fields.text("environment"))),
        EnvironmentVersion.of(fields.integer("version")));
  }
}
