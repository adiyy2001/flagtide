package dev.flagwire.domain.evaluation;

import dev.flagwire.domain.evaluation.AttributeValue.BooleanValue;
import dev.flagwire.domain.evaluation.AttributeValue.NumberValue;
import dev.flagwire.domain.evaluation.AttributeValue.Scalar;
import dev.flagwire.domain.evaluation.AttributeValue.TextValue;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public final class Evaluator {

  private enum Outcome {
    NOT_APPLICABLE,
    MISS,
    HIT;

    static Outcome of(boolean hit) {
      return hit ? HIT : MISS;
    }
  }

  public EvaluationResult evaluate(
      FlagConfig flag, EvaluationContext context, Map<String, Segment> segments) {
    if (flag.killSwitch()) {
      return this.direct(
          flag, flag.offVariant(), Reason.KILL_SWITCH, OptionalInt.empty(), Optional.empty());
    }
    if (!flag.enabled()) {
      return this.direct(
          flag, flag.offVariant(), Reason.OFF, OptionalInt.empty(), Optional.empty());
    }
    OptionalInt matched =
        IntStream.range(0, flag.rules().size())
            .filter(index -> this.matchesRule(flag.rules().get(index), context, segments))
            .findFirst();
    if (matched.isPresent()) {
      Rule rule = flag.rules().get(matched.getAsInt());
      return this.serve(
          flag, rule.serve(), context, Reason.RULE_MATCH, matched, Optional.of(rule.id()));
    }
    return this.serve(
        flag,
        flag.fallthrough(),
        context,
        Reason.FALLTHROUGH,
        OptionalInt.empty(),
        Optional.empty());
  }

  public EvaluationResult evaluate(FlagConfig flag, EvaluationContext context) {
    return this.evaluate(flag, context, Map.of());
  }

  private EvaluationResult serve(
      FlagConfig flag,
      Serve serve,
      EvaluationContext context,
      Reason reason,
      OptionalInt ruleIndex,
      Optional<String> ruleId) {
    return switch (serve) {
      case Serve.Single single -> this.direct(flag, single.variant(), reason, ruleIndex, ruleId);
      case Serve.Rollout rollout -> {
        int bucket = Bucketing.bucket(flag.key(), flag.salt(), context.key());
        yield this.variantResult(
            flag,
            this.pickWeighted(rollout, bucket),
            reason,
            ruleIndex,
            ruleId,
            OptionalInt.of(bucket));
      }
    };
  }

  private EvaluationResult direct(
      FlagConfig flag,
      String variantKey,
      Reason reason,
      OptionalInt ruleIndex,
      Optional<String> ruleId) {
    return this.variantResult(flag, variantKey, reason, ruleIndex, ruleId, OptionalInt.empty());
  }

  private EvaluationResult variantResult(
      FlagConfig flag,
      String variantKey,
      Reason reason,
      OptionalInt ruleIndex,
      Optional<String> ruleId,
      OptionalInt bucket) {
    Variant variant =
        flag.variants().stream()
            .filter(candidate -> candidate.key().equals(variantKey))
            .findFirst()
            .orElseThrow(() -> new InvalidFlagConfigException("unknown variant " + variantKey));
    return new EvaluationResult(variantKey, variant.value(), reason, ruleIndex, ruleId, bucket);
  }

  private String pickWeighted(Serve.Rollout rollout, int bucket) {
    int running = 0;
    for (WeightedVariant entry : rollout.entries()) {
      running += entry.weight();
      if (running > bucket) {
        return entry.variant();
      }
    }
    throw new InvalidFlagConfigException("rollout weights do not sum to 100000");
  }

  private boolean matchesRule(Rule rule, EvaluationContext context, Map<String, Segment> segments) {
    return rule.conditions().stream()
        .allMatch(condition -> this.matches(condition, context, segments));
  }

  private boolean matches(
      Condition condition, EvaluationContext context, Map<String, Segment> segments) {
    return switch (condition) {
      case Condition.Attribute attribute -> this.matchesAttribute(attribute, context);
      case Condition.SegmentMembership membership ->
          this.matchesSegment(membership, context, segments);
    };
  }

  private boolean matchesSegment(
      Condition.SegmentMembership condition,
      EvaluationContext context,
      Map<String, Segment> segments) {
    return Optional.ofNullable(segments.get(condition.segment()))
        .map(segment -> this.isMember(segment, context) != condition.negate())
        .orElse(false);
  }

  private boolean isMember(Segment segment, EvaluationContext context) {
    if (segment.excluded().contains(context.key())) {
      return false;
    }
    if (segment.included().contains(context.key())) {
      return true;
    }
    return segment.rules().stream()
        .anyMatch(
            group ->
                group.stream().allMatch(condition -> this.matchesAttribute(condition, context)));
  }

  private boolean matchesAttribute(Condition.Attribute condition, EvaluationContext context) {
    Outcome outcome =
        context
            .attribute(condition.attribute())
            .map(AttributeValue::elements)
            .flatMap(
                elements ->
                    elements.stream()
                        .map(element -> this.outcomeOf(condition, element))
                        .max(Comparator.naturalOrder()))
            .orElse(Outcome.NOT_APPLICABLE);
    if (outcome == Outcome.NOT_APPLICABLE) {
      return false;
    }
    return (outcome == Outcome.HIT) != condition.negate();
  }

  private Outcome outcomeOf(Condition.Attribute condition, Scalar element) {
    List<Scalar> values = condition.values();
    return switch (condition.operator()) {
      case EQUALS, IN ->
          Outcome.of(values.stream().anyMatch(value -> this.sameScalar(element, value)));
      case CONTAINS, STARTS_WITH ->
          element instanceof TextValue text
              ? Outcome.of(
                  values.stream()
                      .anyMatch(value -> this.textMatches(condition.operator(), text, value)))
              : Outcome.NOT_APPLICABLE;
      case LT, LTE, GT, GTE ->
          element instanceof NumberValue number
              ? Outcome.of(
                  values.stream()
                      .anyMatch(value -> this.numberMatches(condition.operator(), number, value)))
              : Outcome.NOT_APPLICABLE;
      case SEMVER_EQUALS, SEMVER_LT, SEMVER_LTE, SEMVER_GT, SEMVER_GTE ->
          element instanceof TextValue text
              ? SemanticVersion.parse(text.value())
                  .map(
                      version ->
                          Outcome.of(
                              values.stream()
                                  .anyMatch(
                                      value ->
                                          this.semverMatches(
                                              condition.operator(), version, value))))
                  .orElse(Outcome.NOT_APPLICABLE)
              : Outcome.NOT_APPLICABLE;
    };
  }

  private boolean sameScalar(Scalar left, Scalar right) {
    return switch (left) {
      case TextValue text -> right instanceof TextValue other && text.value().equals(other.value());
      case NumberValue number ->
          right instanceof NumberValue other && number.value() == other.value();
      case BooleanValue flag ->
          right instanceof BooleanValue other && flag.value() == other.value();
    };
  }

  private boolean textMatches(Operator operator, TextValue element, Scalar value) {
    if (!(value instanceof TextValue text)) {
      return false;
    }
    return operator == Operator.CONTAINS
        ? element.value().contains(text.value())
        : element.value().startsWith(text.value());
  }

  private boolean numberMatches(Operator operator, NumberValue element, Scalar value) {
    if (!(value instanceof NumberValue bound)) {
      return false;
    }
    return switch (operator) {
      case LT -> element.value() < bound.value();
      case LTE -> element.value() <= bound.value();
      case GT -> element.value() > bound.value();
      default -> element.value() >= bound.value();
    };
  }

  private boolean semverMatches(Operator operator, SemanticVersion element, Scalar value) {
    if (!(value instanceof TextValue text)) {
      return false;
    }
    return SemanticVersion.parse(text.value())
        .map(bound -> this.satisfiesOrder(operator, element.compareTo(bound)))
        .orElse(false);
  }

  private boolean satisfiesOrder(Operator operator, int order) {
    return switch (operator) {
      case SEMVER_EQUALS -> order == 0;
      case SEMVER_LT -> order < 0;
      case SEMVER_LTE -> order <= 0;
      case SEMVER_GT -> order > 0;
      default -> order >= 0;
    };
  }
}
