package dev.flagwire.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

final class Rules {

  static final String DOMAIN = "dev.flagwire.domain..";
  static final String APPLICATION = "dev.flagwire.application..";
  static final String ADAPTERS = "dev.flagwire.adapter..";
  static final String BOOTSTRAP = "dev.flagwire.bootstrap..";
  static final String INBOUND_ADAPTERS = "dev.flagwire.adapter.in..";
  static final String OUTBOUND_PORTS = "dev.flagwire.application.port.out..";
  static final String USE_CASES = "dev.flagwire.application.usecase..";

  static final ArchRule DOMAIN_DEPENDS_ONLY_ON_ITSELF_AND_THE_JDK =
      classes()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(DOMAIN, "java..");

  static final ArchRule DOMAIN_DOES_NOT_TOUCH_THE_OUTSIDE_WORLD =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "java.io..", "java.net..", "java.sql..", "java.nio.file..", "java.util.logging..");

  static final ArchRule APPLICATION_DEPENDS_ONLY_ON_THE_DOMAIN_AND_THE_JDK =
      classes()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(APPLICATION, DOMAIN, "java..");

  static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .matching("dev.flagwire.adapter.(*)..")
          .should()
          .notDependOnEachOther()
          .allowEmptyShould(true);

  static final ArchRule ONLY_BOOTSTRAP_DEPENDS_ON_ADAPTERS =
      noClasses()
          .that()
          .resideOutsideOfPackages(ADAPTERS, BOOTSTRAP)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ADAPTERS)
          .allowEmptyShould(true);

  static final ArchRule INBOUND_ADAPTERS_NEVER_TOUCH_OUTBOUND_PORTS =
      noClasses()
          .that()
          .resideInAPackage(INBOUND_ADAPTERS)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(OUTBOUND_PORTS)
          .allowEmptyShould(true);

  static final ArchRule ADAPTERS_NEVER_CALL_USE_CASES =
      noClasses()
          .that()
          .resideInAPackage(ADAPTERS)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(USE_CASES)
          .allowEmptyShould(true);

  static final ArchRule OUTBOUND_PORTS_ARE_INTERFACES_RECORDS_OR_ENUMS =
      classes()
          .that()
          .resideInAPackage(OUTBOUND_PORTS)
          .should(
              new ArchCondition<JavaClass>("be an interface, a record or an enum") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                  boolean allowed = item.isInterface() || item.isRecord() || item.isEnum();
                  events.add(
                      new SimpleConditionEvent(
                          item,
                          allowed,
                          item.getName() + (allowed ? " is allowed" : " is a class")));
                }
              });

  static final ArchRule DOMAIN_HAS_NO_PACKAGE_CYCLES =
      slices().matching("dev.flagwire.domain.(*)..").should().beFreeOfCycles();

  static final ArchRule APPLICATION_HAS_NO_PACKAGE_CYCLES =
      slices().matching("dev.flagwire.application.(*)..").should().beFreeOfCycles();

  private Rules() {}
}
