package dev.flagwire.application.contract;

import static dev.flagwire.application.testing.Samples.BLOG;
import static dev.flagwire.application.testing.Samples.DEV;
import static dev.flagwire.application.testing.Samples.PROD;
import static dev.flagwire.application.testing.Samples.SHOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.flagwire.application.port.out.AuditQuery;
import dev.flagwire.application.testing.Samples;
import dev.flagwire.domain.audit.AuditEntry;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public abstract class AuditLogContract extends AdapterContract {

  private static AuditQuery all() {
    return AuditQuery.forProject(SHOP, 100);
  }

  @Test
  void returnsTheNewestEntryFirstEvenWhenTimestampsAreEqual() {
    this.adapters.auditLog().append(Samples.audit("first", SHOP, DEV, "a"));
    this.adapters.auditLog().append(Samples.audit("second", SHOP, DEV, "a"));
    this.adapters.auditLog().append(Samples.audit("third", SHOP, DEV, "a"));

    assertThat(this.adapters.auditLog().find(all()))
        .extracting(AuditEntry::id)
        .containsExactly("third", "second", "first");
  }

  @Test
  void keepsAuthorBeforeAfterAndTimestampOfAnEntry() {
    AuditEntry entry = Samples.audit("one", SHOP, DEV, "checkout");

    this.adapters.auditLog().append(entry);

    AuditEntry stored = this.adapters.auditLog().find(all()).get(0);
    assertThat(stored).isEqualTo(entry);
    assertThat(stored.author()).isEqualTo("ann");
    assertThat(stored.before()).isPresent();
    assertThat(stored.after()).isPresent();
    assertThat(stored.at()).isEqualTo(Samples.NOW);
  }

  @Test
  void filtersByProjectEnvironmentAndEntity() {
    this.adapters.auditLog().append(Samples.audit("a", SHOP, DEV, "checkout"));
    this.adapters.auditLog().append(Samples.audit("b", SHOP, PROD, "checkout"));
    this.adapters.auditLog().append(Samples.audit("c", SHOP, DEV, "search"));
    this.adapters.auditLog().append(Samples.audit("d", BLOG, DEV, "checkout"));
    this.adapters.auditLog().append(Samples.audit("e", SHOP, null, "checkout"));

    assertThat(this.adapters.auditLog().find(all()))
        .extracting(AuditEntry::id)
        .containsExactly("e", "c", "b", "a");
    assertThat(this.adapters.auditLog().find(all().inEnvironment(DEV)))
        .extracting(AuditEntry::id)
        .containsExactly("c", "a");
    assertThat(this.adapters.auditLog().find(all().aboutEntity("checkout")))
        .extracting(AuditEntry::id)
        .containsExactly("e", "b", "a");
    assertThat(this.adapters.auditLog().find(all().inEnvironment(DEV).aboutEntity("checkout")))
        .extracting(AuditEntry::id)
        .containsExactly("a");
  }

  @Test
  void pagesWithLimitAndOffset() {
    IntStream.range(0, 10)
        .forEach(
            index -> this.adapters.auditLog().append(Samples.audit("e" + index, SHOP, DEV, "a")));

    assertThat(this.adapters.auditLog().find(AuditQuery.forProject(SHOP, 3)))
        .extracting(AuditEntry::id)
        .containsExactly("e9", "e8", "e7");
    assertThat(this.adapters.auditLog().find(AuditQuery.forProject(SHOP, 3).skipping(8)))
        .extracting(AuditEntry::id)
        .containsExactly("e1", "e0");
  }

  @Test
  void rollsBackAnAppend() {
    assertThatThrownBy(
            () ->
                this.adapters
                    .transactions()
                    .inTransaction(
                        () -> {
                          this.adapters.auditLog().append(Samples.audit("lost", SHOP, DEV, "a"));
                          throw new IllegalStateException("boom");
                        }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(this.adapters.auditLog().find(all())).isEmpty();
  }
}
