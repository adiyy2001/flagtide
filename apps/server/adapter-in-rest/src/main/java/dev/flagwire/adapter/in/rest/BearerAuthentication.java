package dev.flagwire.adapter.in.rest;

import dev.flagwire.application.security.Principal;
import dev.flagwire.application.usecase.Authenticate;
import dev.flagwire.domain.error.FlagwireException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.Locale;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

public class BearerAuthentication {

  private static final String SCHEME = "bearer ";

  private final Authenticate authenticate;
  private final Caller caller;

  @Inject
  public BearerAuthentication(Authenticate authenticate, Caller caller) {
    this.authenticate = authenticate;
    this.caller = caller;
  }

  @ServerRequestFilter(priority = Priorities.AUTHENTICATION)
  public void authenticate(ContainerRequestContext request) {
    if (!isProtected(request.getUriInfo().getPath())) {
      return;
    }
    String header = request.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(SCHEME)) {
      throw FlagwireException.unauthorized("send the key as Authorization: Bearer <key>");
    }
    Principal principal = this.authenticate.execute(header.substring(SCHEME.length()).trim());
    this.caller.authenticated(principal);
  }

  private static boolean isProtected(String path) {
    return path.startsWith("/api/") || path.startsWith("/sdk/");
  }
}
