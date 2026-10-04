package dev.flagtide.bootstrap;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

final class RawStreamClient implements AutoCloseable {

  private static final int CLOSE_OPCODE = 8;
  private static final int MASK_BIT = 0x80;

  private final Socket socket;
  private final DataInputStream in;
  private final OutputStream out;

  RawStreamClient(String host, int port, String origin) throws IOException {
    this.socket = new Socket();
    this.socket.connect(new InetSocketAddress(host, port), 5000);
    this.socket.setSoTimeout(30000);
    this.in = new DataInputStream(this.socket.getInputStream());
    this.out = this.socket.getOutputStream();
    this.handshake(host, port, origin);
  }

  private void handshake(String host, int port, String origin) throws IOException {
    byte[] nonce = new byte[16];
    new SecureRandom().nextBytes(nonce);
    String request =
        "GET /sdk/v1/stream HTTP/1.1\r\nHost: %s:%d\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\nOrigin: %s\r\n\r\n"
            .formatted(host, port, Base64.getEncoder().encodeToString(nonce), origin);
    this.out.write(request.getBytes(StandardCharsets.US_ASCII));
    this.out.flush();
    StringBuilder head = new StringBuilder();
    while (!head.toString().endsWith("\r\n\r\n")) {
      head.append((char) this.in.readUnsignedByte());
    }
    if (!head.toString().startsWith("HTTP/1.1 101")) {
      throw new IOException("upgrade refused: " + head);
    }
  }

  void sendText(String text) throws IOException {
    byte[] payload = text.getBytes(StandardCharsets.UTF_8);
    byte[] mask = new byte[4];
    new SecureRandom().nextBytes(mask);
    this.out.write(0x81);
    this.out.write(MASK_BIT | payload.length);
    this.out.write(mask);
    for (int index = 0; index < payload.length; index++) {
      this.out.write(payload[index] ^ mask[index % 4]);
    }
    this.out.flush();
  }

  int readUntilCloseCode() throws IOException {
    while (true) {
      int first = this.in.readUnsignedByte();
      int second = this.in.readUnsignedByte() & 0x7F;
      long length = second;
      if (second == 126) {
        length = this.in.readUnsignedShort();
      } else if (second == 127) {
        length = this.in.readLong();
      }
      if ((first & 0x0F) == CLOSE_OPCODE) {
        return this.in.readUnsignedShort();
      }
      this.in.skipNBytes(length);
    }
  }

  @Override
  public void close() throws IOException {
    this.socket.close();
  }
}
