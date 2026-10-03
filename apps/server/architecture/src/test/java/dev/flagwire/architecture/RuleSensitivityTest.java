package dev.flagwire.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RuleSensitivityTest {

  private static JavaClasses everything;

  @BeforeAll
  static void importProductionAndFixtureClasses() {
    everything =
        new ClassFileImporter()
            .withImportOption(location -> !location.contains("/architecture/target/classes/"))
            .importPackages("dev.flagwire");
  }

  private static void violates(ArchRule rule) {
    assertThatThrownBy(() -> rule.check(everything)).isInstanceOf(AssertionError.class);
  }

  @Test
  void aDomainClassThatReachesTheApplicationIsCaught() {
    violates(Rules.DOMAIN_DEPENDS_ONLY_ON_ITSELF_AND_THE_JDK);
  }

  @Test
  void aDomainClassThatTouchesTheFileSystemIsCaught() {
    violates(Rules.DOMAIN_DOES_NOT_TOUCH_THE_OUTSIDE_WORLD);
  }

  @Test
  void anApplicationClassThatReachesAnAdapterIsCaught() {
    violates(Rules.APPLICATION_DEPENDS_ONLY_ON_THE_DOMAIN_AND_THE_JDK);
  }

  @Test
  void anAdapterThatReachesAnotherAdapterIsCaught() {
    violates(Rules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER);
  }

  @Test
  void anAdapterOutsideBootstrapDependingOnAnAdapterIsCaught() {
    violates(Rules.ONLY_BOOTSTRAP_DEPENDS_ON_ADAPTERS);
  }

  @Test
  void anInboundAdapterUsingAnOutboundPortIsCaught() {
    violates(Rules.INBOUND_ADAPTERS_NEVER_TOUCH_OUTBOUND_PORTS);
  }

  @Test
  void anOutboundAdapterCallingAUseCaseIsCaught() {
    violates(Rules.OUTBOUND_ADAPTERS_NEVER_CALL_USE_CASES);
  }

  @Test
  void anInboundAdapterReachingAnOutboundAdapterIsCaught() {
    violates(Rules.INBOUND_ADAPTERS_DO_NOT_TOUCH_OUTBOUND_ADAPTERS);
  }

  @Test
  void anApplicationPackageCycleIsCaught() {
    violates(Rules.APPLICATION_HAS_NO_PACKAGE_CYCLES);
  }
}
