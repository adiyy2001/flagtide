package dev.flagtide.adapter.out.memory;

import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.value.Revision;
import java.util.Optional;

final class Revisions {

  private Revisions() {}

  static void requireMatch(
      String entity, String key, Optional<Revision> stored, Revision expected) {
    Revision actual = stored.orElse(Revision.NONE);
    if (!actual.equals(expected)) {
      throw FlagtideException.conflict(
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
