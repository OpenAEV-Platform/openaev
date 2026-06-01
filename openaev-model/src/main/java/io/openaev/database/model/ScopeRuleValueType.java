package io.openaev.database.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum ScopeRuleValueType {
  IP("IPv4"),
  IP_SUBNET("Text"),
  DOMAIN("Text"),
  ASSET_ID("IPv4"),
  ASSET_GROUP_ID("Text");
  private final String contractOutputType;

  ScopeRuleValueType(String contractOutputType) {
    this.contractOutputType = contractOutputType;
  }

  public String getContractOutputType() {
    return contractOutputType;
  }

  public static Set<String> getAllContractOutputTypes() {
    return Arrays.stream(values()).map(type -> type.contractOutputType).collect(Collectors.toSet());
  }

  public static Set<String> getAllNamesAsSet() {
    return Arrays.stream(ScopeRuleValueType.values()).map(Enum::name).collect(Collectors.toSet());
  }
}
