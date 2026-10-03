package dev.flagwire.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SystemTimeSourceTest {

  @Test
  void returnsTheCurrentTimeInUtc() {
    ZonedDateTime before = ZonedDateTime.now().minus(Duration.ofSeconds(1));

    ZonedDateTime now = new SystemTimeSource().now();

    assertThat(now).isAfter(before).isBefore(ZonedDateTime.now().plus(Duration.ofSeconds(1)));
    assertThat(now.getOffset().getTotalSeconds()).isZero();
  }
}
