package dev.flagtide.application.contract;

import static dev.flagtide.application.testing.Samples.BLOG;
import static dev.flagtide.application.testing.Samples.DEV;
import static dev.flagtide.application.testing.Samples.PROD;
import static dev.flagtide.application.testing.Samples.SHOP;
import static dev.flagtide.application.testing.Samples.SHOP_DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagtide.application.testing.Samples;
import dev.flagtide.domain.access.ApiKey;
import dev.flagtide.domain.access.ApiKeyKind;
import dev.flagtide.domain.error.FlagtideException;
import org.junit.jupiter.api.Test;

public abstract class ApiKeyStoreContract extends AdapterContract {

  @Test
  void findsAKeyByItsLookupValue() {
    ApiKey key = Samples.apiKey("one", ApiKeyKind.ADMIN, SHOP, DEV);

    this.adapters.apiKeys().save(key);

    assertThat(this.adapters.apiKeys().findByLookup(key.lookup())).contains(key);
    assertThat(this.adapters.apiKeys().findByLookup("unknown")).isEmpty();
  }

  @Test
  void listsTheKeysOfOneEnvironment() {
    ApiKey admin = Samples.apiKey("a", ApiKeyKind.ADMIN, SHOP, DEV);
    ApiKey sdk = Samples.apiKey("b", ApiKeyKind.SDK, SHOP, DEV);
    this.adapters.apiKeys().save(admin);
    this.adapters.apiKeys().save(sdk);
    this.adapters.apiKeys().save(Samples.apiKey("c", ApiKeyKind.SDK, SHOP, PROD));
    this.adapters.apiKeys().save(Samples.apiKey("d", ApiKeyKind.SDK, BLOG, DEV));

    assertThat(this.adapters.apiKeys().findByEnvironment(SHOP_DEV)).containsExactly(admin, sdk);
  }

  @Test
  void rejectsTwoKeysWithTheSameLookupValue() {
    this.adapters.apiKeys().save(Samples.apiKey("one", ApiKeyKind.ADMIN, SHOP, DEV));

    assertThatThrownBy(
            () -> this.adapters.apiKeys().save(Samples.apiKey("one", ApiKeyKind.SDK, SHOP, PROD)))
        .isInstanceOf(FlagtideException.class);
  }

  @Test
  void rollsBackASave() {
    ApiKey key = Samples.apiKey("one", ApiKeyKind.ADMIN, SHOP, DEV);

    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.apiKeys().save(key);
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.adapters.apiKeys().findByLookup(key.lookup())).isEmpty();
  }
}
