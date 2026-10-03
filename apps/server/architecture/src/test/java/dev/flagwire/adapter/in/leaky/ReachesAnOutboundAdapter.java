package dev.flagwire.adapter.in.leaky;

import dev.flagwire.adapter.out.memory.MemoryAdapters;

public final class ReachesAnOutboundAdapter {

  public Class<?> adapters() {
    return MemoryAdapters.class;
  }
}
