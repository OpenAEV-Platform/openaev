package io.openaev.database.model;

import java.util.List;

/**
 * Central registry that translates injector contract output types into chaining-engine semantics.
 *
 * <p>Injector contracts declare what they produce using {@link ContractOutputType} (e.g. PORT,
 * PORTSCAN, TEXT). The chaining engine works with {@link ChainingMappedType}, which classifies each
 * output as PRIMITIVE, COMPLEX, or NOT_CHAINABLE and, for primitives, resolves the exact {@link
 * PrimitiveType} to store values under.
 *
 * <p>This registry is the single source of truth for that translation. Any new contract output type
 * must be registered in {@link ChainingOutputType} for the chaining engine to handle it.
 */
public final class ChainingTypeRegistry {

  private ChainingTypeRegistry() {}

  public static List<PrimitiveType> getPrimitiveTypes() {
    return List.of(PrimitiveType.values());
  }

  /**
   * Translates a contract output type into the chaining-engine type used at runtime.
   *
   * <p>Example: ContractOutputType.PORT -> ChainingMappedType.primitive(PrimitiveType.Port)
   *
   * @param type the contract output type declared by the injector
   * @return the resolved chaining mapped type
   * @throws IllegalArgumentException if the contract output type has no registered mapping
   */
  public static ChainingMappedType getMappedTypeForContractOutputType(ContractOutputType type) {
    ChainingOutputType outputType = ChainingOutputType.fromContractOutputType(type);
    return switch (outputType.kind()) {
      case PRIMITIVE -> ChainingMappedType.primitive(outputType.primitiveType());
      case COMPLEX -> ChainingMappedType.complex();
      case NOT_CHAINABLE -> ChainingMappedType.nonChainable();
    };
  }

  public static ChainingMappedType getMappedTypeForScopeRuleValueType(
      ScopeRuleValueType valueType) {
    return ChainingMappedType.primitive(
        switch (valueType) {
          case IP -> List.of(PrimitiveType.IPv4, PrimitiveType.IPv6);
          case IP_SUBNET -> List.of(PrimitiveType.IpSubnet);
          case DOMAIN -> List.of(PrimitiveType.Domain);
          case ASSET_ID -> List.of(PrimitiveType.AssetId);
          case ASSET_GROUP_ID -> List.of(PrimitiveType.AssetGroupId);
        });
  }
}
