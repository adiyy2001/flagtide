package dev.flagtide.application.usecase;

import dev.flagtide.application.port.out.FlagRepository;
import dev.flagtide.application.port.out.TransactionRunner;
import dev.flagtide.application.security.Principal;
import dev.flagtide.domain.error.FlagtideException;
import dev.flagtide.domain.event.DomainEvent;
import dev.flagtide.domain.event.Stamp;
import dev.flagtide.domain.event.Transition;
import dev.flagtide.domain.flag.Flag;
import dev.flagtide.domain.value.EnvironmentKey;
import dev.flagtide.domain.value.EnvironmentVersion;
import dev.flagtide.domain.value.FlagKey;
import dev.flagtide.domain.value.Revision;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

final class FlagEditor {

  private final TransactionRunner transactions;
  private final FlagRepository flags;
  private final ChangeRecorder recorder;

  FlagEditor(TransactionRunner transactions, FlagRepository flags, ChangeRecorder recorder) {
    this.transactions = transactions;
    this.flags = flags;
    this.recorder = recorder;
  }

  CommandResult<Flag> edit(
      Principal principal,
      FlagKey key,
      Optional<Revision> expectedRevision,
      BiFunction<Flag, Stamp, Transition<Flag>> change) {
    return this.transactions.inTransaction(
        () -> {
          Flag current = this.load(principal, key);
          this.requireExpectedRevision(current, expectedRevision);
          Transition<Flag> transition = change.apply(current, this.recorder.stamp(principal));
          if (!transition.changed()) {
            return CommandResult.unchanged(current);
          }
          this.flags.save(principal.project(), transition.next(), current.revision());
          DomainEvent event = transition.events().get(0);
          Map<EnvironmentKey, EnvironmentVersion> versions =
              this.recorder.recordFlag(
                  principal.project(), Optional.of(current), transition.next(), event);
          return new CommandResult<>(transition.next(), transition.events(), versions);
        });
  }

  Flag load(Principal principal, FlagKey key) {
    return this.flags
        .find(principal.project(), key)
        .orElseThrow(() -> FlagtideException.notFound("flag", key.value()));
  }

  private void requireExpectedRevision(Flag current, Optional<Revision> expected) {
    expected.ifPresent(
        revision -> {
          if (!revision.equals(current.revision())) {
            throw FlagtideException.conflict(
                "flag "
                    + current.key().value()
                    + " is at revision "
                    + current.revision().value()
                    + " but the edit was based on revision "
                    + revision.value());
          }
        });
  }
}
