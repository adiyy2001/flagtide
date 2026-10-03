package dev.flagwire.domain.error;

import java.util.List;

public final class FlagwireException extends RuntimeException {

  private final transient FlagwireError error;

  public FlagwireException(FlagwireError error) {
    super(error.message());
    this.error = error;
  }

  public FlagwireError error() {
    return this.error;
  }

  public static FlagwireException notFound(String entity, String id) {
    return new FlagwireException(new FlagwireError.NotFound(entity, id));
  }

  public static FlagwireException conflict(String message) {
    return new FlagwireException(new FlagwireError.Conflict(message));
  }

  public static FlagwireException invalid(String field, String message) {
    return new FlagwireException(
        new FlagwireError.ValidationFailed(List.of(new Violation(field, message))));
  }

  public static FlagwireException invalid(List<Violation> violations) {
    return new FlagwireException(new FlagwireError.ValidationFailed(violations));
  }

  public static FlagwireException unauthorized(String message) {
    return new FlagwireException(new FlagwireError.Unauthorized(message));
  }

  public static FlagwireException forbidden(String message) {
    return new FlagwireException(new FlagwireError.Forbidden(message));
  }
}
