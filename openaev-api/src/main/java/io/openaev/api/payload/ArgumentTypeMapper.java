package io.openaev.api.payload;

import io.openaev.database.model.ArgumentType;

public final class ArgumentTypeMapper {

  private ArgumentTypeMapper() {}

  public static ArgumentTypeOutput toOutput(ArgumentType argumentType) {
    return new ArgumentTypeOutput(argumentType, argumentType.subTypes);
  }
}
