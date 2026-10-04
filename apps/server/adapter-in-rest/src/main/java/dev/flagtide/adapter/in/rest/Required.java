package dev.flagtide.adapter.in.rest;

import dev.flagtide.domain.error.FlagtideException;
import java.util.List;

public final class Required {

  private Required() {}

  public static <T> T field(String name, T value) {
    if (value == null) {
      throw FlagtideException.invalid(name, "is required");
    }
    return value;
  }

  public static <T> List<T> items(String name, List<T> value) {
    List<T> items = field(name, value);
    items.forEach(item -> field(name, item));
    return items;
  }

  public static String text(String name, String value) {
    return field(name, value);
  }

  public static String textOr(String value, String fallback) {
    return value == null ? fallback : value;
  }
}
