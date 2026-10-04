package dev.flagtide.application.port.out;

import java.time.ZonedDateTime;

public interface TimeSource {

  ZonedDateTime now();
}
