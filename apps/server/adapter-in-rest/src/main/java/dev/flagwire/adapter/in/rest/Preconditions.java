package dev.flagwire.adapter.in.rest;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.Revision;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Preconditions {

  private static final Pattern ENTITY_TAG = Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

  private Preconditions() {}

  static Optional<Revision> expectedRevision(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank() || ifMatch.strip().equals("*")) {
      return Optional.empty();
    }
    Matcher matcher = ENTITY_TAG.matcher(ifMatch.strip());
    if (!matcher.matches()) {
      throw FlagwireException.invalid("If-Match", "must be an entity tag such as \"3\"");
    }
    return Optional.of(Revision.of(Long.parseLong(matcher.group(1))));
  }

  static String tag(Revision revision) {
    return Long.toString(revision.value());
  }

  static String tag(long version) {
    return Long.toString(version);
  }

  static boolean matches(String ifNoneMatch, String tag) {
    if (ifNoneMatch == null) {
      return false;
    }
    String quoted = "\"" + tag + "\"";
    for (String candidate : ifNoneMatch.split(",")) {
      String trimmed = candidate.strip();
      if (trimmed.equals("*") || trimmed.equals(quoted) || trimmed.equals("W/" + quoted)) {
        return true;
      }
    }
    return false;
  }
}
