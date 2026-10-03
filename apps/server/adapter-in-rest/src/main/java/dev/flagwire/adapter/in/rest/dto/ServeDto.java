package dev.flagwire.adapter.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.flag.RolloutEntry;
import dev.flagwire.domain.flag.Serving;
import dev.flagwire.domain.value.VariantKey;
import dev.flagwire.domain.value.Weight;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
    name = "Serve",
    description = "Serves one variant, or a weighted rollout whose weights sum to 100000")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServeDto(String variant, List<RolloutDto> rollout) {

  @Schema(name = "RolloutEntry")
  public record RolloutDto(String variant, Integer weight) {}

  public static ServeDto from(Serving serving) {
    return switch (serving) {
      case Serving.Fixed fixed -> new ServeDto(fixed.variant().value(), null);
      case Serving.Rollout rollout ->
          new ServeDto(
              null,
              rollout.entries().stream()
                  .map(entry -> new RolloutDto(entry.variant().value(), entry.weight().units()))
                  .toList());
    };
  }

  public Serving toDomain(String field) {
    if (this.variant != null && this.rollout != null) {
      throw FlagwireException.invalid(field, "give either variant or rollout, not both");
    }
    if (this.variant != null) {
      return Serving.fixed(new VariantKey(this.variant));
    }
    List<RolloutDto> entries = Required.items(field + ".rollout", this.rollout);
    return new Serving.Rollout(
        entries.stream()
            .map(
                entry ->
                    new RolloutEntry(
                        new VariantKey(Required.text(field + ".rollout.variant", entry.variant())),
                        new Weight(Required.field(field + ".rollout.weight", entry.weight()))))
            .toList());
  }
}
