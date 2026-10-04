package dev.flagtide.application.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public final class Concurrently {

  public record Outcome<T>(T value, Throwable failure) {

    public boolean succeeded() {
      return this.failure == null;
    }
  }

  private Concurrently() {}

  public static <T> List<Outcome<T>> run(int threads, Supplier<T> work) {
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      CountDownLatch ready = new CountDownLatch(threads);
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Outcome<T>>> futures = new ArrayList<>();
      for (int index = 0; index < threads; index++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  try {
                    return new Outcome<>(work.get(), null);
                  } catch (RuntimeException failure) {
                    return new Outcome<>(null, failure);
                  }
                }));
      }
      ready.await();
      go.countDown();
      List<Outcome<T>> outcomes = new ArrayList<>();
      for (Future<Outcome<T>> future : futures) {
        outcomes.add(future.get());
      }
      return outcomes;
    } catch (InterruptedException | ExecutionException failure) {
      throw new IllegalStateException(failure);
    } finally {
      executor.shutdownNow();
    }
  }
}
