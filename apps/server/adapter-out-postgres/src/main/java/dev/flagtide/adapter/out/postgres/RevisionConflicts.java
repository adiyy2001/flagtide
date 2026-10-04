package dev.flagtide.adapter.out.postgres;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.Revision;
import java.util.OptionalLong;

final class RevisionConflicts {

  private RevisionConflicts() {}

  static FlagtideException conflict(
      String entity, String key, OptionalLong stored, Revision expected) {
    return FlagtideException.conflict(
        entity
            + " "
            + key
            + " is at revision "
            + stored.orElse(Revision.NONE.value())
            + " but the write expected revision "
            + expected.value());
  }
}
