package dev.flagtide.adapter.out.postgres;

import io.vertx.pgclient.PgConnectOptions;
import java.net.URI;

public final class ListenerConnection {

  private static final String JDBC_PREFIX = "jdbc:";
  private static final int DEFAULT_PORT = 5432;

  private ListenerConnection() {}

  public static PgConnectOptions optionsFromJdbcUrl(
      String jdbcUrl, String user, String password, String applicationName) {
    String plain =
        jdbcUrl.startsWith(JDBC_PREFIX) ? jdbcUrl.substring(JDBC_PREFIX.length()) : jdbcUrl;
    URI uri = URI.create(plain);
    PgConnectOptions options =
        new PgConnectOptions()
            .setHost(uri.getHost())
            .setPort(uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT)
            .setDatabase(uri.getPath().substring(1))
            .setUser(user)
            .setPassword(password);
    options.getProperties().put("application_name", applicationName);
    return options;
  }
}
