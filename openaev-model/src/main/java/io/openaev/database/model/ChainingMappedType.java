package io.openaev.database.model;

import java.util.List;

public record ChainingMappedType(
    ChainingTypeKind kind, List<PrimitiveType> primitiveTypes, ContractOutputType origin) {

  public ChainingMappedType {
    primitiveTypes = primitiveTypes == null ? List.of() : List.copyOf(primitiveTypes);
  }

  public static ChainingMappedType primitive(List<PrimitiveType> primitiveTypes) {
    return new ChainingMappedType(ChainingTypeKind.PRIMITIVE, primitiveTypes, null);
  }

  public static ChainingMappedType primitive(PrimitiveType primitiveType) {
    return new ChainingMappedType(ChainingTypeKind.PRIMITIVE, List.of(primitiveType), null);
  }

  public static ChainingMappedType complex() {
    return new ChainingMappedType(ChainingTypeKind.COMPLEX, List.of(), null);
  }

  public static ChainingMappedType complex(
      List<PrimitiveType> primitiveRecipe, ContractOutputType origin) {
    return new ChainingMappedType(ChainingTypeKind.COMPLEX, primitiveRecipe, origin);
  }

  public static ChainingMappedType nonChainable() {
    return new ChainingMappedType(ChainingTypeKind.NOT_CHAINABLE, List.of(), null);
  }
}
