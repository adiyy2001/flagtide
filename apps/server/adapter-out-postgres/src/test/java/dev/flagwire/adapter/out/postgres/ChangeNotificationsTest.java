package dev.flagwire.adapter.out.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.port.out.ChangeNotification;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import dev.flagwire.domain.value.EnvironmentVersion;
import dev.flagwire.domain.value.ProjectKey;
import org.junit.jupiter.api.Test;

class ChangeNotificationsTest {

  private static final ChangeNotification NOTIFICATION =
      new ChangeNotification(
          new EnvironmentRef(new ProjectKey("shop"), new EnvironmentKey("prod")),
          EnvironmentVersion.of(42));

  @Test
  void writesAPointerNotTheChange() {
    assertThat(ChangeNotifications.payload(NOTIFICATION))
        .isEqualTo("{\"environment\":\"prod\",\"project\":\"shop\",\"version\":42}");
  }

  @Test
  void parsesWhatItWrites() {
    String payload = ChangeNotifications.payload(NOTIFICATION);

    assertThat(ChangeNotifications.parse(payload)).isEqualTo(NOTIFICATION);
  }

  @Test
  void aPointerIsFarBelowTheNotifyPayloadLimit() {
    ChangeNotification longest =
        new ChangeNotification(
            new EnvironmentRef(new ProjectKey("a".repeat(64)), new EnvironmentKey("b".repeat(64))),
            EnvironmentVersion.of(Long.MAX_VALUE));

    assertThat(ChangeNotifications.payload(longest).length()).isLessThan(8000);
  }
}
