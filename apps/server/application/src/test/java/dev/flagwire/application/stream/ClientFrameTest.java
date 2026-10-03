package dev.flagwire.application.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClientFrameTest {

  @Test
  void parsesAHelloWithEveryField() {
    ClientFrame frame =
        ClientFrame.parse(
            "{\"t\":\"hello\",\"sdkKey\":\"fws_k\",\"version\":7,\"clientId\":\"c1\",\"sdk\":\"core/1\"}");

    assertThat(frame)
        .isEqualTo(
            new ClientFrame.Hello(
                "fws_k",
                Optional.of(EnvironmentVersion.of(7)),
                Optional.of("c1"),
                Optional.of("core/1")));
  }

  @Test
  void aHelloNeedsOnlyTheKey() {
    ClientFrame frame = ClientFrame.parse("{\"t\":\"hello\",\"sdkKey\":\"fws_k\"}");

    assertThat(frame)
        .isEqualTo(
            new ClientFrame.Hello("fws_k", Optional.empty(), Optional.empty(), Optional.empty()));
  }

  @Test
  void parsesAnAck() {
    assertThat(ClientFrame.parse("{\"t\":\"ack\",\"v\":12}"))
        .isEqualTo(new ClientFrame.Ack(EnvironmentVersion.of(12)));
  }

  @Test
  void rejectsWhatIsNotAFrame() {
    assertThatThrownBy(() -> ClientFrame.parse("not json")).isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> ClientFrame.parse("[]")).isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> ClientFrame.parse("{\"t\":\"shout\"}"))
        .isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> ClientFrame.parse("{\"t\":\"hello\"}"))
        .isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> ClientFrame.parse("{\"t\":\"ack\",\"v\":-1}"))
        .isInstanceOf(FlagwireException.class);
    assertThatThrownBy(() -> ClientFrame.parse("{\"t\":\"ack\",\"v\":1.5}"))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void rejectsAFrameThatIsTooLong() {
    String oversized =
        "{\"t\":\"hello\",\"sdkKey\":\"" + "k".repeat(ClientFrame.MAX_TEXT_LENGTH) + "\"}";

    assertThatThrownBy(() -> ClientFrame.parse(oversized)).isInstanceOf(FlagwireException.class);
  }
}
