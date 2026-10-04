package dev.flagtide.domain.error;

import java.util.List;

public sealed interface FlagtideError {

  String message();

  record NotFound(String entity, String id) implements FlagtideError {
    @Override
    public String message() {
      return this.entity + " " + this.id + " does not exist";
    }
  }

  record Conflict(String message) implements FlagtideError {}

  record ValidationFailed(List<Violation> violations) implements FlagtideError {
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

  record Unauthorized(String message) implements FlagtideError {}

  record Forbidden(String message) implements FlagtideError {}
}
