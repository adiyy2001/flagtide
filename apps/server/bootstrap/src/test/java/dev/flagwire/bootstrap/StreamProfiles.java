package dev.flagwire.bootstrap;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.HashMap;
import java.util.Map;

final class StreamProfiles {

  static final String ALLOWED_ORIGIN = "http://allowed.test";

  private StreamProfiles() {}

  private static Map<String, String> small() {
    Map<String, String> overrides = new HashMap<>();
    overrides.put("flagwire.change-log.retention", "5");
    overrides.put("flagwire.propagation.ring-capacity", "5");
    overrides.put("flagwire.propagation.heartbeat-interval", "PT0.3S");
    overrides.put("flagwire.stream.hello-timeout", "PT0.6S");
    overrides.put("flagwire.stream.max-pending-frames", "8");
    overrides.put("flagwire.stream.allowed-origins", ALLOWED_ORIGIN);
    overrides.put("quarkus.http.cors.origins", ALLOWED_ORIGIN);
    return overrides;
  }

  public static class Memory implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      Map<String, String> overrides = small();
      overrides.put("flagwire.persistence", "memory");
      overrides.put("quarkus.datasource.devservices.enabled", "false");
      overrides.put("quarkus.datasource.active", "false");
      overrides.put("quarkus.flyway.active", "false");
      return overrides;
    }

    @Override
    public String getConfigProfile() {
      return "test";
    }
  }

  public static class Postgres implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
      return small();
    }

    @Override
    public String getConfigProfile() {
      return "test";
    }
  }
}
