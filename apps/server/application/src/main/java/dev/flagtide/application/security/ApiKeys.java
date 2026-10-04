package dev.flagtide.application.security;

import dev.flagtide.domain.access.ApiKeyKind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

public final class ApiKeys {

  private static final String ADMIN_PREFIX = "fwa_";
  private static final String SDK_PREFIX = "fws_";

  private ApiKeys() {}

  public static String secret(ApiKeyKind kind, String token) {
    return (kind == ApiKeyKind.ADMIN ? ADMIN_PREFIX : SDK_PREFIX) + token;
  }

  public static String lookupFor(ApiKeyKind kind, String secret) {
    return kind == ApiKeyKind.ADMIN ? sha256(secret) : secret;
  }

  public static Optional<ApiKeyKind> kindOf(String secret) {
    if (secret.startsWith(ADMIN_PREFIX)) {
      return Optional.of(ApiKeyKind.ADMIN);
    }
    if (secret.startsWith(SDK_PREFIX)) {
      return Optional.of(ApiKeyKind.SDK);
    }
    return Optional.empty();
  }

  static String sha256(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException missing) {
      throw new IllegalStateException("SHA-256 is part of every JDK", missing);
    }
  }
}
