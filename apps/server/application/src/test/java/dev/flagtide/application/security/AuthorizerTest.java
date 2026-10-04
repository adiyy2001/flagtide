package dev.flagtide.application.security;

import static dev.flagtide.application.testing.Samples.DEV;
import static dev.flagtide.application.testing.Samples.PROD;
import static dev.flagtide.application.testing.Samples.SHOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideError;
import dev.flagtide.domain.error.FlagtideException;
import org.junit.jupiter.api.Test;

class AuthorizerTest {

  private final Authorizer authorizer = new Authorizer();
  private final Principal admin = Principal.of(Samples.apiKey("a", ApiKeyKind.ADMIN, SHOP, DEV));
  private final Principal sdk = Principal.of(Samples.apiKey("s", ApiKeyKind.SDK, SHOP, DEV));

  @Test
  void anAdminKeyPassesTheAdminCheck() {
    assertThatCode(() -> this.authorizer.requireAdmin(this.admin)).doesNotThrowAnyException();
  }

  @Test
  void anSdkKeyIsForbiddenFromAdminOperations() {
    assertThatThrownBy(() -> this.authorizer.requireAdmin(this.sdk))
        .isInstanceOfSatisfying(
            FlagtideException.class,
            exception -> assertThat(exception.error()).isInstanceOf(FlagtideError.Forbidden.class));
  }

  @Test
  void writingNeedsAnAdminKeyOfTheSameEnvironment() {
    assertThatCode(() -> this.authorizer.requireEnvironmentWrite(this.admin, DEV))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentWrite(this.admin, PROD))
        .isInstanceOf(FlagtideException.class)
        .hasMessageContaining("prod");
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentWrite(this.sdk, DEV))
        .isInstanceOf(FlagtideException.class);
  }

  @Test
  void readingNeedsAKeyOfTheSameEnvironmentOfAnyKind() {
    assertThatCode(() -> this.authorizer.requireEnvironmentRead(this.sdk, DEV))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentRead(this.sdk, PROD))
        .isInstanceOf(FlagtideException.class);
  }
}
