package dev.flagwire.adapter.memory;

import dev.flagwire.domain.error.FlagwireException;
import dev.flagwire.domain.value.Revision;
import java.util.Optional;

final class Revisions {

  private Revisions() {}

  static void requireMatch(
      String entity, String key, Optional<Revision> stored, Revision expected) {
    Revision actual = stored.orElse(Revision.NONE);
    if (!actual.equals(expected)) {
      throw FlagwireException.conflict(
          entity
              + " "
              + key
              + " is at revision "
              + actual.value()
              + " but the write expected revision "
              + expected.value());
    }
  }
}
