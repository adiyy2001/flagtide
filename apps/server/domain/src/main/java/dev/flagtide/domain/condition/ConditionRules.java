package dev.flagtide.domain.condition;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.evaluation.AttributeValue;
import dev.flagtide.domain.evaluation.Condition;
import dev.flagtide.domain.evaluation.Operator;
import dev.flagtide.domain.evaluation.SemanticVersion;
import dev.flagtide.domain.value.SegmentKey;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ConditionRules {

  private static final int MAX_VALUES = 1000;
  private static final int MAX_ATTRIBUTE_LENGTH = 128;
  private static final Set<Operator> SINGLE_VALUE =
      EnumSet.of(
          Operator.EQUALS,
          Operator.LT,
          Operator.LTE,
          Operator.GT,
          Operator.GTE,
          Operator.SEMVER_EQUALS,
          Operator.SEMVER_LT,
          Operator.SEMVER_LTE,
          Operator.SEMVER_GT,
          Operator.SEMVER_GTE);

  private ConditionRules() {}

  public static void validate(Condition condition) {
    switch (condition) {
      case Condition.Attribute attribute -> validateAttribute(attribute);
      case Condition.SegmentMembership membership -> new SegmentKey(membership.segment());
    }
  }

  public static Optional<SegmentKey> referencedSegment(Condition condition) {
    return condition instanceof Condition.SegmentMembership membership
        ? Optional.of(new SegmentKey(membership.segment()))
        : Optional.empty();
  }

  private static void validateAttribute(Condition.Attribute condition) {
    if (condition.attribute().isBlank() || condition.attribute().length() > MAX_ATTRIBUTE_LENGTH) {
      throw FlagtideException.invalid("attribute", "must be 1 to 128 characters and not blank");
    }
    List<AttributeValue.Scalar> values = condition.values();
    if (values.isEmpty() || values.size() > MAX_VALUES) {
      throw FlagtideException.invalid("values", "must hold 1 to 1000 values");
    }
    if (SINGLE_VALUE.contains(condition.operator()) && values.size() != 1) {
      throw FlagtideException.invalid(
          "values", condition.operator().wireName() + " takes exactly one value");
    }
    values.forEach(value -> validateValue(condition.operator(), value));
  }

  private static void validateValue(Operator operator, AttributeValue.Scalar value) {
    switch (operator) {
      case EQUALS, IN -> requireFinite(operator, value);
      case CONTAINS, STARTS_WITH -> requireText(operator, value);
      case LT, LTE, GT, GTE -> requireNumber(operator, value);
      default -> requireSemanticVersion(operator, value);
    }
  }

  private static void requireFinite(Operator operator, AttributeValue.Scalar value) {
    if (value instanceof AttributeValue.NumberValue number && !Double.isFinite(number.value())) {
      throw FlagtideException.invalid("values", operator.wireName() + " needs finite numbers");
    }
  }

  private static void requireText(Operator operator, AttributeValue.Scalar value) {
    if (!(value instanceof AttributeValue.TextValue)) {
      throw FlagtideException.invalid("values", operator.wireName() + " needs strings");
    }
  }

  private static void requireNumber(Operator operator, AttributeValue.Scalar value) {
    if (!(value instanceof AttributeValue.NumberValue number) || !Double.isFinite(number.value())) {
      throw FlagtideException.invalid("values", operator.wireName() + " needs finite numbers");
    }
  }

  private static void requireSemanticVersion(Operator operator, AttributeValue.Scalar value) {
    boolean valid =
        value instanceof AttributeValue.TextValue text
            && SemanticVersion.parse(text.value()).isPresent();
    if (!valid) {
      throw FlagtideException.invalid(
          "values", operator.wireName() + " needs valid semantic versions");
    }
  }
}
