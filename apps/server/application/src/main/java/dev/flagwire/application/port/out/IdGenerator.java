package dev.flagwire.application.port.out;

import dev.flagwire.domain.value.Salt;

public interface IdGenerator {

  String newId();

  Salt newSalt();

  String newToken();
}
