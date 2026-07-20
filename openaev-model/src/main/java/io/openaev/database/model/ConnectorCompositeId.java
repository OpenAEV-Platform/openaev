package io.openaev.database.model;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for connector entities ({@link Collector}, {@link Injector}, {@link
 * Executor}). The DB uses {@code (id, tenant_id)} as PK to allow the same static connector ID
 * across multiple tenants.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorCompositeId implements Serializable {

  private String id;
  private String tenantId;

  public static ConnectorCompositeId of(String id, String tenantId) {
    return new ConnectorCompositeId(id, tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ConnectorCompositeId that = (ConnectorCompositeId) o;
    return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId);
  }
}
