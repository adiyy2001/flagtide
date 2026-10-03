package dev.flagwire.application.support;

import dev.flagwire.application.port.out.TimeSource;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class SystemTimeSource implements TimeSource {

  @Override
  public ZonedDateTime now() {
    return ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
  }
}
