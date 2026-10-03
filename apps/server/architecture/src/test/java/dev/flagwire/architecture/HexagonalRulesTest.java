package dev.flagwire.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HexagonalRulesTest {

  private static JavaClasses classes;

  @BeforeAll
  static void importProductionClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(location -> !location.contains("/architecture/target/classes/"))
            .importPackages("dev.flagwire");
  }

  private static void holds(ArchRule rule) {
    rule.check(classes);
  }

  @Test
  void theImportSeesEveryLayer() {
    assertThat(
            classes.stream()
                .filter(type -> type.getPackageName().startsWith("dev.flagwire.domain")))
        .hasSizeGreaterThan(50);
    assertThat(
            classes.stream()
                .filter(type -> type.getPackageName().startsWith("dev.flagwire.application")))
        .hasSizeGreaterThan(50);
    assertThat(
            classes.stream()
                .filter(type -> type.getPackageName().startsWith("dev.flagwire.adapter.memory")))
        .isNotEmpty();
  }

  @Test
  void theDomainDependsOnlyOnItselfAndTheJdk() {
    holds(Rules.DOMAIN_DEPENDS_ONLY_ON_ITSELF_AND_THE_JDK);
  }

  @Test
  void theDomainDoesNotTouchTheOutsideWorld() {
    holds(Rules.DOMAIN_DOES_NOT_TOUCH_THE_OUTSIDE_WORLD);
  }

  @Test
  void theApplicationDependsOnlyOnTheDomainAndTheJdk() {
    holds(Rules.APPLICATION_DEPENDS_ONLY_ON_THE_DOMAIN_AND_THE_JDK);
  }

  @Test
  void adaptersDoNotDependOnEachOther() {
    holds(Rules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER);
  }

  @Test
  void onlyBootstrapMayDependOnAdapters() {
    holds(Rules.ONLY_BOOTSTRAP_DEPENDS_ON_ADAPTERS);
  }

  @Test
  void inboundAdaptersGoThroughUseCasesAndNeverTouchOutboundPorts() {
    holds(Rules.INBOUND_ADAPTERS_NEVER_TOUCH_OUTBOUND_PORTS);
  }

  @Test
  void adaptersImplementPortsAndNeverCallUseCases() {
    holds(Rules.ADAPTERS_NEVER_CALL_USE_CASES);
  }

  @Test
  void outboundPortsAreInterfacesRecordsOrEnums() {
    holds(Rules.OUTBOUND_PORTS_ARE_INTERFACES_RECORDS_OR_ENUMS);
  }

  @Test
  void theDomainHasNoPackageCycles() {
    holds(Rules.DOMAIN_HAS_NO_PACKAGE_CYCLES);
  }

  @Test
  void theApplicationHasNoPackageCycles() {
    holds(Rules.APPLICATION_HAS_NO_PACKAGE_CYCLES);
  }
}
