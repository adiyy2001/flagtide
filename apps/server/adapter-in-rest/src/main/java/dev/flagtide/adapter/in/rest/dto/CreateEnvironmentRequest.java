package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.adapter.in.rest.Required;
import dev.flagtide.application.usecase.CreateEnvironment;
import dev.flagtide.domain.value.EnvironmentKey;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateEnvironment")
public record CreateEnvironmentRequest(String key, String name) {

  public CreateEnvironment.Command toCommand() {
    return new CreateEnvironment.Command(
        new EnvironmentKey(Required.text("key", this.key)), Required.text("name", this.name));
  }
}
