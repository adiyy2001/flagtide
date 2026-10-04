package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.domain.flag.FlagEnvironmentConfig;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EnvironmentConfig")
public record EnvironmentConfigResponse(
    boolean enabled,
    boolean killSwitch,
    String offVariant,
    String salt,
    List<RuleDto> rules,
    ServeDto fallthrough) {

  public static EnvironmentConfigResponse from(FlagEnvironmentConfig config) {
    return new EnvironmentConfigResponse(
        config.enabled(),
        config.killSwitch(),
        config.offVariant().value(),
        config.salt().value(),
        config.rules().stream().map(RuleDto::from).toList(),
        ServeDto.from(config.fallthrough()));
  }
}
