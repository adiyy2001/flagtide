package dev.flagtide.domain.flag;

import dev.flagtide.domain.condition.ConditionJson;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.FlagType;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.json.Json;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import dev.flagtide.domain.value.RuleId;
import dev.flagtide.domain.value.Salt;
import dev.flagtide.domain.value.VariantKey;
import dev.flagtide.domain.value.Weight;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class FlagDocument {

  private FlagDocument() {}

  public static JsonValue toJson(Flag flag) {
    Map<String, JsonValue> environments = new HashMap<>();
    flag.environments()
        .forEach((key, config) -> environments.put(key.value(), environmentToJson(config)));
    return Json.object()
        .text("key", flag.key().value())
        .text("description", flag.description())
        .text("type", flag.type().name().toLowerCase(Locale.ROOT))
        .bool("archived", flag.archived())
        .number("revision", flag.revision().value())
        .text("createdAt", flag.createdAt().toString())
        .text("updatedAt", flag.updatedAt().toString())
        .put("variants", Json.array(flag.variants(), FlagDocument::variantToJson))
        .put("environments", new JsonValue.JsonObject(environments))
        .build();
  }

  public static JsonValue definitionToJson(Flag flag) {
    return Json.object()
        .text("key", flag.key().value())
        .text("description", flag.description())
        .text("type", flag.type().name().toLowerCase(Locale.ROOT))
        .bool("archived", flag.archived())
        .put("variants", Json.array(flag.variants(), FlagDocument::variantToJson))
        .build();
  }

  public static JsonValue environmentToJson(FlagEnvironmentConfig config) {
    return Json.object()
        .bool("enabled", config.enabled())
        .bool("killSwitch", config.killSwitch())
        .text("offVariant", config.offVariant().value())
        .text("salt", config.salt().value())
        .put("rules", Json.array(config.rules(), FlagDocument::ruleToJson))
        .put("fallthrough", servingToJson(config.fallthrough()))
        .build();
  }

  public static Flag fromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("flag", json);
    Map<EnvironmentKey, FlagEnvironmentConfig> environments = new HashMap<>();
    fields
        .object("environments")
        .members()
        .forEach(
            (name, value) ->
                environments.put(
                    new EnvironmentKey(name),
                    environmentFromJson("flag.environments." + name, value)));
    return new Flag(
        new FlagKey(fields.text("key")),
        fields.text("description"),
        typeFromName(fields.text("type")),
        fields.array("variants").stream().map(FlagDocument::variantFromJson).toList(),
        environments,
        fields.bool("archived"),
        new Revision(fields.integer("revision")),
        ZonedDateTime.parse(fields.text("createdAt")),
        ZonedDateTime.parse(fields.text("updatedAt")));
  }

  private static JsonValue variantToJson(FlagVariant variant) {
    return Json.object().text("key", variant.key().value()).put("value", variant.value()).build();
  }

  private static FlagVariant variantFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("variant", json);
    return new FlagVariant(new VariantKey(fields.text("key")), fields.require("value"));
  }

  private static JsonValue ruleToJson(TargetingRule rule) {
    return Json.object()
        .text("id", rule.id().value())
        .number("order", rule.order())
        .put("conditions", Json.array(rule.conditions(), ConditionJson::toJson))
        .put("serve", servingToJson(rule.serving()))
        .build();
  }

  private static JsonValue servingToJson(Serving serving) {
    return switch (serving) {
      case Serving.Fixed fixed -> Json.object().text("variant", fixed.variant().value()).build();
      case Serving.Rollout rollout ->
          Json.object()
              .put(
                  "rollout",
                  Json.array(
                      rollout.entries(),
                      entry ->
                          Json.object()
                              .text("variant", entry.variant().value())
                              .number("weight", entry.weight().units())
                              .build()))
              .build();
    };
  }

  private static FlagEnvironmentConfig environmentFromJson(String context, JsonValue json) {
    JsonFields fields = JsonFields.of(context, json);
    return new FlagEnvironmentConfig(
        fields.bool("enabled"),
        fields.bool("killSwitch"),
        new VariantKey(fields.text("offVariant")),
        fields.array("rules").stream().map(FlagDocument::ruleFromJson).toList(),
        servingFromJson(context + ".fallthrough", fields.require("fallthrough")),
        new Salt(fields.text("salt")));
  }

  private static TargetingRule ruleFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("rule", json);
    return new TargetingRule(
        new RuleId(fields.text("id")),
        (int) fields.integer("order"),
        fields.array("conditions").stream()
            .map(condition -> ConditionJson.fromJson("rule.conditions", condition))
            .toList(),
        servingFromJson("rule.serve", fields.require("serve")));
  }

  private static Serving servingFromJson(String context, JsonValue json) {
    JsonFields fields = JsonFields.of(context, json);
    if (fields.find("variant").isPresent()) {
      return Serving.fixed(new VariantKey(fields.text("variant")));
    }
    return new Serving.Rollout(
        fields.array("rollout").stream()
            .map(
                entry -> {
                  JsonFields entryFields = JsonFields.of(context + ".rollout", entry);
                  return new RolloutEntry(
                      new VariantKey(entryFields.text("variant")),
                      new Weight((int) entryFields.integer("weight")));
                })
            .toList());
  }

  private static FlagType typeFromName(String name) {
    try {
      return FlagType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw FlagtideException.invalid("flag.type", "unknown flag type " + name);
    }
  }
}
