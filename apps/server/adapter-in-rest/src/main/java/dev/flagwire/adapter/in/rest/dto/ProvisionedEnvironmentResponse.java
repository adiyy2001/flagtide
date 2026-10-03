package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.application.usecase.IssuedKey;
import dev.flagwire.application.usecase.ProvisionedEnvironment;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ProvisionedEnvironment")
public record ProvisionedEnvironmentResponse(
    String key, String name, List<IssuedKeyResponse> keys) {

  @Schema(name = "IssuedKey", description = "The secret is shown once and cannot be read again")
  public record IssuedKeyResponse(String id, String kind, String label, String secret) {

    static IssuedKeyResponse from(IssuedKey issued) {
      return new IssuedKeyResponse(
          issued.key().id(),
          issued.key().kind().name().toLowerCase(java.util.Locale.ROOT),
          issued.key().label(),
          issued.secret());
    }
  }

  public static ProvisionedEnvironmentResponse from(ProvisionedEnvironment provisioned) {
    return new ProvisionedEnvironmentResponse(
        provisioned.environment().key().value(),
        provisioned.environment().name(),
        provisioned.keys().stream().map(IssuedKeyResponse::from).toList());
  }
}
