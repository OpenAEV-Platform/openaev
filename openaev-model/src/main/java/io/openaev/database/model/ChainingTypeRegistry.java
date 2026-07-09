package io.openaev.database.model;

import java.util.List;

public final class ChainingTypeRegistry {

  private ChainingTypeRegistry() {}

  public static List<PrimitiveType> getPrimitiveTypes() {
    return List.of(PrimitiveType.values());
  }

  public static ChainingMappedType getMappedTypeForContractOutputType(ContractOutputType type) {
    ChainingOutputType outputType = ChainingOutputType.fromContractOutputType(type);
    return switch (outputType.kind()) {
      case PRIMITIVE -> ChainingMappedType.primitive(outputType.primitiveType());
      case COMPLEX -> ChainingMappedType.complex();
      case NON_CHAINABLE -> ChainingMappedType.nonChainable();
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
