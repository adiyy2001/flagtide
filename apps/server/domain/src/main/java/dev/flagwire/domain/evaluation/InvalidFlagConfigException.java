package dev.flagwire.domain.evaluation;

public class InvalidFlagConfigException extends RuntimeException {

  public InvalidFlagConfigException(String message) {
    super(message);
  }
}
