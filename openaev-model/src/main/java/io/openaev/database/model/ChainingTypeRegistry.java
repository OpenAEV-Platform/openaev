package io.openaev.database.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
  private static final Map<ContractOutputType, Map<String, PrimitiveType>>
      CONTEXTUAL_COMPLEX_FIELD_PRIMITIVES =
          Map.ofEntries(
              Map.entry(
                  ContractOutputType.Credentials,
                  Map.ofEntries(
                      Map.entry("username", PrimitiveType.Username),
                      Map.entry("password", PrimitiveType.Password),
                      Map.entry("hash", PrimitiveType.Hash),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Username,
                  Map.ofEntries(
                      Map.entry("username", PrimitiveType.Username),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.AdminUsername,
                  Map.ofEntries(
                      Map.entry("username", PrimitiveType.AdminUsername),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Share,
                  Map.ofEntries(
                      Map.entry("share_name", PrimitiveType.ShareName),
                      Map.entry("permissions", PrimitiveType.Permissions),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.File,
                  Map.ofEntries(
                      // `share` reuses ShareName so a file discovered on a share links to its
                      // share finding; `file_name` is the basename the graph chains on.
                      Map.entry("file_name", PrimitiveType.FileName),
                      Map.entry("path", PrimitiveType.FilePath),
                      Map.entry("share", PrimitiveType.ShareName),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Group,
                  Map.ofEntries(
                      Map.entry("group_name", PrimitiveType.GroupName),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Computer,
                  Map.ofEntries(
                      Map.entry("computer_name", PrimitiveType.ComputerName),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.PasswordPolicy,
                  Map.ofEntries(
                      Map.entry("key", PrimitiveType.Key),
                      Map.entry("value", PrimitiveType.Value),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Delegation,
                  Map.ofEntries(
                      Map.entry("account", PrimitiveType.DelegationAccount),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Sid,
                  Map.ofEntries(
                      Map.entry("sid", PrimitiveType.SID),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.Vulnerability,
                  Map.ofEntries(
                      Map.entry("name", PrimitiveType.VulnerabilityName),
                      Map.entry("status", PrimitiveType.VulnerabilityStatus),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.AccountWithPasswordNotRequired,
                  Map.ofEntries(
                      Map.entry("account", PrimitiveType.AccountWithPasswordNotRequired),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.AsreproastableAccount,
                  Map.ofEntries(
                      Map.entry("username", PrimitiveType.AsreproastableAccount),
                      Map.entry("hash", PrimitiveType.Hash),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.KerberoastableAccount,
                  Map.ofEntries(
                      Map.entry("username", PrimitiveType.KerberoastableAccount),
                      Map.entry("hash", PrimitiveType.Hash),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.PortsScan,
                  Map.ofEntries(
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("port", PrimitiveType.Port),
                      Map.entry("service", PrimitiveType.Service),
                      Map.entry("asset_id", PrimitiveType.AssetId))),
              Map.entry(
                  ContractOutputType.CVE,
                  Map.ofEntries(
                      Map.entry("id", PrimitiveType.CVE),
                      Map.entry("host", PrimitiveType.Host),
                      Map.entry("severity", PrimitiveType.Severity),
                      Map.entry("asset_id", PrimitiveType.AssetId))));

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
      case COMPLEX -> ChainingMappedType.complex(outputType.primitiveRecipe(), type);
      case NOT_CHAINABLE -> ChainingMappedType.nonChainable();
    };
  }

  public static List<PrimitiveType> getPrimitiveTypesForContractOutputType(
      ContractOutputType type) {
    if (type == null) {
      return List.of();
    }

    ChainingMappedType mappedType = getMappedTypeForContractOutputType(type);
    if (!mappedType.primitiveTypes().isEmpty()) {
      return mappedType.primitiveTypes();
    }

    Map<String, PrimitiveType> contextualFieldMapping =
        CONTEXTUAL_COMPLEX_FIELD_PRIMITIVES.get(type);
    if (contextualFieldMapping == null) {
      return List.of();
    }

    return contextualFieldMapping.values().stream().distinct().collect(Collectors.toList());
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
          // Team / person scope is an audience axis, not an asset-target primitive: it contributes
          // no primitive targets to the engine's asset/IP resolution (which stays asset-centric).
          case TEAM_ID, PLAYER_ID -> List.of();
        });
  }

  public static Optional<PrimitiveType> resolveComplexFieldPrimitive(
      String outputTypeName, String jsonFieldName) {
    if (outputTypeName == null || jsonFieldName == null) {
      return Optional.empty();
    }

    ContractOutputType outputType;
    try {
      outputType = ContractOutputType.valueOf(outputTypeName);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }

    return resolveComplexFieldPrimitive(outputType, jsonFieldName);
  }

  public static Optional<PrimitiveType> resolveComplexFieldPrimitive(
      ContractOutputType outputType, String jsonFieldName) {
    if (outputType == null || jsonFieldName == null) {
      return Optional.empty();
    }

    Map<String, PrimitiveType> fieldMap = CONTEXTUAL_COMPLEX_FIELD_PRIMITIVES.get(outputType);
    if (fieldMap == null) {
      return Optional.empty();
    }

    return Optional.ofNullable(fieldMap.get(jsonFieldName.toLowerCase(Locale.ROOT)));
  }
}
