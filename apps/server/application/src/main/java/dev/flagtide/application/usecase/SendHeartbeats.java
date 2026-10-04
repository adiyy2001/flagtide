package dev.flagtide.application.usecase;

import dev.flagtide.application.stream.ChangeStreams;

public final class SendHeartbeats {

  private final ChangeStreams streams;

  public SendHeartbeats(ChangeStreams streams) {
    this.streams = streams;
  }

  public void execute() {
    this.streams.heartbeat();
  }
}
