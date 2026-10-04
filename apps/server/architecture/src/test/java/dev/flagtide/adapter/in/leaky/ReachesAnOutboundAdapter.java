package dev.flagtide.adapter.in.leaky;

import dev.flagtide.adapter.out.memory.MemoryAdapters;

public final class ReachesAnOutboundAdapter {

  public Class<?> adapters() {
    return MemoryAdapters.class;
  }
}
