package dev.flagtide.domain.error;

import java.util.List;

public final class FlagtideException extends RuntimeException {

  private final transient FlagtideError error;

  public FlagtideException(FlagtideError error) {
    super(error.message());
    this.error = error;
  }

  public FlagtideError error() {
    return this.error;
  }

  public static FlagtideException notFound(String entity, String id) {
    return new FlagtideException(new FlagtideError.NotFound(entity, id));
  }

  public static FlagtideException conflict(String message) {
    return new FlagtideException(new FlagtideError.Conflict(message));
  }

  public static FlagtideException invalid(String field, String message) {
    return new FlagtideException(
        new FlagtideError.ValidationFailed(List.of(new Violation(field, message))));
  }

  public static FlagtideException invalid(List<Violation> violations) {
    return new FlagtideException(new FlagtideError.ValidationFailed(violations));
  }

  public static FlagtideException unauthorized(String message) {
    return new FlagtideException(new FlagtideError.Unauthorized(message));
  }

  public static FlagtideException forbidden(String message) {
    return new FlagtideException(new FlagtideError.Forbidden(message));
  }
}
