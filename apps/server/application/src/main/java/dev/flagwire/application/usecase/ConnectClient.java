package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.TimeSource;
import dev.flagwire.application.security.Principal;
import dev.flagwire.application.stream.ChangeStreams;
import dev.flagwire.application.stream.StreamClient;
import dev.flagwire.application.stream.StreamSession;
import dev.flagwire.domain.access.ApiKeyKind;
import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.EnvironmentVersion;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConnectClient {

  public record Command(String sdkKey, Optional<EnvironmentVersion> version, StreamClient client) {}

  private record CachedPrincipal(Principal principal, ZonedDateTime expiresAt) {}

  private final Authenticate authenticate;
  private final ChangeStreams streams;
  private final TimeSource timeSource;
  private final Duration keyCacheTtl;
  private final Map<String, CachedPrincipal> keys = new ConcurrentHashMap<>();

  public ConnectClient(
      Authenticate authenticate,
      ChangeStreams streams,
      TimeSource timeSource,
      Duration keyCacheTtl) {
    this.authenticate = authenticate;
    this.streams = streams;
    this.timeSource = timeSource;
    this.keyCacheTtl = keyCacheTtl;
  }

  public StreamSession execute(Command command) {
    Principal principal = this.principalOf(command.sdkKey());
    return new StreamSession(
        principal, this.streams.connect(principal, command.version(), command.client()));
  }

  private Principal principalOf(String sdkKey) {
    ZonedDateTime now = this.timeSource.now();
    CachedPrincipal cached = this.keys.get(sdkKey);
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.principal();
    }
    this.keys.remove(sdkKey);
    Principal principal = this.authenticate.execute(sdkKey);
    if (principal.kind() != ApiKeyKind.SDK) {
      throw FlagwireException.unauthorized("the stream needs an SDK key");
    }
    this.keys.put(sdkKey, new CachedPrincipal(principal, now.plus(this.keyCacheTtl)));
    return principal;
  }
}
