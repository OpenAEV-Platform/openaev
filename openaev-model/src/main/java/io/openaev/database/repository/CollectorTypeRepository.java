package io.openaev.database.repository;

import io.openaev.database.model.CollectorType;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectorTypeRepository extends CrudRepository<CollectorType, String> {

  Optional<CollectorType> findByName(@NotNull String name);

  /**
   * Tenant-exact lookup, used to dedupe a create against the write tenant only. {@code
   * collector_type_name} is unique per tenant (not globally), so a caller whose ambient scope spans
   * several tenants must not fall back to {@link #findByName}, which can return another tenant's
   * row.
   */
  Optional<CollectorType> findByNameAndTenantId(@NotNull String name, @NotNull String tenantId);
}
