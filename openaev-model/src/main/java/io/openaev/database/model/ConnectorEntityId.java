package io.openaev.database.model;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for connector entities (Injector, Collector, Executor). The DB PK is {@code
 * (id, tenant_id)} to allow the same static connector ID across multiple tenants.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorEntityId implements Serializable {
  private String id;
  private String tenant;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConnectorEntityId that = (ConnectorEntityId) o;
    return Objects.equals(id, that.id) && Objects.equals(tenant, that.tenant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenant);
  }
}
