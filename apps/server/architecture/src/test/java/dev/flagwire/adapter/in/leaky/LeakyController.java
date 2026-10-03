package dev.flagwire.adapter.in.leaky;

import dev.flagwire.application.port.out.TimeSource;

public final class LeakyController {

  public Class<?> port() {
    return TimeSource.class;
  }
}
