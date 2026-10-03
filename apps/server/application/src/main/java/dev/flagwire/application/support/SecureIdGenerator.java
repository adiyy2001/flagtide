package dev.flagwire.application.support;

import dev.flagwire.application.port.out.IdGenerator;
import dev.flagwire.domain.value.Salt;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public final class SecureIdGenerator implements IdGenerator {

  private static final int SALT_BYTES = 4;
  private static final int TOKEN_BYTES = 24;

  private final SecureRandom random = new SecureRandom();

  @Override
  public String newId() {
    return UUID.randomUUID().toString();
  }

  @Override
  public Salt newSalt() {
    byte[] bytes = new byte[SALT_BYTES];
    this.random.nextBytes(bytes);
    return new Salt(HexFormat.of().formatHex(bytes));
  }

  @Override
  public String newToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    this.random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
