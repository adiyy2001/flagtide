package dev.flagwire.domain.value;

import dev.flagwire.domain.error.FlagwireException;
import java.util.regex.Pattern;

final class Slugs {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

  private Slugs() {}

  static String require(String field, String value) {
    if (value == null || !SLUG.matcher(value).matches()) {
      throw FlagwireException.invalid(
          field, "must match [a-z0-9][a-z0-9_-]{0,63} but was '" + value + "'");
    }
    return value;
  }
}
