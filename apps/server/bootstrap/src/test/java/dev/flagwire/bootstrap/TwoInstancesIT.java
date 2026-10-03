package dev.flagwire.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class TwoInstancesIT {

  private static final String PROJECT = "demo";
  private static final String ADMIN_KEY = "fwa_demo_dev_admin_000000000000";
  private static final String SDK_KEY = "fws_demo_dev_sdk_0000000000000";
  private static final Duration PATIENCE = Duration.ofSeconds(10);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final AtomicInteger FLIPS = new AtomicInteger();

  private static PostgreSQLContainer postgres;
  private static ServerProcess serverA;
  private static ServerProcess serverB;

  @BeforeAll
  static void startStack() throws Exception {
    postgres = new PostgreSQLContainer("postgres:18.6");
    postgres.start();
    Path jar = Path.of(System.getProperty("flagwire.server.jar"));
    Map<String, String> shared =
        Map.of(
            "QUARKUS_DATASOURCE_JDBC_URL", postgres.getJdbcUrl(),
            "QUARKUS_DATASOURCE_USERNAME", postgres.getUsername(),
            "QUARKUS_DATASOURCE_PASSWORD", postgres.getPassword());
    Map<String, String> seeding = new HashMap<>(shared);
    seeding.put("FLAGWIRE_SEED_ENABLED", "true");
    seeding.put("FLAGWIRE_SEED_PROJECT", PROJECT);
    seeding.put("FLAGWIRE_SEED_NAME", "Demo");
    seeding.put("FLAGWIRE_SEED_KEYS_DEV_ADMIN", ADMIN_KEY);
    seeding.put("FLAGWIRE_SEED_KEYS_DEV_SDK", SDK_KEY);
    serverA = ServerProcess.start(jar, "server-a", seeding);
    serverB = ServerProcess.start(jar, "server-b", shared);
    createFlag("shared");
  }

  @AfterAll
  static void stopStack() {
    if (serverB != null) {
      serverB.close();
    }
    if (serverA != null) {
      serverA.close();
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @Test
  void aChangeMadeThroughOneInstanceReachesClientsOfBoth() throws Exception {
    try (Client onA = Client.connect(serverA);
        Client onB = Client.connect(serverB)) {
      long known = onA.awaitSnapshotVersion();
      onB.awaitSnapshotVersion();

      flip(serverA);

      assertThat(onA.awaitDeltasTo(known + 1)).isTrue();
      assertThat(onB.awaitDeltasTo(known + 1)).isTrue();
    }
  }

  @Test
  void aChangeMadeThroughTheOtherInstanceReachesClientsOfBoth() throws Exception {
    try (Client onA = Client.connect(serverA);
        Client onB = Client.connect(serverB)) {
      long known = onA.awaitSnapshotVersion();
      onB.awaitSnapshotVersion();

      flip(serverB);

      assertThat(onA.awaitDeltasTo(known + 1)).isTrue();
      assertThat(onB.awaitDeltasTo(known + 1)).isTrue();
    }
  }

  @Test
  void aClientThatReconnectsWithAnOldVersionGetsExactlyTheMissedDeltas() throws Exception {
    long known;
    try (Client first = Client.connect(serverB)) {
      known = first.awaitSnapshotVersion();
    }
    flip(serverA);
    flip(serverA);
    flip(serverA);

    try (Client second = Client.connect(serverB, known)) {
      assertThat(second.awaitDeltasTo(known + 3)).isTrue();
      assertThat(second.deltaVersions()).containsExactly(known + 1, known + 2, known + 3);
    }
  }

  @Test
  void aWriteStillReachesAnInstanceAfterItsListenerConnectionWasKilled() throws Exception {
    try (Client onB = Client.connect(serverB)) {
      long known = onB.awaitSnapshotVersion();
      String listener = "flagwire-listener-" + serverB.name();
      terminateBackend(listener);
      awaitBackend(listener);

      flip(serverA);

      assertThat(onB.awaitDeltasTo(known + 1)).isTrue();
    }
  }

  private static void createFlag(String key) throws Exception {
    String body =
        """
        {"key":"%s","description":"two instance test","type":"boolean",
         "variants":[{"key":"on","value":true},{"key":"off","value":false}],
         "offVariant":"off","fallthroughVariant":"on"}
        """
            .formatted(key);
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(serverA.uri("/api/v1/projects/" + PROJECT + "/flags"))
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
  }

  private static void flip(ServerProcess instance) throws Exception {
    boolean enabled = FLIPS.incrementAndGet() % 2 == 1;
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(
                    instance.uri(
                        "/api/v1/projects/" + PROJECT + "/flags/shared/environments/dev/enabled"))
                .header("Authorization", "Bearer " + ADMIN_KEY)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"enabled\":" + enabled + "}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private static void terminateBackend(String applicationName) throws Exception {
    try (Connection connection = adminConnection();
        PreparedStatement find =
            connection.prepareStatement(
                "SELECT pid FROM pg_stat_activity WHERE application_name = ?");
        PreparedStatement terminate =
            connection.prepareStatement("SELECT pg_terminate_backend(?)")) {
      find.setString(1, applicationName);
      try (ResultSet backends = find.executeQuery()) {
        assertThat(backends.next()).isTrue();
        terminate.setInt(1, backends.getInt(1));
        try (ResultSet terminated = terminate.executeQuery()) {
          terminated.next();
          assertThat(terminated.getBoolean(1)).isTrue();
        }
      }
    }
  }

  private static Connection adminConnection() throws Exception {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static void awaitBackend(String applicationName) {
    Awaitility.await()
        .atMost(PATIENCE)
        .untilAsserted(
            () -> {
              try (Connection connection = adminConnection();
                  PreparedStatement count =
                      connection.prepareStatement(
                          "SELECT count(*) FROM pg_stat_activity WHERE application_name = ?")) {
                count.setString(1, applicationName);
                try (ResultSet rows = count.executeQuery()) {
                  rows.next();
                  assertThat(rows.getInt(1)).isEqualTo(1);
                }
              }
            });
  }

  private static final class Client implements AutoCloseable {

    private final WebSocket socket;
    private final LinkedBlockingQueue<JsonNode> frames;
    private final List<Long> versions = new CopyOnWriteArrayList<>();

    private Client(WebSocket socket, LinkedBlockingQueue<JsonNode> frames) {
      this.socket = socket;
      this.frames = frames;
    }

    static Client connect(ServerProcess server) throws Exception {
      return connect(server, null);
    }

    static Client connect(ServerProcess server, Long version) throws Exception {
      LinkedBlockingQueue<JsonNode> frames = new LinkedBlockingQueue<>();
      WebSocket socket =
          HTTP.newWebSocketBuilder()
              .buildAsync(server.webSocketUri("/sdk/v1/stream"), new Collector(frames))
              .get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
      String hello =
          version == null
              ? "{\"t\":\"hello\",\"sdkKey\":\"%s\"}".formatted(SDK_KEY)
              : "{\"t\":\"hello\",\"sdkKey\":\"%s\",\"version\":%d}".formatted(SDK_KEY, version);
      socket.sendText(hello, true).get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
      return new Client(socket, frames);
    }

    long awaitSnapshotVersion() throws Exception {
      JsonNode frame = this.next("snapshot");
      return frame.get("v").asLong();
    }

    boolean awaitDeltasTo(long version) throws Exception {
      long deadline = System.nanoTime() + PATIENCE.toNanos();
      while (System.nanoTime() < deadline) {
        JsonNode frame = this.frames.poll(100, TimeUnit.MILLISECONDS);
        if (frame == null || !frame.get("t").asText().equals("deltas")) {
          continue;
        }
        frame.get("entries").forEach(entry -> this.versions.add(entry.get("v").asLong()));
        if (frame.get("to").asLong() >= version) {
          return true;
        }
      }
      return false;
    }

    List<Long> deltaVersions() {
      return this.versions;
    }

    private JsonNode next(String type) throws Exception {
      long deadline = System.nanoTime() + PATIENCE.toNanos();
      while (System.nanoTime() < deadline) {
        JsonNode frame = this.frames.poll(100, TimeUnit.MILLISECONDS);
        if (frame != null && frame.get("t").asText().equals(type)) {
          return frame;
        }
      }
      throw new AssertionError("no " + type + " frame in time");
    }

    @Override
    public void close() {
      this.socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }
  }

  private static final class Collector implements WebSocket.Listener {

    private final LinkedBlockingQueue<JsonNode> frames;
    private final StringBuilder partial = new StringBuilder();

    Collector(LinkedBlockingQueue<JsonNode> frames) {
      this.frames = frames;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      this.partial.append(data);
      if (last) {
        try {
          this.frames.add(MAPPER.readTree(this.partial.toString()));
        } catch (Exception malformed) {
          throw new IllegalStateException(malformed);
        } finally {
          this.partial.setLength(0);
        }
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }
  }
}
