package dev.flagwire.domain.leaky;

import dev.flagwire.application.support.SystemTimeSource;
import java.io.File;

public final class LeakyDomainClass {

  private final File file = new File("x");
  private final SystemTimeSource time = new SystemTimeSource();

  public File file() {
    return this.file;
  }

  public SystemTimeSource time() {
    return this.time;
  }
}
