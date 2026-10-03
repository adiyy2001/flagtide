package dev.flagwire.bootstrap;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ServerProcess implements AutoCloseable {

  private static final Duration STARTUP_PATIENCE = Duration.ofSeconds(90);

  private final Process process;
  private final int port;
  private final String name;

  private ServerProcess(Process process, int port, String name) {
    this.process = process;
    this.port = port;
    this.name = name;
  }

  static ServerProcess start(Path jar, String name, Map<String, String> environment)
      throws Exception {
    int port = freePort();
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-Xmx256m");
    command.add("-jar");
    command.add(jar.toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().putAll(environment);
    builder.environment().put("QUARKUS_HTTP_HOST", "127.0.0.1");
    builder.environment().put("QUARKUS_HTTP_PORT", Integer.toString(port));
    builder.environment().put("FLAGWIRE_INSTANCE_ID", name);
    builder.redirectErrorStream(true);
    builder.redirectOutput(jar.getParent().getParent().resolve(name + ".log").toFile());
    ServerProcess server = new ServerProcess(builder.start(), port, name);
    server.awaitReady();
    return server;
  }

  int port() {
    return this.port;
  }

  String name() {
    return this.name;
  }

  URI uri(String path) {
    return URI.create("http://127.0.0.1:" + this.port + path);
  }

  URI webSocketUri(String path) {
    return URI.create("ws://127.0.0.1:" + this.port + path);
  }

  private void awaitReady() throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest ready = HttpRequest.newBuilder(this.uri("/q/health/ready")).build();
    long deadline = System.nanoTime() + STARTUP_PATIENCE.toNanos();
    while (System.nanoTime() < deadline) {
      if (!this.process.isAlive()) {
        throw new IllegalStateException(this.name + " exited with " + this.process.exitValue());
      }
      try {
        if (client.send(ready, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
          return;
        }
      } catch (IOException notYet) {
        Thread.sleep(250);
      }
    }
    throw new IllegalStateException(this.name + " did not become ready");
  }

  private static int freePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  @Override
  public void close() {
    this.process.destroy();
    try {
      if (!this.process.waitFor(10, TimeUnit.SECONDS)) {
        this.process.destroyForcibly();
      }
    } catch (InterruptedException interrupted) {
      this.process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }
}
