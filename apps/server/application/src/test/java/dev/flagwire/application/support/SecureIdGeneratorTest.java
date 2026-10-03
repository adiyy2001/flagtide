package dev.flagwire.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SecureIdGeneratorTest {

  private final SecureIdGenerator ids = new SecureIdGenerator();

  @Test
  void producesUuidIdentifiers() {
    assertThat(UUID.fromString(this.ids.newId())).isNotNull();
  }

  @Test
  void producesEightHexCharacterSalts() {
    assertThat(this.ids.newSalt().value()).matches("[0-9a-f]{8}");
  }

  @Test
  void producesUrlSafeTokensThatDoNotRepeat() {
    Set<String> tokens = new HashSet<>();
    IntStream.range(0, 200).forEach(index -> tokens.add(this.ids.newToken()));

    assertThat(tokens).hasSize(200).allMatch(token -> token.matches("[A-Za-z0-9_-]{32}"));
  }
}
