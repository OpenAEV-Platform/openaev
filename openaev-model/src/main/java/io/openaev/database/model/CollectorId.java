package io.openaev.database.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite primary key for {@link Collector}.
 *
 * <p>Since built-in collectors have static IDs shared across tenants, each tenant needs its own
 * copy. The PK must therefore include both the collector ID and the tenant ID.
 *
 * <p>The tenant is stored as a plain {@code String} (not a {@code @ManyToOne}) to avoid a known
 * Hibernate 6.x {@code AssertionError} in {@code EmbeddableAssembler} when an {@code @EmbeddedId}
 * with {@code @ManyToOne} is combined with EAGER collections using composite join columns.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class CollectorId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "collector_id")
  private String id = UUID.randomUUID().toString();

  @Column(name = "tenant_id", updatable = false, nullable = false)
  private String tenantId;

  public CollectorId(String id, String tenantId) {
    this.id = id;
    this.tenantId = tenantId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CollectorId that = (CollectorId) o;
    return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId);
  }
}
