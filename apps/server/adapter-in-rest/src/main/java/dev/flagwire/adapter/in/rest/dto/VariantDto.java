package dev.flagwire.adapter.in.rest.dto;

import com.fasterxml.jackson.databind.JsonNode;
import dev.flagwire.adapter.in.rest.JsonNodes;
import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.domain.flag.FlagVariant;
import dev.flagwire.domain.value.VariantKey;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Variant")
public record VariantDto(
    String key, @Schema(description = "Any JSON value of the flag type") JsonNode value) {

  public static VariantDto from(FlagVariant variant) {
    return new VariantDto(variant.key().value(), JsonNodes.toNode(variant.value()));
  }

  public FlagVariant toDomain() {
    return new FlagVariant(
        new VariantKey(Required.text("variants.key", this.key)),
        JsonNodes.toValue("variants.value", this.value));
  }

  public static List<FlagVariant> toDomain(List<VariantDto> variants) {
    return Required.items("variants", variants).stream().map(VariantDto::toDomain).toList();
  }
}
