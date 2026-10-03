package dev.flagwire.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.flagwire.application.usecase.ListApiKeys;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ApiKey", description = "Admin keys are stored hashed and never shown again")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeyResponse(String id, String kind, String environment, String label, String sdkKey) {

  public static KeyResponse from(ListApiKeys.KeyView view) {
    return new KeyResponse(
        view.id(),
        view.kind().name().toLowerCase(Locale.ROOT),
        view.environment().value(),
        view.label(),
        view.sdkKey().orElse(null));
  }
}
