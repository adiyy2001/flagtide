package dev.flagtide.domain.evaluation;

import java.util.List;

public sealed interface Condition {

  record Attribute(
      String attribute, Operator operator, List<AttributeValue.Scalar> values, boolean negate)
      implements Condition {
    public Attribute {
      values = List.copyOf(values);
    }
  }

  record SegmentMembership(String segment, boolean negate) implements Condition {}
}
