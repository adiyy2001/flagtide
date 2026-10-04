package dev.flagtide.application.leaky;

import dev.flagtide.adapter.out.memory.MemoryAdapters;

public final class LeakyApplicationClass {

  public Class<?> adapters() {
    return MemoryAdapters.class;
  }
}
