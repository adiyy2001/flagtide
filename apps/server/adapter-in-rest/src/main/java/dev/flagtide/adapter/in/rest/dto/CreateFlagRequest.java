package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.adapter.in.rest.Required;
import dev.flagtide.application.usecase.CreateFlag;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.VariantKey;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "CreateFlag")
public record CreateFlagRequest(
    String key,
    String description,
    @Schema(enumeration = {"boolean", "string", "number", "json"}) String type,
    List<VariantDto> variants,
    String offVariant,
    String fallthroughVariant) {

  public CreateFlag.Command toCommand() {
    return new CreateFlag.Command(
        new FlagKey(Required.text("key", this.key)),
        Required.textOr(this.description, ""),
        flagType(Required.text("type", this.type)),
        VariantDto.toDomain(this.variants),
        new VariantKey(Required.text("offVariant", this.offVariant)),
        new VariantKey(Required.text("fallthroughVariant", this.fallthroughVariant)));
  }

  private static FlagType flagType(String name) {
    try {
      return FlagType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw FlagtideException.invalid("type", "must be boolean, string, number or json");
    }
  }
}
