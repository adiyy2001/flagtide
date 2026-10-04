package dev.flagtide.bootstrap;

import static dev.flagtide.bootstrap.Requests.as;
import static dev.flagtide.bootstrap.Requests.booleanFlag;
import static org.assertj.core.api.Assertions.assertThat;

import dev.flagtide.application.change.ChangeLogEntry;
import dev.flagtide.application.port.out.ChangeLog;
import dev.flagtide.application.usecase.CreateProject;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.ProjectKey;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class ConcurrencyTest {

  private static final int WRITERS = 32;
  private static final int ROUNDS = 12;

  @Inject CreateProject createProject;
  @Inject ChangeLog changeLog;

  TestProject project;

  @BeforeEach
  void freshProject() {
    this.project = TestProject.create(this.createProject);
  }

  private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      CountDownLatch ready = new CountDownLatch(tasks.size());
      CountDownLatch go = new CountDownLatch(1);
      List<Future<T>> futures = new ArrayList<>();
      for (Callable<T> task : tasks) {
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return task.call();
                }));
      }
      ready.await();
      go.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private int post(String path, String body) {
    return as(this.project.devAdmin()).body(body).post(path).statusCode();
  }

  private int put(String path, String ifMatch, String body) {
    return as(this.project.devAdmin())
        .header("If-Match", ifMatch)
        .body(body)
        .put(path)
        .statusCode();
  }

  @Test
  void twoParallelEditsOfOneFlagGiveExactlyOneConflict() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      String key = "race-" + round;
      assertThat(this.post(this.project.flagsPath(), booleanFlag(key))).isEqualTo(201);
      String flag = this.project.flagsPath() + "/" + key;
      List<Callable<Integer>> edits =
          List.of(
              () -> this.put(flag + "/environments/dev/enabled", "\"1\"", "{\"enabled\":true}"),
              () ->
                  this.put(
                      flag,
                      "\"1\"",
                      """
                      {"description":"edited","variants":[{"key":"on","value":true},{"key":"off","value":false}]}
                      """));

      List<Integer> statuses = this.runTogether(edits);

      assertThat(statuses).containsExactlyInAnyOrder(200, 409);
      assertThat(as(this.project.devAdmin()).get(flag).header("ETag")).isEqualTo("\"2\"");
    }
  }

  @Test
  void thirtyTwoParallelWritersToOneEnvironmentLeaveNoGapAndNoDuplicate() throws Exception {
    EnvironmentRef dev =
        new EnvironmentRef(new ProjectKey(this.project.key()), new EnvironmentKey("dev"));
    EnvironmentVersion before = this.changeLog.currentVersion(dev);
    List<Callable<Integer>> writers =
        IntStream.range(0, WRITERS)
            .<Callable<Integer>>mapToObj(
                index -> () -> this.post(this.project.flagsPath(), booleanFlag("writer-" + index)))
            .toList();

    List<Integer> statuses = this.runTogether(writers);

    assertThat(statuses).containsOnly(201);
    List<Long> versions =
        this.changeLog.entriesAfter(dev, before).stream()
            .map(ChangeLogEntry::version)
            .map(EnvironmentVersion::value)
            .toList();
    assertThat(versions)
        .containsExactlyElementsOf(
            IntStream.rangeClosed(1, WRITERS).mapToObj(offset -> before.value() + offset).toList());
    assertThat(this.changeLog.currentVersion(dev).value()).isEqualTo(before.value() + WRITERS);
  }
}
