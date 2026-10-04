package dev.flagtide.domain.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;

final class VectorReader {

  private VectorReader() {}

  static JsonValue json(JsonNode node) {
    if (node.isNull()) {
      return new JsonValue.JsonNull();
    }
    if (node.isBoolean()) {
      return new JsonValue.JsonBoolean(node.booleanValue());
    }
    if (node.isNumber()) {
      return new JsonValue.JsonNumber(node.doubleValue());
    }
    if (node.isTextual()) {
      return new JsonValue.JsonString(node.textValue());
    }
    if (node.isArray()) {
      return new JsonValue.JsonArray(list(node, VectorReader::json));
    }
    Map<String, JsonValue> members = new LinkedHashMap<>();
    node.properties().forEach(field -> members.put(field.getKey(), json(field.getValue())));
    return new JsonValue.JsonObject(members);
  }

  static FlagConfig flag(JsonNode node) {
    return new FlagConfig(
        node.get("key").textValue(),
        FlagType.valueOf(node.get("type").textValue().toUpperCase()),
        node.get("enabled").booleanValue(),
        node.get("killSwitch").booleanValue(),
        node.get("salt").textValue(),
        list(
            node.get("variants"),
            variant -> new Variant(variant.get("key").textValue(), json(variant.get("value")))),
        node.get("offVariant").textValue(),
        list(node.get("rules"), VectorReader::rule),
        serve(node.get("fallthrough")));
  }

  static Segment segment(JsonNode node) {
    return new Segment(
        node.get("key").textValue(),
        Set.copyOf(list(node.get("included"), JsonNode::textValue)),
        Set.copyOf(list(node.get("excluded"), JsonNode::textValue)),
        list(node.get("rules"), group -> list(group, VectorReader::attributeCondition)));
  }

  static EvaluationContext context(JsonNode node) {
    Map<String, JsonValue> attributes = new LinkedHashMap<>();
    node.get("attributes")
        .properties()
        .forEach(field -> attributes.put(field.getKey(), json(field.getValue())));
    return EvaluationContext.of(node.get("key").textValue(), attributes);
  }

  static EvaluationResult result(JsonNode node) {
    return new EvaluationResult(
        node.get("variantKey").textValue(),
        json(node.get("value")),
        Reason.valueOf(node.get("reason").textValue()),
        optionalInt(node.get("ruleIndex")),
        node.get("ruleId").isNull()
            ? Optional.empty()
            : Optional.of(node.get("ruleId").textValue()),
        optionalInt(node.get("bucket")));
  }

  static <T> List<T> list(JsonNode array, Function<JsonNode, T> mapper) {
    return StreamSupport.stream(array.spliterator(), false).map(mapper).toList();
  }

  private static OptionalInt optionalInt(JsonNode node) {
    return node.isNull() ? OptionalInt.empty() : OptionalInt.of(node.intValue());
  }

  private static Rule rule(JsonNode node) {
    List<Condition> conditions = new ArrayList<>();
    node.get("conditions").forEach(condition -> conditions.add(condition(condition)));
    return new Rule(node.get("id").textValue(), conditions, serve(node.get("serve")));
  }

  private static Condition condition(JsonNode node) {
    if (node.has("segment")) {
      return new Condition.SegmentMembership(node.get("segment").textValue(), negate(node));
    }
    return attributeCondition(node);
  }

  private static Condition.Attribute attributeCondition(JsonNode node) {
    return new Condition.Attribute(
        node.get("attribute").textValue(),
        Operator.fromWireName(node.get("operator").textValue()).orElseThrow(),
        list(node.get("values"), VectorReader::scalar),
        negate(node));
  }

  private static boolean negate(JsonNode node) {
    return node.has("negate") && node.get("negate").booleanValue();
  }

  private static AttributeValue.Scalar scalar(JsonNode node) {
    if (node.isTextual()) {
      return new AttributeValue.TextValue(node.textValue());
    }
    if (node.isNumber()) {
      return new AttributeValue.NumberValue(node.doubleValue());
    }
    return new AttributeValue.BooleanValue(node.booleanValue());
  }

  private static Serve serve(JsonNode node) {
    if (node.has("variant")) {
      return new Serve.Single(node.get("variant").textValue());
    }
    return new Serve.Rollout(
        list(
            node.get("rollout"),
            entry ->
                new WeightedVariant(
                    entry.get("variant").textValue(), entry.get("weight").intValue())));
  }
}
