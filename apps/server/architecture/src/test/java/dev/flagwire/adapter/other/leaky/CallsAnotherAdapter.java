package dev.flagwire.adapter.other.leaky;

import dev.flagwire.adapter.memory.MemoryAdapters;

public final class CallsAnotherAdapter {

  public Class<?> adapters() {
    return MemoryAdapters.class;
  }
}
