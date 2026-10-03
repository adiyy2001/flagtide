package dev.flagwire.application.security;

import static dev.flagwire.application.testing.Samples.DEV;
import static dev.flagwire.application.testing.Samples.PROD;
import static dev.flagwire.application.testing.Samples.SHOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.testing.Samples;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireError;
import dev.flagwire.domain.error.FlagwireException;
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
            FlagwireException.class,
            exception -> assertThat(exception.error()).isInstanceOf(FlagwireError.Forbidden.class));
  }

  @Test
  void writingNeedsAnAdminKeyOfTheSameEnvironment() {
    assertThatCode(() -> this.authorizer.requireEnvironmentWrite(this.admin, DEV))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentWrite(this.admin, PROD))
        .isInstanceOf(FlagwireException.class)
        .hasMessageContaining("prod");
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentWrite(this.sdk, DEV))
        .isInstanceOf(FlagwireException.class);
  }

  @Test
  void readingNeedsAKeyOfTheSameEnvironmentOfAnyKind() {
    assertThatCode(() -> this.authorizer.requireEnvironmentRead(this.sdk, DEV))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> this.authorizer.requireEnvironmentRead(this.sdk, PROD))
        .isInstanceOf(FlagwireException.class);
  }
}
