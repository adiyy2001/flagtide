package dev.flagwire.adapter.memory;

import dev.flagwire.application.port.out.TransactionRunner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

public final class MemoryDatabase implements TransactionRunner {

  private static final class TransactionLog {

    private final Deque<Runnable> undo = new ArrayDeque<>();
    private final List<Runnable> afterCommit = new ArrayList<>();
    private final boolean readOnly;

    TransactionLog(boolean readOnly) {
      this.readOnly = readOnly;
    }
  }

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
  private final ThreadLocal<TransactionLog> active = new ThreadLocal<>();

  @Override
  public <T> T inTransaction(Supplier<T> work) {
    TransactionLog existing = this.active.get();
    if (existing != null) {
      this.requireWritable(existing);
      return work.get();
    }
    TransactionLog log = new TransactionLog(false);
    T result = this.run(log, this.lock.writeLock(), work);
    log.afterCommit.forEach(Runnable::run);
    return result;
  }

  @Override
  public <T> T inReadOnlyTransaction(Supplier<T> work) {
    if (this.active.get() != null) {
      return work.get();
    }
    return this.run(new TransactionLog(true), this.lock.readLock(), work);
  }

  <T> T read(Supplier<T> action) {
    if (this.active.get() != null) {
      return action.get();
    }
    this.lock.readLock().lock();
    try {
      return action.get();
    } finally {
      this.lock.readLock().unlock();
    }
  }

  <T> T write(Supplier<T> action) {
    return this.inTransaction(action);
  }

  void onRollback(Runnable undo) {
    this.active.get().undo.push(undo);
  }

  void afterCommit(Runnable action) {
    this.active.get().afterCommit.add(action);
  }

  private <T> T run(TransactionLog log, Lock guard, Supplier<T> work) {
    guard.lock();
    this.active.set(log);
    try {
      return work.get();
    } catch (RuntimeException | Error failure) {
      log.undo.forEach(Runnable::run);
      throw failure;
    } finally {
      this.active.remove();
      guard.unlock();
    }
  }

  private void requireWritable(TransactionLog log) {
    if (log.readOnly) {
      throw new IllegalStateException("a read-only transaction cannot write");
    }
  }
}
