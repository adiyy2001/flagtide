package dev.flagwire.domain.evaluation;

import java.util.Arrays;
import java.util.Optional;

public enum Operator {
  EQUALS("equals"),
  IN("in"),
  CONTAINS("contains"),
  STARTS_WITH("startsWith"),
  LT("lt"),
  LTE("lte"),
  GT("gt"),
  GTE("gte"),
  SEMVER_EQUALS("semverEquals"),
  SEMVER_LT("semverLt"),
  SEMVER_LTE("semverLte"),
  SEMVER_GT("semverGt"),
  SEMVER_GTE("semverGte");

  private final String wireName;

  Operator(String wireName) {
    this.wireName = wireName;
  }

  public String wireName() {
    return this.wireName;
  }

  public static Optional<Operator> fromWireName(String wireName) {
    return Arrays.stream(values())
        .filter(operator -> operator.wireName.equals(wireName))
        .findFirst();
  }
}
