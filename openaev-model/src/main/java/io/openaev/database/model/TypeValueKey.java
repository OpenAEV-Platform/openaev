package io.openaev.database.model;

import java.util.Objects;

public class TypeValueKey {
  private final ContractOutputType type;
  private final String value;

  public TypeValueKey(ContractOutputType type, String value) {
    this.type = type;
    this.value = value;
  }

  public ContractOutputType getType() {
    return type;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TypeValueKey)) return false;
    TypeValueKey that = (TypeValueKey) o;
    return type == that.type && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, value);
  }
}
