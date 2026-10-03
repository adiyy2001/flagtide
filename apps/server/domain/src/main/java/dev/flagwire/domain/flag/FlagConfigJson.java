package dev.flagwire.domain.flag;

import dev.flagwire.domain.condition.ConditionJson;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.evaluation.JsonValue;
import dev.flagwire.domain.evaluation.Rule;
import dev.flagwire.domain.evaluation.Segment;
import dev.flagwire.domain.evaluation.Serve;
import dev.flagwire.domain.evaluation.Variant;
import dev.flagwire.domain.evaluation.WeightedVariant;
import dev.flagwire.domain.json.Json;
import dev.flagwire.domain.json.JsonFields;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class FlagConfigJson {

  private FlagConfigJson() {}

  public static JsonValue toJson(FlagConfig config) {
    return Json.object()
        .text("key", config.key())
        .text("type", config.type().name().toLowerCase(Locale.ROOT))
        .bool("enabled", config.enabled())
        .bool("killSwitch", config.killSwitch())
        .text("salt", config.salt())
        .put("variants", Json.array(config.variants(), FlagConfigJson::variantToJson))
        .text("offVariant", config.offVariant())
        .put("rules", Json.array(config.rules(), FlagConfigJson::ruleToJson))
        .put("fallthrough", serveToJson(config.fallthrough()))
        .build();
  }

  public static FlagConfig fromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("config", json);
    return new FlagConfig(
        fields.text("key"),
        typeFromName(fields.text("type")),
        fields.bool("enabled"),
        fields.bool("killSwitch"),
        fields.text("salt"),
        fields.array("variants").stream().map(FlagConfigJson::variantFromJson).toList(),
        fields.text("offVariant"),
        fields.array("rules").stream().map(FlagConfigJson::ruleFromJson).toList(),
        serveFromJson("config.fallthrough", fields.require("fallthrough")));
  }

  public static JsonValue segmentToJson(Segment segment) {
    return Json.object()
        .text("key", segment.key())
        .put("included", sortedTexts(segment.included()))
        .put("excluded", sortedTexts(segment.excluded()))
        .put(
            "rules", Json.array(segment.rules(), group -> Json.array(group, ConditionJson::toJson)))
        .build();
  }

  public static Segment segmentFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("segment", json);
    return new Segment(
        fields.text("key"),
        texts(fields.array("included")),
        texts(fields.array("excluded")),
        fields.array("rules").stream().map(FlagConfigJson::groupFromJson).toList());
  }

  private static JsonValue variantToJson(Variant variant) {
    return Json.object().text("key", variant.key()).put("value", variant.value()).build();
  }

  private static Variant variantFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("variant", json);
    return new Variant(fields.text("key"), fields.find("value").orElse(new JsonValue.JsonNull()));
  }

  private static JsonValue ruleToJson(Rule rule) {
    return Json.object()
        .text("id", rule.id())
        .put("conditions", Json.array(rule.conditions(), ConditionJson::toJson))
        .put("serve", serveToJson(rule.serve()))
        .build();
  }

  private static Rule ruleFromJson(JsonValue json) {
    JsonFields fields = JsonFields.of("rule", json);
    List<Condition> conditions =
        fields.array("conditions").stream()
            .map(condition -> ConditionJson.fromJson("rule.conditions", condition))
            .toList();
    return new Rule(
        fields.text("id"), conditions, serveFromJson("rule.serve", fields.require("serve")));
  }

  private static JsonValue serveToJson(Serve serve) {
    return switch (serve) {
      case Serve.Single single -> Json.object().text("variant", single.variant()).build();
      case Serve.Rollout rollout ->
          Json.object()
              .put(
                  "rollout",
                  Json.array(
                      rollout.entries(),
                      entry ->
                          Json.object()
                              .text("variant", entry.variant())
                              .number("weight", entry.weight())
                              .build()))
              .build();
    };
  }

  private static Serve serveFromJson(String context, JsonValue json) {
    JsonFields fields = JsonFields.of(context, json);
    if (fields.find("variant").isPresent()) {
      return new Serve.Single(fields.text("variant"));
    }
    return new Serve.Rollout(
        fields.array("rollout").stream()
            .map(
                entry -> {
                  JsonFields entryFields = JsonFields.of(context + ".rollout", entry);
                  return new WeightedVariant(
                      entryFields.text("variant"), (int) entryFields.integer("weight"));
                })
            .toList());
  }

  private static JsonValue sortedTexts(Set<String> values) {
    return new JsonValue.JsonArray(values.stream().sorted().map(Json::text).toList());
  }

  private static Set<String> texts(List<JsonValue> values) {
    return values.stream()
        .map(value -> Json.requireText("segment.members", value))
        .collect(Collectors.toSet());
  }

  private static List<Condition.Attribute> groupFromJson(JsonValue json) {
    return Json.requireArray("segment.rules", json).stream()
        .map(item -> ConditionJson.attributeFromJson("segment.rules", item))
        .toList();
  }

  private static FlagType typeFromName(String name) {
    try {
      return FlagType.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException unknown) {
      throw FlagwireException.invalid("config.type", "unknown flag type " + name);
    }
  }
}
