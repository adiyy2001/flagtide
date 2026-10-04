package dev.flagtide.adapter.in.rest.dto;

import dev.flagtide.domain.flag.Flag;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Flag")
public record FlagResponse(
    String key,
    String description,
    String type,
    boolean archived,
    long revision,
    String createdAt,
    String updatedAt,
    List<VariantDto> variants,
    Map<String, EnvironmentConfigResponse> environments) {

  public static FlagResponse from(Flag flag) {
    Map<String, EnvironmentConfigResponse> environments = new TreeMap<>();
    flag.environments()
        .forEach(
            (key, config) -> environments.put(key.value(), EnvironmentConfigResponse.from(config)));
    return new FlagResponse(
        flag.key().value(),
        flag.description(),
        flag.type().name().toLowerCase(java.util.Locale.ROOT),
        flag.archived(),
        flag.revision().value(),
        flag.createdAt().toString(),
        flag.updatedAt().toString(),
        flag.variants().stream().map(VariantDto::from).toList(),
        environments);
  }
}
