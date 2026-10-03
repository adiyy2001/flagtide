package dev.flagwire.adapter.in.rest.dto;

import dev.flagwire.adapter.in.rest.Required;
import dev.flagwire.domain.flag.EnvironmentSettings;
import dev.flagwire.domain.value.VariantKey;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EnvironmentSettings")
public record EnvironmentSettingsRequest(
    Boolean enabled, String offVariant, List<RuleDto> rules, ServeDto fallthrough) {

  public EnvironmentSettings toDomain() {
    return new EnvironmentSettings(
        Required.field("enabled", this.enabled),
        new VariantKey(Required.text("offVariant", this.offVariant)),
        Required.items("rules", this.rules).stream().map(rule -> rule.toDomain("rules")).toList(),
        Required.field("fallthrough", this.fallthrough).toDomain("fallthrough"));
  }
}
