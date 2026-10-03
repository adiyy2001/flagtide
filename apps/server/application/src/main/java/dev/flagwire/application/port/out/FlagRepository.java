package dev.flagwire.application.port.out;

import dev.flagwire.domain.flag.Flag;
import dev.flagwire.domain.value.FlagKey;
import dev.flagwire.domain.value.ProjectKey;
import dev.flagwire.domain.value.Revision;
import java.util.List;
import java.util.Optional;

public interface FlagRepository {

  Optional<Flag> find(ProjectKey project, FlagKey key);

  List<Flag> findAll(ProjectKey project);

  void save(ProjectKey project, Flag flag, Revision expected);
}
