package dev.flagwire.domain.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.evaluation.AttributeValue;
import dev.flagwire.domain.evaluation.AttributeValue.BooleanValue;
import dev.flagwire.domain.evaluation.AttributeValue.NumberValue;
import dev.flagwire.domain.evaluation.AttributeValue.Scalar;
import dev.flagwire.domain.evaluation.AttributeValue.TextValue;
import dev.flagwire.domain.evaluation.Condition;
import dev.flagwire.domain.evaluation.Operator;
import dev.flagwire.domain.value.SegmentKey;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ConditionRulesTest {

  private static Condition.Attribute attribute(Operator operator, Scalar... values) {
    return new Condition.Attribute("attr", operator, List.of(values), false);
  }

  static Stream<Condition.Attribute> validConditions() {
    return Stream.of(
        attribute(Operator.EQUALS, new TextValue("a")),
        attribute(Operator.EQUALS, new NumberValue(1)),
        attribute(Operator.EQUALS, new BooleanValue(true)),
        attribute(Operator.IN, new TextValue("a"), new NumberValue(2), new BooleanValue(false)),
        attribute(Operator.CONTAINS, new TextValue("a"), new TextValue("b")),
        attribute(Operator.STARTS_WITH, new TextValue("a")),
        attribute(Operator.LT, new NumberValue(1)),
        attribute(Operator.LTE, new NumberValue(1)),
        attribute(Operator.GT, new NumberValue(1)),
        attribute(Operator.GTE, new NumberValue(1)),
        attribute(Operator.SEMVER_EQUALS, new TextValue("1.2.3")),
        attribute(Operator.SEMVER_LT, new TextValue("1.2.3-rc.1")),
        attribute(Operator.SEMVER_LTE, new TextValue("1.2.3+build")),
        attribute(Operator.SEMVER_GT, new TextValue("0.0.1")),
        attribute(Operator.SEMVER_GTE, new TextValue("10.20.30")));
  }

  static Stream<Condition.Attribute> invalidConditions() {
    return Stream.of(
        attribute(Operator.EQUALS),
        attribute(Operator.IN),
        attribute(Operator.EQUALS, new TextValue("a"), new TextValue("b")),
        attribute(Operator.EQUALS, new NumberValue(Double.NaN)),
        attribute(Operator.IN, new NumberValue(Double.POSITIVE_INFINITY)),
        attribute(Operator.CONTAINS, new NumberValue(1)),
        attribute(Operator.STARTS_WITH, new BooleanValue(true)),
        attribute(Operator.LT, new TextValue("1")),
        attribute(Operator.GTE, new NumberValue(Double.NaN)),
        attribute(Operator.SEMVER_GT, new TextValue("1.2")),
        attribute(Operator.SEMVER_EQUALS, new NumberValue(1)),
        attribute(Operator.SEMVER_LT, new TextValue("01.2.3")),
        new Condition.Attribute("", Operator.EQUALS, List.of(new TextValue("a")), false),
        new Condition.Attribute(" ", Operator.EQUALS, List.of(new TextValue("a")), false),
        new Condition.Attribute(
            "x".repeat(129), Operator.EQUALS, List.of(new TextValue("a")), false));
  }

  @ParameterizedTest
  @MethodSource("validConditions")
  void acceptsWellFormedAttributeConditions(Condition.Attribute condition) {
    assertThatCode(() -> ConditionRules.validate(condition)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("invalidConditions")
  void rejectsMalformedAttributeConditions(Condition.Attribute condition) {
    assertThatThrownBy(() -> ConditionRules.validate(condition))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void rejectsMoreThanAThousandValues() {
    List<Scalar> many = IntStream.range(0, 1001).<Scalar>mapToObj(i -> new NumberValue(i)).toList();

    assertThatThrownBy(
            () -> ConditionRules.validate(new Condition.Attribute("a", Operator.IN, many, false)))
        .hasMessageContaining("1 to 1000 values");
  }

  @ParameterizedTest
  @EnumSource(Operator.class)
  void everyOperatorHasAValidExample(Operator operator) {
    assertThat(validConditions().map(Condition.Attribute::operator)).contains(operator);
  }

  @Test
  void validatesSegmentKeysAndReportsReferences() {
    Condition membership = new Condition.SegmentMembership("beta", true);

    assertThatCode(() -> ConditionRules.validate(membership)).doesNotThrowAnyException();
    assertThat(ConditionRules.referencedSegment(membership)).contains(new SegmentKey("beta"));
    assertThat(ConditionRules.referencedSegment(attribute(Operator.LT, new NumberValue(1))))
        .isEmpty();
    assertThatThrownBy(
            () -> ConditionRules.validate(new Condition.SegmentMembership("Not A Key", false)))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void usesTheAttributeValueTypeNames() {
    assertThat(new AttributeValue.ListValue(List.of(new TextValue("x"))).elements()).hasSize(1);
  }
}
