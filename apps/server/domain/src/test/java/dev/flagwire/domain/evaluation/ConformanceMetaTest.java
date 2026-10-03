package dev.flagwire.domain.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConformanceMetaTest {

  private Path scratch;

  private Path copyOfVectors() throws IOException {
    this.scratch = Files.createTempDirectory("flagwire-vectors-");
    try (Stream<Path> listing = Files.list(ConformanceTest.vectors())) {
      for (Path file : listing.toList()) {
        Files.copy(file, this.scratch.resolve(file.getFileName()));
      }
    }
    return this.scratch;
  }

  private ConformanceSuite.Report runCorrupted(String file, String from, String to)
      throws IOException {
    Path directory = this.copyOfVectors();
    Path target = directory.resolve(file);
    String original = Files.readString(target);
    String corrupted = original.replace(from, to);
    assertThat(corrupted).isNotEqualTo(original);
    Files.writeString(target, corrupted);
    return ConformanceSuite.run(directory);
  }

  @AfterEach
  void removeScratch() throws IOException {
    if (this.scratch == null) {
      return;
    }
    try (Stream<Path> walk = Files.walk(this.scratch)) {
      for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  @Test
  void passesOnTheUntouchedCopy() throws IOException {
    assertThat(ConformanceSuite.run(this.copyOfVectors()).succeeded()).isTrue();
  }

  @Test
  void failsWhenAnEvaluationVectorExpectsAnotherReason() throws IOException {
    ConformanceSuite.Report report =
        this.runCorrupted("eval-core.json", "\"reason\":\"OFF\"", "\"reason\":\"FALLTHROUGH\"");

    assertThat(report.succeeded()).isFalse();
    assertThat(report.failures()).isNotEmpty();
  }

  @Test
  void failsWhenAnEvaluationVectorExpectsAnotherBucket() throws IOException {
    assertThat(
            this.runCorrupted("eval-rollouts.json", "\"bucket\":24999", "\"bucket\":24998")
                .succeeded())
        .isFalse();
  }

  @Test
  void failsWhenAHashVectorIsWrong() throws IOException {
    assertThat(
            this.runCorrupted("murmur3.json", "\"hash\":\"0xBA6BD213\"", "\"hash\":\"0xBA6BD214\"")
                .succeeded())
        .isFalse();
  }

  @Test
  void failsWhenTheVerificationValueIsWrong() throws IOException {
    ConformanceSuite.Report report = this.runCorrupted("murmur3.json", "0xB0F57EE3", "0xB0F57EE4");

    assertThat(report.failures()).singleElement().asString().contains("murmur3-verification");
  }

  @Test
  void failsWhenASemverComparisonIsFlipped() throws IOException {
    assertThat(this.runCorrupted("semver.json", "\"result\":-1", "\"result\":1").succeeded())
        .isFalse();
  }

  @Test
  void failsOnAnUnknownVectorFile() throws IOException {
    Path directory = this.copyOfVectors();
    Files.writeString(directory.resolve("mystery.json"), "{}");

    assertThat(ConformanceSuite.run(directory).failures())
        .singleElement()
        .asString()
        .contains("unknown vector file");
  }

  @Test
  void failsWhenThereAreNoVectors() throws IOException {
    this.scratch = Files.createTempDirectory("flagwire-empty-");

    assertThat(ConformanceSuite.run(this.scratch).succeeded()).isFalse();
  }

  @Test
  void identityTransformKeepsTheSuiteGreen() throws IOException {
    UnaryOperator<String> identity = text -> text;

    assertThat(identity.apply("x")).isEqualTo("x");
    assertThat(ConformanceSuite.run(this.copyOfVectors()).failures()).isEmpty();
  }
}
