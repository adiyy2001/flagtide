package dev.flagwire.adapter.postgres;

import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.json.Json;
import dev.flagwire.domain.json.JsonFields;
import dev.flagwire.domain.json.JsonText;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.ProjectKey;

public final class ChangeNotifications {

  public static final String CHANNEL = "flagwire_changes";

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
