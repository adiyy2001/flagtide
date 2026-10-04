package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.adapter.in.rest.Required;
import dev.flagtide.application.usecase.UpdateFlagDefinition;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import java.util.List;
import java.util.Optional;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UpdateFlag")
public record UpdateFlagRequest(String description, List<VariantDto> variants) {

  public UpdateFlagDefinition.Command toCommand(FlagKey key, Optional<Revision> expected) {
    return new UpdateFlagDefinition.Command(
        key,
        expected,
        Required.text("description", this.description),
        VariantDto.toDomain(this.variants));
  }
}
