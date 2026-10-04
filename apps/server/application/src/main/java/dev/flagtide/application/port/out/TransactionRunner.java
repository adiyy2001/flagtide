package dev.flagtide.application.port.out;

import java.util.function.Supplier;

public interface TransactionRunner {

  <T> T inTransaction(Supplier<T> work);

  <T> T inReadOnlyTransaction(Supplier<T> work);
}
