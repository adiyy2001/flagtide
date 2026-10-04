package dev.flagtide.application.port.out;

import dev.flagtide.domain.value.Salt;

public interface IdGenerator {

  String newId();

  Salt newSalt();

  String newToken();
}
