package dev.flagtide.adapter.out.memory.usecase;

import static dev.flagtide.adapter.out.memory.usecase.Flagtide.DEV;
import static dev.flagtide.adapter.out.memory.usecase.Flagtide.PROD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.port.out.ApiKeyStore;
import dev.flagtide.application.port.out.ChangeNotification;
import dev.flagtide.application.port.out.PropagationReport;
import dev.flagtide.application.port.out.Subscription;
import dev.flagtide.application.security.Principal;
import dev.flagtide.application.stream.ChangeStreams;
import dev.flagtide.application.stream.StreamSession;
import dev.flagtide.application.usecase.Authenticate;
import dev.flagtide.application.usecase.ConnectClient;
import dev.flagtide.application.usecase.PropagateChange;
import dev.flagtide.application.usecase.SendHeartbeats;
import dev.flagtide.application.usecase.ToggleFlag;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.evaluation.JsonValue;
import dev.flagtide.domain.json.JsonFields;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.FlagKey;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChangeStreamsTest {

  private final Flagtide app = new Flagtide();
  private final EnvironmentRef scope = new EnvironmentRef(Flagtide.PROJECT, DEV);
  private final ChangeStreams streams = this.streamsWithCapacity(this.app, 1000);
  private final ConnectClient connect = this.connectWith(this.app, this.streams);
  private final PropagateChange propagate = new PropagateChange(this.streams);
  private final String sdkKey = this.app.secret(DEV, ApiKeyKind.SDK);

  private ChangeStreams streamsWithCapacity(Flagtide target, int capacity) {
    return new ChangeStreams(
        target.adapters.changeLog(),
        target.buildSnapshot::forEnvironment,
        target.adapters.propagationStats(),
        target.time,
        capacity);
  }

  private ConnectClient connectWith(Flagtide target, ChangeStreams changeStreams) {
    return new ConnectClient(
        target.authenticate, changeStreams, target.time, Duration.ofSeconds(10));
  }

  private StreamSession open(RecordingClient client, Long since) {
    return this.connect.execute(
        new ConnectClient.Command(
            this.sdkKey, Optional.ofNullable(since).map(EnvironmentVersion::of), client));
  }

  private void toggle(String key, boolean enabled) {
    this.app.toggleFlag.execute(
        this.app.admin(DEV),
        new ToggleFlag.Command(new FlagKey(key), Optional.empty(), DEV, enabled));
  }

  private void commit(String key, boolean enabled) {
    this.toggle(key, enabled);
    this.propagate.execute(
        new ChangeNotification(
            this.scope, this.app.adapters.changeLog().currentVersion(this.scope)));
  }

  private void createFlags(String... keys) {
    for (String key : keys) {
      Fixtures.createFlag(this.app, key);
    }
  }

  @Test
  void aClientWithoutAVersionGetsASnapshotOfTheCurrentVersion() {
    this.createFlags("checkout", "search");
    RecordingClient client = new RecordingClient();

    this.open(client, null);

    JsonFields snapshot = client.lastFields();
    assertThat(snapshot.text("t")).isEqualTo("snapshot");
    assertThat(snapshot.integer("v")).isEqualTo(2);
    assertThat(snapshot.integer("committedAtMs"))
        .isEqualTo(this.app.time.now().toInstant().toEpochMilli());
    assertThat(RecordingClient.keys(snapshot.array("flags"))).containsExactly("checkout", "search");
    assertThat(snapshot.array("segments")).isEmpty();
  }

  @Test
  void aSnapshotOfAnEmptyEnvironmentHasNoCommitTime() {
    RecordingClient client = new RecordingClient();

    this.open(client, null);

    assertThat(client.lastFields().integer("v")).isZero();
    assertThat(client.lastFields().find("committedAtMs")).isEmpty();
  }

  @Test
  void aClientThatIsCurrentGetsAnEmptyDeltaAndThenOnlyChanges() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();

    this.open(client, 1L);

    JsonFields confirmation = client.lastFields();
    assertThat(confirmation.text("t")).isEqualTo("deltas");
    assertThat(confirmation.integer("from")).isEqualTo(1);
    assertThat(confirmation.integer("to")).isEqualTo(1);
    assertThat(confirmation.array("entries")).isEmpty();
    this.commit("checkout", true);
    assertThat(client.types()).containsExactly("deltas", "deltas");
    assertThat(RecordingClient.entryVersions(client.lastFields())).containsExactly(2L);
  }

  @Test
  void aClientBehindGetsExactlyTheMissedEntriesInOrder() {
    this.createFlags("checkout");
    this.toggle("checkout", true);
    this.toggle("checkout", false);
    this.toggle("checkout", true);
    RecordingClient client = new RecordingClient();

    this.open(client, 1L);

    JsonFields deltas = client.lastFields();
    assertThat(deltas.text("t")).isEqualTo("deltas");
    assertThat(deltas.integer("from")).isEqualTo(1);
    assertThat(deltas.integer("to")).isEqualTo(4);
    assertThat(RecordingClient.entryVersions(deltas)).containsExactly(2L, 3L, 4L);
    JsonValue.JsonArray changes =
        (JsonValue.JsonArray)
            JsonFields.of("entry", deltas.array("entries").getFirst()).require("changes");
    assertThat(RecordingClient.keys(changes.items())).containsExactly("checkout");
    assertThat(JsonFields.of("change", changes.items().getFirst()).text("op")).isEqualTo("upsert");
  }

  @Test
  void aClientAheadOfTheServerGetsASnapshotAtTheServerVersion() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();

    this.open(client, 99L);

    assertThat(client.lastFields().text("t")).isEqualTo("snapshot");
    assertThat(client.lastFields().integer("v")).isEqualTo(1);
  }

  @Test
  void aClientAheadOfThisInstanceButNotOfTheDatabaseIsCaughtUpFirst() {
    this.createFlags("checkout");
    this.open(new RecordingClient(), null);
    this.toggle("checkout", true);
    this.toggle("checkout", false);
    RecordingClient client = new RecordingClient();

    this.open(client, 3L);

    assertThat(client.lastFields().text("t")).isEqualTo("deltas");
    assertThat(client.lastFields().integer("to")).isEqualTo(3);
    assertThat(client.lastFields().array("entries")).isEmpty();
  }

  @Test
  void aClientOlderThanTheRetainedWindowGetsASnapshot() {
    Flagtide shortLived = new Flagtide(3);
    ChangeStreams shortStreams = this.streamsWithCapacity(shortLived, 3);
    ConnectClient shortConnect = this.connectWith(shortLived, shortStreams);
    shortLived.createFlag.execute(shortLived.admin(DEV), Fixtures.booleanFlag("checkout"));
    for (int index = 0; index < 5; index++) {
      shortLived.toggleFlag.execute(
          shortLived.admin(DEV),
          new ToggleFlag.Command(new FlagKey("checkout"), Optional.empty(), DEV, index % 2 == 0));
    }
    String key = shortLived.secret(DEV, ApiKeyKind.SDK);
    RecordingClient stale = new RecordingClient();
    RecordingClient recent = new RecordingClient();

    shortConnect.execute(
        new ConnectClient.Command(key, Optional.of(EnvironmentVersion.of(1)), stale));
    shortConnect.execute(
        new ConnectClient.Command(key, Optional.of(EnvironmentVersion.of(3)), recent));

    assertThat(stale.lastFields().text("t")).isEqualTo("snapshot");
    assertThat(stale.lastFields().integer("v")).isEqualTo(6);
    assertThat(recent.lastFields().text("t")).isEqualTo("deltas");
    assertThat(RecordingClient.entryVersions(recent.lastFields())).containsExactly(4L, 5L, 6L);
  }

  @Test
  void aChangeReachesEveryConnectedClientAsOneSharedFrame() {
    this.createFlags("checkout");
    RecordingClient first = new RecordingClient();
    RecordingClient second = new RecordingClient();
    this.open(first, 1L);
    this.open(second, null);

    this.commit("checkout", true);

    assertThat(first.last()).contains("\"to\":2");
    assertThat(second.last()).isSameAs(first.last());
  }

  @Test
  void aChangeInAnotherEnvironmentDoesNotReachTheClient() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L);

    this.app.toggleFlag.execute(
        this.app.admin(PROD),
        new ToggleFlag.Command(new FlagKey("checkout"), Optional.empty(), PROD, true));
    this.propagate.execute(
        new ChangeNotification(
            new EnvironmentRef(Flagtide.PROJECT, PROD), EnvironmentVersion.of(2)));

    assertThat(client.frames()).hasSize(1);
  }

  @Test
  void severalCommitsBeforeOneNotificationArriveAsOneFrameAndLaterNotificationsAreIgnored() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L);
    this.toggle("checkout", true);
    this.toggle("checkout", false);

    this.propagate.execute(new ChangeNotification(this.scope, EnvironmentVersion.of(2)));
    this.propagate.execute(new ChangeNotification(this.scope, EnvironmentVersion.of(3)));

    assertThat(client.frames()).hasSize(2);
    assertThat(RecordingClient.entryVersions(client.lastFields())).containsExactly(2L, 3L);
  }

  @Test
  void aClientThatAlreadyHoldsANewerSnapshotOnlyGetsWhatFollowsIt() {
    this.createFlags("checkout");
    this.open(new RecordingClient(), 1L);
    this.toggle("checkout", true);
    RecordingClient client = new RecordingClient();
    this.open(client, null);
    assertThat(client.lastFields().integer("v")).isEqualTo(2);
    this.toggle("checkout", false);
    this.toggle("checkout", true);

    this.propagate.execute(new ChangeNotification(this.scope, EnvironmentVersion.of(4)));

    assertThat(RecordingClient.entryVersions(client.lastFields())).containsExactly(3L, 4L);
  }

  @Test
  void aStreamThatFellBehindTheRetainedWindowRestartsEveryClientFromASnapshot() {
    Flagtide shortLived = new Flagtide(3);
    ChangeStreams shortStreams = this.streamsWithCapacity(shortLived, 3);
    ConnectClient shortConnect = this.connectWith(shortLived, shortStreams);
    shortLived.createFlag.execute(shortLived.admin(DEV), Fixtures.booleanFlag("checkout"));
    RecordingClient client = new RecordingClient();
    shortConnect.execute(
        new ConnectClient.Command(
            shortLived.secret(DEV, ApiKeyKind.SDK), Optional.of(EnvironmentVersion.of(1)), client));
    for (int index = 0; index < 5; index++) {
      shortLived.toggleFlag.execute(
          shortLived.admin(DEV),
          new ToggleFlag.Command(new FlagKey("checkout"), Optional.empty(), DEV, index % 2 == 0));
    }

    shortStreams.resync();

    assertThat(client.types()).containsExactly("deltas", "snapshot");
    assertThat(client.lastFields().integer("v")).isEqualTo(6);
  }

  @Test
  void resyncRepairsNotificationsThatWereLost() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L);
    this.toggle("checkout", true);

    this.propagate.resync();

    assertThat(RecordingClient.entryVersions(client.lastFields())).containsExactly(2L);
  }

  @Test
  void aClosedSessionReceivesNothingMore() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    StreamSession session = this.open(client, 1L);

    session.close();
    session.close();
    this.commit("checkout", true);

    assertThat(client.frames()).hasSize(1);
    assertThat(this.app.adapters.propagationStats().report(this.scope).connectedClients()).isZero();
  }

  @Test
  void connectedClientsAreCountedPerEnvironment() {
    this.createFlags("checkout");

    this.open(new RecordingClient(), null);
    this.open(new RecordingClient(), 1L);

    assertThat(this.app.adapters.propagationStats().report(this.scope).connectedClients())
        .isEqualTo(2);
  }

  @Test
  void heartbeatsCarryTheTimeAndTheCurrentVersion() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L);

    new SendHeartbeats(this.streams).execute();

    JsonFields heartbeat = client.lastFields();
    assertThat(heartbeat.text("t")).isEqualTo("hb");
    assertThat(heartbeat.integer("v")).isEqualTo(1);
    assertThat(heartbeat.integer("ts")).isEqualTo(this.app.time.now().toInstant().toEpochMilli());
  }

  @Test
  void anEnvironmentWithoutClientsGetsNoHeartbeat() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L).close();

    new SendHeartbeats(this.streams).execute();

    assertThat(client.types()).containsExactly("deltas");
  }

  @Test
  void anAcknowledgementOfALiveChangeIsTimedFromItsCommit() {
    this.createFlags("checkout");
    StreamSession session = this.open(new RecordingClient(), 1L);
    this.commit("checkout", true);
    this.app.time.advance(Duration.ofMillis(42));

    session.acknowledge(EnvironmentVersion.of(2));

    PropagationReport report = this.app.adapters.propagationStats().report(this.scope);
    assertThat(report.samples()).isEqualTo(1);
    assertThat(report.p50Millis()).isEqualTo(42);
  }

  @Test
  void anAcknowledgementIsCountedOnceAndOnlyForWhatWasSent() {
    this.createFlags("checkout");
    StreamSession session = this.open(new RecordingClient(), 1L);
    this.commit("checkout", true);

    session.acknowledge(EnvironmentVersion.of(2));
    session.acknowledge(EnvironmentVersion.of(2));
    session.acknowledge(EnvironmentVersion.of(9));

    assertThat(this.app.adapters.propagationStats().report(this.scope).samples()).isEqualTo(1);
  }

  @Test
  void acknowledgingTheWelcomeDoesNotCountAsPropagation() {
    this.createFlags("checkout");
    this.app.time.advance(Duration.ofSeconds(30));
    StreamSession session = this.open(new RecordingClient(), null);

    session.acknowledge(EnvironmentVersion.of(1));

    assertThat(this.app.adapters.propagationStats().report(this.scope).samples()).isZero();
  }

  @Test
  void anAcknowledgementAfterClosingIsIgnored() {
    this.createFlags("checkout");
    StreamSession session = this.open(new RecordingClient(), 1L);
    this.commit("checkout", true);
    session.close();

    session.acknowledge(EnvironmentVersion.of(2));

    assertThat(this.app.adapters.propagationStats().report(this.scope).samples()).isZero();
  }

  @Test
  void aVersionThatLeftTheRingCannotBeTimed() {
    Flagtide small = new Flagtide(2);
    ChangeStreams smallStreams = this.streamsWithCapacity(small, 2);
    ConnectClient smallConnect = this.connectWith(small, smallStreams);
    small.createFlag.execute(small.admin(DEV), Fixtures.booleanFlag("checkout"));
    StreamSession session =
        smallConnect.execute(
            new ConnectClient.Command(
                small.secret(DEV, ApiKeyKind.SDK),
                Optional.of(EnvironmentVersion.of(1)),
                new RecordingClient()));
    for (int index = 0; index < 4; index++) {
      small.toggleFlag.execute(
          small.admin(DEV),
          new ToggleFlag.Command(new FlagKey("checkout"), Optional.empty(), DEV, index % 2 == 0));
      smallStreams.propagate(
          new ChangeNotification(
              this.scope, small.adapters.changeLog().currentVersion(this.scope)));
    }

    session.acknowledge(EnvironmentVersion.of(2));

    assertThat(small.adapters.propagationStats().report(this.scope).samples()).isZero();
  }

  @Test
  void theMemoryChangeFeedDrivesPropagation() {
    this.createFlags("checkout");
    RecordingClient client = new RecordingClient();
    this.open(client, 1L);
    Subscription subscription = this.app.adapters.changeFeed().subscribe(this.propagate::execute);

    this.toggle("checkout", true);
    subscription.close();

    assertThat(RecordingClient.entryVersions(client.lastFields())).containsExactly(2L);
  }

  @Test
  void aKeyThatIsNotAnSdkKeyIsRefused() {
    FlagtideError failure =
        this.app.failure(
            () ->
                this.connect.execute(
                    new ConnectClient.Command(
                        this.app.secret(DEV, ApiKeyKind.ADMIN),
                        Optional.empty(),
                        new RecordingClient())));

    assertThat(failure).isInstanceOf(FlagtideError.Unauthorized.class);
  }

  @Test
  void anUnknownKeyIsRefused() {
    FlagtideError failure =
        this.app.failure(
            () ->
                this.connect.execute(
                    new ConnectClient.Command(
                        "fws_unknown_unknown_unknown_unknown",
                        Optional.empty(),
                        new RecordingClient())));

    assertThat(failure).isInstanceOf(FlagtideError.Unauthorized.class);
  }

  @Test
  void theSessionKnowsItsPrincipal() {
    StreamSession session = this.open(new RecordingClient(), null);

    Principal principal = session.principal();

    assertThat(principal.environmentRef()).isEqualTo(this.scope);
    assertThat(principal.kind()).isEqualTo(ApiKeyKind.SDK);
  }

  @Test
  void aKeyIsLookedUpOnceWithinTheCacheTimeAndAgainAfterIt() {
    int[] lookups = {0};
    Authenticate counting = new Authenticate(this.countingKeyStore(this.app, lookups));
    ConnectClient cached =
        new ConnectClient(counting, this.streams, this.app.time, Duration.ofSeconds(10));
    ConnectClient.Command command =
        new ConnectClient.Command(this.sdkKey, Optional.empty(), new RecordingClient());

    cached.execute(command);
    cached.execute(command);
    this.app.time.advance(Duration.ofSeconds(11));
    cached.execute(command);

    assertThat(lookups[0]).isEqualTo(2);
  }

  @Test
  void aRingNeedsRoomForAtLeastOneEntry() {
    assertThatThrownBy(() -> this.streamsWithCapacity(this.app, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ApiKeyStore countingKeyStore(Flagtide target, int[] lookups) {
    ApiKeyStore delegate = target.adapters.apiKeys();
    return (ApiKeyStore)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {ApiKeyStore.class},
            (proxy, method, arguments) -> {
              if (method.getName().equals("findByLookup")) {
                lookups[0]++;
              }
              try {
                return method.invoke(delegate, arguments);
              } catch (InvocationTargetException failure) {
                throw failure.getCause();
              }
            });
  }
}
