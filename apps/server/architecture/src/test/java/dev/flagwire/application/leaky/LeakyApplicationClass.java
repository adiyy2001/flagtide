package dev.flagwire.application.leaky;

import dev.flagwire.adapter.memory.MemoryAdapters;

public final class LeakyApplicationClass {

  public Class<?> adapters() {
    return MemoryAdapters.class;
  }
}
