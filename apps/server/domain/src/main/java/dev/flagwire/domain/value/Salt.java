package dev.flagwire.domain.value;

import dev.flagwire.domain.error.FlagwireException;
import java.util.regex.Pattern;

public record Salt(String value) {

  private static final Pattern HEX = Pattern.compile("[0-9a-f]{1,64}");

  public Salt {
    if (value == null || !HEX.matcher(value).matches()) {
      throw FlagwireException.invalid("salt", "must be 1 to 64 lowercase hex characters");
    }
  }

  public static Salt of(String value) {
    return new Salt(value);
  }
}
