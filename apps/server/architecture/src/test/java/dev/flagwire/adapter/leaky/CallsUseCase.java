package dev.flagwire.adapter.leaky;

import dev.flagwire.application.usecase.CreateFlag;

public final class CallsUseCase {

  public Class<?> useCase() {
    return CreateFlag.class;
  }
}
