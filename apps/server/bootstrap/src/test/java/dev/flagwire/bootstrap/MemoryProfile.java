package dev.flagwire.bootstrap;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class MemoryProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "flagwire.persistence", "memory",
        "quarkus.datasource.devservices.enabled", "false",
        "quarkus.datasource.active", "false",
        "quarkus.flyway.active", "false");
  }

  @Override
  public String getConfigProfile() {
    return "test";
  }
}
