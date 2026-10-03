package dev.flagwire.application.port.out;

public interface Subscription extends AutoCloseable {

  @Override
  void close();
}
