package dev.flagwire.bootstrap;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "flagwire.seed")
public interface SeedConfig {

  @WithDefault("false")
  boolean enabled();

  @WithDefault("demo")
  String project();

  @WithDefault("Demo")
  String name();

  Map<String, Keys> keys();

  interface Keys {

    Optional<String> admin();

    Optional<String> sdk();
  }
}
