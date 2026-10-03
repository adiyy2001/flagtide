package dev.flagwire.application.testing;

import dev.flagwire.application.port.out.TimeSource;
import java.time.Duration;
import java.time.ZonedDateTime;

public final class MutableTimeSource implements TimeSource {

  private volatile ZonedDateTime current;

  public MutableTimeSource(ZonedDateTime start) {
    this.current = start;
  }

  @Override
  public ZonedDateTime now() {
    return this.current;
  }

  public void advance(Duration duration) {
    this.current = this.current.plus(duration);
  }

  public void set(ZonedDateTime value) {
    this.current = value;
  }
}
