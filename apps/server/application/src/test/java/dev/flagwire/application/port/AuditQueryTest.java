package dev.flagwire.application.port;

import static dev.flagwire.application.testing.Samples.DEV;
import static dev.flagwire.application.testing.Samples.SHOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagwire.application.port.out.AuditQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuditQueryTest {

  @Test
  void startsUnfilteredAtTheBeginning() {
    AuditQuery query = AuditQuery.forProject(SHOP, 25);

    assertThat(query.project()).isEqualTo(SHOP);
    assertThat(query.environment()).isEmpty();
    assertThat(query.entityKey()).isEmpty();
    assertThat(query.limit()).isEqualTo(25);
    assertThat(query.offset()).isZero();
  }

  @Test
  void refinesWithoutLosingEarlierChoices() {
    AuditQuery query =
        AuditQuery.forProject(SHOP, 25).inEnvironment(DEV).aboutEntity("checkout").skipping(50);

    assertThat(query.environment()).isEqualTo(Optional.of(DEV));
    assertThat(query.entityKey()).contains("checkout");
    assertThat(query.limit()).isEqualTo(25);
    assertThat(query.offset()).isEqualTo(50);
  }
}
