package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.SegmentRepository;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.segment.Segment;
import dev.flagwire.domain.value.EnvironmentKey;
import dev.flagwire.domain.value.EnvironmentRef;
import java.util.List;

public final class ListSegments {

  private final SegmentRepository segments;
  private final Authorizer authorizer;

  public ListSegments(SegmentRepository segments, Authorizer authorizer) {
    this.segments = segments;
    this.authorizer = authorizer;
  }

  public List<Segment> execute(Principal principal, EnvironmentKey environment) {
    this.authorizer.requireAdmin(principal);
    return this.segments.findAll(new EnvironmentRef(principal.project(), environment));
  }
}
