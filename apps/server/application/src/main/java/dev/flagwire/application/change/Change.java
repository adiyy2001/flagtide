package dev.flagwire.application.change;

import dev.flagwire.domain.evaluation.FlagConfig;
import dev.flagwire.domain.evaluation.Segment;

public sealed interface Change {

  String key();

  record FlagUpserted(FlagConfig config) implements Change {
    @Override
    public String key() {
      return this.config.key();
    }
  }

  record FlagRemoved(String key) implements Change {}

  record SegmentUpserted(Segment segment) implements Change {
    @Override
    public String key() {
      return this.segment.key();
    }
  }

  record SegmentRemoved(String key) implements Change {}
}
