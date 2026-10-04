package dev.flagtide.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.domain.access.ApiKeyKind;
import org.junit.jupiter.api.Test;

class ApiKeysTest {

  @Test
  void prefixesTheTokenByKind() {
    assertThat(ApiKeys.secret(ApiKeyKind.ADMIN, "abc")).isEqualTo("fwa_abc");
    assertThat(ApiKeys.secret(ApiKeyKind.SDK, "abc")).isEqualTo("fws_abc");
  }

  @Test
  void recognisesTheKindFromThePrefix() {
    assertThat(ApiKeys.kindOf("fwa_abc")).contains(ApiKeyKind.ADMIN);
    assertThat(ApiKeys.kindOf("fws_abc")).contains(ApiKeyKind.SDK);
    assertThat(ApiKeys.kindOf("other_abc")).isEmpty();
    assertThat(ApiKeys.kindOf("")).isEmpty();
  }

  @Test
  void hashesAdminSecretsAndKeepsSdkKeysReadable() {
    assertThat(ApiKeys.lookupFor(ApiKeyKind.SDK, "fws_abc")).isEqualTo("fws_abc");
    assertThat(ApiKeys.lookupFor(ApiKeyKind.ADMIN, "fwa_abc"))
        .hasSize(64)
        .isNotEqualTo("fwa_abc")
        .isEqualTo(ApiKeys.lookupFor(ApiKeyKind.ADMIN, "fwa_abc"));
    assertThat(ApiKeys.lookupFor(ApiKeyKind.ADMIN, "fwa_abc"))
        .isNotEqualTo(ApiKeys.lookupFor(ApiKeyKind.ADMIN, "fwa_abd"));
  }

  @Test
  void matchesTheKnownSha256OfAnAdminSecret() {
    assertThat(ApiKeys.sha256("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}
