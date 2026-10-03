package dev.flagwire.adapter.in.websocket;

public final class CloseCodes {

  public static final int BAD_FRAME = 4400;
  public static final int UNAUTHORIZED = 4401;
  public static final int ORIGIN_NOT_ALLOWED = 4403;
  public static final int NO_HELLO_IN_TIME = 4408;
  public static final int SLOW_CONSUMER = 4429;
  public static final int UNAVAILABLE = 1013;

  private CloseCodes() {}
}
