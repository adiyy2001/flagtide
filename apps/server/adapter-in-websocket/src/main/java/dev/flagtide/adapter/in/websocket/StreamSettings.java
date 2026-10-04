package dev.flagtide.adapter.in.websocket;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "flagtide.stream")
public interface StreamSettings {

  Duration helloTimeout();

  int maxPendingFrames();

  int maxWaitingForHello();

  Optional<List<String>> allowedOrigins();
}
