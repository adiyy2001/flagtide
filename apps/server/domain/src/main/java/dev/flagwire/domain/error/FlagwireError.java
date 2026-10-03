package dev.flagwire.domain.error;

import java.util.List;

public sealed interface FlagwireError {

  String message();

  record NotFound(String entity, String id) implements FlagwireError {
    @Override
    public String message() {
      return this.entity + " " + this.id + " does not exist";
    }
  }

  record Conflict(String message) implements FlagwireError {}

  record ValidationFailed(List<Violation> violations) implements FlagwireError {
    public ValidationFailed {
      violations = List.copyOf(violations);
    }

    @Override
    public String message() {
      return this.violations.stream()
          .map(violation -> violation.field() + ": " + violation.message())
          .reduce((first, second) -> first + "; " + second)
          .orElse("validation failed");
    }
  }

  record Unauthorized(String message) implements FlagwireError {}

  record Forbidden(String message) implements FlagwireError {}
}
