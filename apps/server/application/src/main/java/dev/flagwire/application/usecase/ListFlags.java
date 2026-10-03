package dev.flagwire.application.usecase;

import dev.flagwire.application.port.out.FlagRepository;
import dev.flagwire.application.security.Authorizer;
import dev.flagwire.application.security.Principal;
import dev.flagwire.domain.evaluation.FlagType;
import dev.flagwire.domain.flag.Flag;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ListFlags {

  public record Query(Optional<String> text, Optional<FlagType> type, boolean includeArchived) {

    public static Query everything() {
      return new Query(Optional.empty(), Optional.empty(), false);
    }
  }

  private final FlagRepository flags;
  private final Authorizer authorizer;

  public ListFlags(FlagRepository flags, Authorizer authorizer) {
    this.flags = flags;
    this.authorizer = authorizer;
  }

  public List<Flag> execute(Principal principal, Query query) {
    this.authorizer.requireAdmin(principal);
    return this.flags.findAll(principal.project()).stream()
        .filter(flag -> query.includeArchived() || !flag.archived())
        .filter(flag -> query.type().map(type -> type == flag.type()).orElse(true))
        .filter(flag -> query.text().map(text -> this.matches(flag, text)).orElse(true))
        .sorted(Comparator.comparing(Flag::key))
        .toList();
  }

  private boolean matches(Flag flag, String text) {
    String needle = text.toLowerCase(Locale.ROOT);
    return flag.key().value().contains(needle)
        || flag.description().toLowerCase(Locale.ROOT).contains(needle);
  }
}
