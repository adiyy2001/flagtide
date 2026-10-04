package dev.flagtide.application.stream;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.json.JsonText;
import dev.flagtide.domain.value.EnvironmentVersion;
import java.util.Optional;

public sealed interface ClientFrame {

  int MAX_TEXT_LENGTH = 4096;

  record Hello(
      String sdkKey,
      Optional<EnvironmentVersion> version,
      Optional<String> clientId,
      Optional<String> sdk)
      implements ClientFrame {}

  record Ack(EnvironmentVersion version) implements ClientFrame {}

  static ClientFrame parse(String text) {
    if (text.length() > MAX_TEXT_LENGTH) {
      throw FlagtideException.invalid("frame", "is longer than " + MAX_TEXT_LENGTH + " characters");
    }
    JsonFields fields = JsonFields.of("frame", JsonText.parse(text));
    String type = fields.text("t");
    return switch (type) {
      case "hello" ->
          new Hello(
              fields.text("sdkKey"),
              fields.find("version").map(value -> EnvironmentVersion.of(fields.integer("version"))),
              fields.optionalText("clientId"),
              fields.optionalText("sdk"));
      case "ack" -> new Ack(EnvironmentVersion.of(fields.integer("v")));
      default -> throw FlagtideException.invalid("frame.t", "unknown frame type " + type);
    };
  }
}
