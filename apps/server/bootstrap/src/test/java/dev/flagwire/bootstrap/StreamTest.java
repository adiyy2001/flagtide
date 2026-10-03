package dev.flagwire.bootstrap;

import static dev.flagwire.bootstrap.Requests.as;
import static dev.flagwire.bootstrap.Requests.booleanFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.flagwire.application.port.out.ChangeLog;
import dev.flagwire.application.usecase.CreateProject;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.ProjectKey;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.websockets.next.BasicWebSocketConnector;
import jakarta.inject.Inject;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class StreamTest {

  private static final int CLOSE_BAD_FRAME = 4400;
  private static final int CLOSE_UNAUTHORIZED = 4401;
  private static final int CLOSE_HELLO_DEADLINE = 4408;
  private static final int CLOSE_SLOW_CONSUMER = 4429;
  private static final int BIG_VARIANT_CHARACTERS = 600_000;
  private static final int BIG_FLAGS = 40;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Inject CreateProject createProject;
  @Inject ChangeLog changeLog;
  @Inject BasicWebSocketConnector connector;

  @TestHTTPResource("/")
  URL base;

  TestProject project;
  private final List<StreamProbe> probes = new ArrayList<>();

  @BeforeEach
  void freshProject() {
    this.project = TestProject.create(this.createProject);
  }

  @AfterEach
  void closeProbes() {
    this.probes.forEach(StreamProbe::close);
    this.probes.clear();
  }

  private StreamProbe connect() throws Exception {
    return this.register(StreamProbe.connect(this.connector, this.base.toURI()));
  }

  private StreamProbe register(StreamProbe probe) {
    this.probes.add(probe);
    return probe;
  }

  private long currentVersion() {
    return this.changeLog
        .currentVersion(
            new EnvironmentRef(new ProjectKey(this.project.key()), new EnvironmentKey("dev")))
        .value();
  }

  private void createFlags(String prefix, int count) {
    for (int index = 0; index < count; index++) {
      int status =
          as(this.project.devAdmin())
              .body(booleanFlag(prefix + "-" + index))
              .post(this.project.flagsPath())
              .statusCode();
      assertThat(status).isEqualTo(201);
    }
  }

  private static List<Long> versionsOf(JsonNode deltas) {
    List<Long> versions = new ArrayList<>();
    deltas.get("entries").forEach(entry -> versions.add(entry.get("v").asLong()));
    return versions;
  }

  @Test
  void aHelloWithoutAVersionIsAnsweredWithTheSnapshot() throws Exception {
    this.createFlags("seed", 2);
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk());

    JsonNode snapshot = client.next("snapshot");
    assertThat(snapshot.get("v").asLong()).isEqualTo(this.currentVersion());
    assertThat(snapshot.get("flags")).hasSize(2);
  }

  @Test
  void aClientThatIsCurrentGetsAnEmptyDeltasFrame() throws Exception {
    this.createFlags("seed", 1);
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk(), this.currentVersion());

    JsonNode deltas = client.next("deltas");
    assertThat(deltas.get("from").asLong()).isEqualTo(this.currentVersion());
    assertThat(deltas.get("entries")).isEmpty();
  }

  @Test
  void aReconnectingClientGetsExactlyTheMissedDeltasInOrder() throws Exception {
    this.createFlags("seed", 1);
    long known = this.currentVersion();
    this.createFlags("missed", 3);
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk(), known);

    JsonNode deltas = client.next("deltas");
    assertThat(deltas.get("from").asLong()).isEqualTo(known);
    assertThat(deltas.get("to").asLong()).isEqualTo(known + 3);
    assertThat(versionsOf(deltas)).containsExactly(known + 1, known + 2, known + 3);
  }

  @Test
  void aVersionOlderThanTheRetainedWindowGetsASnapshot() throws Exception {
    this.createFlags("seed", 9);
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk(), 1);

    JsonNode snapshot = client.next("snapshot");
    assertThat(snapshot.get("v").asLong()).isEqualTo(this.currentVersion());
    assertThat(snapshot.get("flags")).hasSize(9);
  }

  @Test
  void aVersionAheadOfTheServerGetsASnapshot() throws Exception {
    this.createFlags("seed", 1);
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk(), 1_000_000);

    assertThat(client.next("snapshot").get("v").asLong()).isEqualTo(this.currentVersion());
  }

  @Test
  void aChangeAfterTheHelloReachesTheConnectedClient() throws Exception {
    StreamProbe client = this.connect();
    client.hello(this.project.devSdk());
    long known = client.next("snapshot").get("v").asLong();

    this.createFlags("live", 1);

    JsonNode deltas = client.next("deltas");
    assertThat(deltas.get("from").asLong()).isEqualTo(known);
    assertThat(versionsOf(deltas)).containsExactly(known + 1);
  }

  @Test
  void aWrongKeyIsClosedWithUnauthorized() throws Exception {
    StreamProbe client = this.connect();

    client.hello("fws_not_a_real_key_000000000000");

    assertThat(client.next("error").get("code").asInt()).isEqualTo(CLOSE_UNAUTHORIZED);
    assertThat(client.awaitClose().getCode()).isEqualTo(CLOSE_UNAUTHORIZED);
  }

  @Test
  void anAdminKeyIsClosedWithUnauthorized() throws Exception {
    StreamProbe client = this.connect();

    client.hello(this.project.devAdmin());

    assertThat(client.awaitClose().getCode()).isEqualTo(CLOSE_UNAUTHORIZED);
  }

  @Test
  void aClientThatNeverSaysHelloIsClosedAtTheDeadline() throws Exception {
    StreamProbe client = this.connect();

    assertThat(client.awaitClose().getCode()).isEqualTo(CLOSE_HELLO_DEADLINE);
  }

  @Test
  void aMalformedFrameIsClosedWithBadFrame() throws Exception {
    StreamProbe client = this.connect();

    client.send("not json");

    assertThat(client.awaitClose().getCode()).isEqualTo(CLOSE_BAD_FRAME);
  }

  @Test
  void anAckBeforeHelloIsClosedWithBadFrame() throws Exception {
    StreamProbe client = this.connect();

    client.ack(1);

    assertThat(client.awaitClose().getCode()).isEqualTo(CLOSE_BAD_FRAME);
  }

  @Test
  void heartbeatsCarryTheCurrentVersion() throws Exception {
    this.createFlags("seed", 1);
    StreamProbe client = this.connect();
    client.hello(this.project.devSdk());
    client.next("snapshot");

    JsonNode heartbeat = client.next("hb");

    assertThat(heartbeat.get("v").asLong()).isEqualTo(this.currentVersion());
    assertThat(heartbeat.get("ts").asLong()).isPositive();
  }

  @Test
  void anAckFeedsTheHistogramBehindThePropagationEndpoint() throws Exception {
    StreamProbe client = this.connect();
    client.hello(this.project.devSdk());
    client.next("snapshot");
    this.createFlags("acked", 1);
    long version = client.next("deltas").get("to").asLong();

    client.ack(version);

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              JsonNode report = this.propagation();
              assertThat(report.get("connectedClients").asInt()).isEqualTo(1);
              assertThat(report.get("samples").asLong()).isEqualTo(1);
              assertThat(report.get("p95Millis").asLong()).isBetween(0L, 5000L);
            });
  }

  @Test
  void theConnectedCountFallsWhenTheClientLeaves() throws Exception {
    StreamProbe client = this.connect();
    client.hello(this.project.devSdk());
    client.next("snapshot");
    assertThat(this.propagation().get("connectedClients").asInt()).isEqualTo(1);

    client.close();

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(this.propagation().get("connectedClients").asInt()).isZero());
  }

  @Test
  void thePropagationEndpointNeedsAnAdminKey() {
    as(this.project.devSdk()).get(this.propagationPath("dev")).then().statusCode(403);
    as(this.project.devAdmin()).get(this.propagationPath("nowhere")).then().statusCode(404);
  }

  @Test
  void anOriginOutsideTheAllowListIsRefused() {
    assertThatThrownBy(
            () -> new RawStreamClient(this.base.getHost(), this.base.getPort(), "http://evil.test"))
        .hasMessageContaining("403");
  }

  @Test
  void anAllowListedOriginIsAccepted() throws Exception {
    try (RawStreamClient raw =
        new RawStreamClient(
            this.base.getHost(), this.base.getPort(), StreamProfiles.ALLOWED_ORIGIN)) {
      raw.sendText("not json");

      assertThat(raw.readUntilCloseCode()).isEqualTo(CLOSE_BAD_FRAME);
    }
  }

  @Test
  void theOriginOfTheServerItselfIsAccepted() throws Exception {
    StreamProbe client = this.connect();

    client.hello(this.project.devSdk());

    client.next("snapshot");
  }

  @Test
  void aClientThatStopsReadingIsClosedWithSlowConsumer() throws Exception {
    String bigVariant = "x".repeat(BIG_VARIANT_CHARACTERS);
    try (RawStreamClient raw =
        new RawStreamClient(
            this.base.getHost(), this.base.getPort(), StreamProfiles.ALLOWED_ORIGIN)) {
      raw.sendText("{\"t\":\"hello\",\"sdkKey\":\"%s\"}".formatted(this.project.devSdk()));
      IntStream.range(0, BIG_FLAGS)
          .forEach(
              index ->
                  as(this.project.devAdmin())
                      .body(
                          """
                          {"key":"big-%d","description":"large","type":"string",
                           "variants":[{"key":"a","value":"%s"},{"key":"b","value":"b"}],
                           "offVariant":"b","fallthroughVariant":"a"}
                          """
                              .formatted(index, bigVariant))
                      .post(this.project.flagsPath())
                      .then()
                      .statusCode(201));

      assertThat(raw.readUntilCloseCode()).isEqualTo(CLOSE_SLOW_CONSUMER);
    }
  }

  private String propagationPath(String environment) {
    return "/api/v1/projects/"
        + this.project.key()
        + "/environments/"
        + environment
        + "/propagation";
  }

  private JsonNode propagation() throws Exception {
    String body =
        as(this.project.devAdmin())
            .get(this.propagationPath("dev"))
            .then()
            .statusCode(200)
            .extract()
            .asString();
    return MAPPER.readTree(body);
  }
}
