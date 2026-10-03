package dev.flagwire.adapter.postgres;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.Revision;
import java.util.OptionalLong;

final class RevisionConflicts {

  private RevisionConflicts() {}

  static FlagwireException conflict(
      String entity, String key, OptionalLong stored, Revision expected) {
    return FlagwireException.conflict(
        entity
            + " "
            + key
            + " is at revision "
            + stored.orElse(Revision.NONE.value())
            + " but the write expected revision "
            + expected.value());
  }
}
