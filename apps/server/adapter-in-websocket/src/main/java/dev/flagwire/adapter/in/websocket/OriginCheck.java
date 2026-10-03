package dev.flagwire.adapter.in.websocket;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class OriginCheck implements HttpUpgradeCheck {

  private static final String ORIGIN = "Origin";
  private static final String HOST = "Host";
  private static final int FORBIDDEN = 403;

  private final List<String> allowed;

  @Inject
  public OriginCheck(StreamSettings settings) {
    this.allowed = settings.allowedOrigins().orElse(List.of());
  }

  @Override
  public Uni<CheckResult> perform(HttpUpgradeContext context) {
    String origin = context.httpRequest().getHeader(ORIGIN);
    if (origin == null
        || this.allowed.contains(origin)
        || sameOrigin(origin, context.httpRequest().getHeader(HOST))) {
      return CheckResult.permitUpgrade();
    }
    return CheckResult.rejectUpgrade(FORBIDDEN);
  }

  static boolean sameOrigin(String origin, String host) {
    if (host == null) {
      return false;
    }
    int schemeEnd = origin.indexOf("://");
    return schemeEnd > 0 && origin.substring(schemeEnd + 3).equalsIgnoreCase(host);
  }
}
