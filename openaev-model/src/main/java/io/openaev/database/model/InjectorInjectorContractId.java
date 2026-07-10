package io.openaev.database.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite identifier for the {@link InjectorInjectorContract} join entity. */
public class InjectorInjectorContractId implements Serializable {

  private String injectorId;
  private String injectorContractId;
  private String tenantId;

  public InjectorInjectorContractId() {}

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InjectorInjectorContractId that = (InjectorInjectorContractId) o;
    return Objects.equals(injectorId, that.injectorId)
        && Objects.equals(injectorContractId, that.injectorContractId)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(injectorId, injectorContractId, tenantId);
  }
}
