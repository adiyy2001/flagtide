package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ConformanceTest {

  static Path vectors() {
    return Path.of(System.getProperty("flagwire.vectors", "../../spec/vectors")).normalize();
  }

  @TestFactory
  Stream<DynamicTest> everySharedVector() {
    return ConformanceSuite.load(vectors()).stream()
        .map(
            entry ->
                DynamicTest.dynamicTest(
                    entry.id(), () -> assertThat(ConformanceSuite.attempt(entry)).isEmpty()));
  }

  @Test
  void reportsTheSameSummaryLineAsTheOtherRunner() {
    ConformanceSuite.Report report = ConformanceSuite.run(vectors());

    System.out.println(report.summaryLine());

    assertThat(report.failures()).isEmpty();
    assertThat(report.total()).isGreaterThanOrEqualTo(200);
  }
}
