package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.application.usecase.CreateEnvironment;
import dev.flagwire.domain.value.EnvironmentKey;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateEnvironment")
public record CreateEnvironmentRequest(String key, String name) {

  public CreateEnvironment.Command toCommand() {
    return new CreateEnvironment.Command(
        new EnvironmentKey(Required.text("key", this.key)), Required.text("name", this.name));
  }
}
