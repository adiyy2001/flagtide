package dev.flagwire.domain.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class ConformanceSuite {

  record Case(String id, Supplier<Optional<String>> check) {}

  record Report(int total, List<String> failures) {
    int passed() {
      return this.total - this.failures.size();
    }

    boolean succeeded() {
      return this.total > 0 && this.failures.isEmpty();
    }

    String summaryLine() {
      return "conformance java cases="
          + this.total
          + " passed="
          + this.passed()
          + " failed="
          + this.failures.size();
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Evaluator EVALUATOR = new Evaluator();

  private ConformanceSuite() {}

  static List<Case> load(Path vectorsDirectory) {
    List<Path> files;
    try (Stream<Path> listing = Files.list(vectorsDirectory)) {
      files =
          listing.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    List<Case> cases = new ArrayList<>();
    files.forEach(file -> cases.addAll(casesOf(file)));
    return cases;
  }

  static Report run(Path vectorsDirectory) {
    List<Case> cases = load(vectorsDirectory);
    List<String> failures = new ArrayList<>();
    cases.forEach(
        entry -> {
          Optional<String> problem = attempt(entry);
          problem.ifPresent(message -> failures.add(entry.id() + ": " + message));
        });
    return new Report(cases.size(), failures);
  }

  static Optional<String> attempt(Case entry) {
    try {
      return entry.check().get();
    } catch (RuntimeException exception) {
      return Optional.of("threw " + exception);
    }
  }

  private static List<Case> casesOf(Path file) {
    JsonNode document = read(file);
    String name = file.getFileName().toString();
    return switch (name) {
      case "murmur3.json" -> murmurCases(document);
      case "bucket.json" -> bucketCases(document);
      case "utf8.json" -> utf8Cases(document);
      case "semver.json" -> semverCases(document);
      default ->
          name.startsWith("eval-")
              ? evaluationCases(document)
              : List.of(new Case(name, () -> Optional.of("unknown vector file")));
    };
  }

  private static JsonNode read(Path file) {
    try {
      return MAPPER.readTree(Files.readString(file));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static List<Case> murmurCases(JsonNode document) {
    List<Case> cases = new ArrayList<>();
    document
        .get("vectors")
        .forEach(
            vector ->
                cases.add(
                    new Case(
                        vector.get("id").textValue(),
                        () ->
                            expectEqual(
                                Murmur3X86x32.hashUnsigned(
                                    bytes(vector.get("hex").textValue()),
                                    vector.get("seed").asInt()),
                                hex32(vector.get("hash").textValue())))));
    cases.add(
        new Case(
            "murmur3-verification",
            () ->
                expectEqual(
                    verificationValue(),
                    hex32(document.get("verification").get("expected").textValue()))));
    return cases;
  }

  private static long verificationValue() {
    byte[] collected = new byte[256 * 4];
    for (int length = 0; length < 256; length++) {
      byte[] key = new byte[length];
      for (int index = 0; index < length; index++) {
        key[index] = (byte) index;
      }
      int hash = Murmur3X86x32.hash(key, 256 - length);
      for (int part = 0; part < 4; part++) {
        collected[length * 4 + part] = (byte) (hash >>> (8 * part));
      }
    }
    return Murmur3X86x32.hashUnsigned(collected, 0);
  }

  private static List<Case> bucketCases(JsonNode document) {
    return VectorReader.list(
        document.get("cases"),
        entry ->
            new Case(
                entry.get("id").textValue(),
                () -> {
                  String flagKey = entry.get("flagKey").textValue();
                  String salt = entry.get("salt").textValue();
                  String contextKey = entry.get("contextKey").textValue();
                  byte[] input = Utf8.encode(flagKey + "." + salt + "." + contextKey);
                  Optional<String> hashProblem =
                      expectEqual(
                          Murmur3X86x32.hashUnsigned(input, 0),
                          hex32(entry.get("hash").textValue()));
                  return hashProblem.isPresent()
                      ? hashProblem
                      : expectEqual(
                          Bucketing.bucket(flagKey, salt, contextKey),
                          entry.get("bucket").intValue());
                }));
  }

  private static List<Case> utf8Cases(JsonNode document) {
    return VectorReader.list(
        document.get("cases"),
        entry ->
            new Case(
                entry.get("id").textValue(),
                () ->
                    expectEqual(
                        HexFormat.of().formatHex(Utf8.encode(entry.get("text").textValue())),
                        entry.get("hex").textValue())));
  }

  private static List<Case> semverCases(JsonNode document) {
    List<Case> cases = new ArrayList<>();
    document
        .get("valid")
        .forEach(
            entry -> cases.add(new Case(entry.get("id").textValue(), () -> validVersion(entry))));
    document
        .get("invalid")
        .forEach(
            entry ->
                cases.add(
                    new Case(
                        entry.get("id").textValue(),
                        () ->
                            expectEqual(
                                SemanticVersion.parse(entry.get("input").textValue()),
                                Optional.empty()))));
    document
        .get("compare")
        .forEach(
            entry -> cases.add(new Case(entry.get("id").textValue(), () -> comparison(entry))));
    return cases;
  }

  private static Optional<String> validVersion(JsonNode entry) {
    Optional<SemanticVersion> parsed = SemanticVersion.parse(entry.get("input").textValue());
    if (parsed.isEmpty()) {
      return Optional.of("parsed as invalid");
    }
    SemanticVersion version = parsed.get();
    return expectEqual(
        List.of(version.major(), version.minor(), version.patch(), version.prerelease()),
        List.of(
            entry.get("major").textValue(),
            entry.get("minor").textValue(),
            entry.get("patch").textValue(),
            VectorReader.list(entry.get("prerelease"), JsonNode::textValue)));
  }

  private static Optional<String> comparison(JsonNode entry) {
    Optional<SemanticVersion> left = SemanticVersion.parse(entry.get("a").textValue());
    Optional<SemanticVersion> right = SemanticVersion.parse(entry.get("b").textValue());
    if (left.isEmpty() || right.isEmpty()) {
      return Optional.of("a side parsed as invalid");
    }
    int expected = entry.get("result").intValue();
    Optional<String> forward =
        expectEqual(Integer.signum(left.get().compareTo(right.get())), expected);
    return forward.isPresent()
        ? forward
        : expectEqual(Integer.signum(right.get().compareTo(left.get())), -expected);
  }

  private static List<Case> evaluationCases(JsonNode document) {
    return VectorReader.list(
        document.get("cases"),
        entry ->
            new Case(
                entry.get("id").textValue(),
                () -> {
                  FlagConfig flag = VectorReader.flag(entry.get("flag"));
                  Map<String, Segment> segments =
                      Segment.indexByKey(
                          VectorReader.list(entry.get("segments"), VectorReader::segment));
                  EvaluationContext context = VectorReader.context(entry.get("context"));
                  return expectEqual(
                      EVALUATOR.evaluate(flag, context, segments),
                      VectorReader.result(entry.get("expected")));
                }));
  }

  private static Optional<String> expectEqual(Object actual, Object expected) {
    return actual.equals(expected)
        ? Optional.empty()
        : Optional.of("expected " + expected + " but got " + actual);
  }

  private static byte[] bytes(String hex) {
    return HexFormat.of().parseHex(hex);
  }

  private static long hex32(String text) {
    return Long.parseLong(text.substring(2), 16);
  }
}
