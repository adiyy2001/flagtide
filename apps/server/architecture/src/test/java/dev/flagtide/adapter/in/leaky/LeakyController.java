package dev.flagtide.adapter.in.leaky;

import dev.flagtide.application.port.out.TimeSource;

public final class LeakyController {

  public Class<?> port() {
    return TimeSource.class;
  }
}
