package io.openaev.database.model;

import java.util.List;

public final class ChainingTypeRegistry {

  private ChainingTypeRegistry() {}

  public static boolean isPrimitiveType(ContractOutputType type) {
    return ChainingOutputType.fromContractOutputType(type).kind() == ChainingTypeKind.PRIMITIVE;
  }

  public static boolean isPrimitiveChainingType(ContractOutputType type) {
    return isPrimitiveType(type);
  }

  public static boolean isComplexType(ContractOutputType type) {
    return ChainingOutputType.fromContractOutputType(type).kind() == ChainingTypeKind.COMPLEX;
  }

  public static boolean isComplexChainingType(ContractOutputType type) {
    return isComplexType(type);
  }

  public static boolean isChainableType(ContractOutputType type) {
    return ChainingOutputType.fromContractOutputType(type).kind() != ChainingTypeKind.NON_CHAINABLE;
  }

  public static List<PrimitiveType> getPrimitiveTypes() {
    return List.of(PrimitiveType.values());
  }

  public static List<ComplexType> getComplexTypes() {
    return List.of(ComplexType.values());
  }

  public static List<ComplexType> getComplexChainingTypes() {
    return getComplexTypes();
  }
}
