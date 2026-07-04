package io.openaev.api.payload;

import io.openaev.database.model.PrimitiveType;

public final class PrimitiveTypeMapper {

  private PrimitiveTypeMapper() {}

  public static PrimitiveTypeOutput toOutput(PrimitiveType primitiveType) {
    return new PrimitiveTypeOutput(primitiveType);
  }
}
