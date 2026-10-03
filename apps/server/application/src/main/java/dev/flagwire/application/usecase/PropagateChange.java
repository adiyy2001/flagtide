package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.application.stream.ChangeStreams;

public final class PropagateChange {

  private final ChangeStreams streams;

  public PropagateChange(ChangeStreams streams) {
    this.streams = streams;
  }

  public void execute(ChangeNotification notification) {
    this.streams.propagate(notification);
  }

  public void resync() {
    this.streams.resync();
  }
}
