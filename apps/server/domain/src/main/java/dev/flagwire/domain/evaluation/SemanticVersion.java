package dev.flagwire.domain.evaluation;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SemanticVersion(String major, String minor, String patch, List<String> prerelease)
    implements Comparable<SemanticVersion> {

  private static final String NUMBER = "(0|[1-9][0-9]*)";
  private static final String IDENTIFIER = "(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)";
  private static final Pattern PATTERN =
      Pattern.compile(
          NUMBER
              + "\\."
              + NUMBER
              + "\\."
              + NUMBER
              + "(?:-("
              + IDENTIFIER
              + "(?:\\."
              + IDENTIFIER
              + ")*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
  private static final Pattern NUMERIC_IDENTIFIER = Pattern.compile("[0-9]+");

  public SemanticVersion {
    prerelease = List.copyOf(prerelease);
  }

  public static Optional<SemanticVersion> parse(String text) {
    Matcher matcher = PATTERN.matcher(text);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    List<String> prerelease =
        Optional.ofNullable(matcher.group(4))
            .map(value -> List.of(value.split("\\.", -1)))
            .orElse(List.of());
    return Optional.of(
        new SemanticVersion(matcher.group(1), matcher.group(2), matcher.group(3), prerelease));
  }

  @Override
  public int compareTo(SemanticVersion other) {
    int core = compareDigitStrings(this.major, other.major);
    if (core == 0) {
      core = compareDigitStrings(this.minor, other.minor);
    }
    if (core == 0) {
      core = compareDigitStrings(this.patch, other.patch);
    }
    return core != 0 ? core : comparePrerelease(this.prerelease, other.prerelease);
  }

  private static int comparePrerelease(List<String> left, List<String> right) {
    if (left.isEmpty() || right.isEmpty()) {
      return Boolean.compare(left.isEmpty(), right.isEmpty());
    }
    int shared = Math.min(left.size(), right.size());
    for (int index = 0; index < shared; index++) {
      int result = compareIdentifiers(left.get(index), right.get(index));
      if (result != 0) {
        return result;
      }
    }
    return Integer.compare(left.size(), right.size());
  }

  private static int compareIdentifiers(String left, String right) {
    boolean leftNumeric = NUMERIC_IDENTIFIER.matcher(left).matches();
    boolean rightNumeric = NUMERIC_IDENTIFIER.matcher(right).matches();
    if (leftNumeric && rightNumeric) {
      return compareDigitStrings(left, right);
    }
    if (leftNumeric) {
      return -1;
    }
    if (rightNumeric) {
      return 1;
    }
    return Integer.signum(left.compareTo(right));
  }

  private static int compareDigitStrings(String left, String right) {
    if (left.length() != right.length()) {
      return Integer.compare(left.length(), right.length());
    }
    return Integer.signum(left.compareTo(right));
  }
}
