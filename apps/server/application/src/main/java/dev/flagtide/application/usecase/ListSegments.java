package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.SegmentRepository;
import dev.flagtide.application.security.Authorizer;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.segment.Segment;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentRef;
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
