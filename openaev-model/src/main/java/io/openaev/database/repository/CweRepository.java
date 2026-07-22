package io.openaev.database.repository;

import io.openaev.database.model.Cwe;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CweRepository extends CrudRepository<Cwe, String>, JpaSpecificationExecutor<Cwe> {

  Optional<Cwe> findByExternalId(@NotBlank String externalId);

  // Aligned with the per-tenant unique key (cwe_external_id, tenant_id): under a multi-tenant read
  // scope, findByExternalId alone can match one row per in-scope tenant and blow up.
  Optional<Cwe> findByExternalIdAndTenantId(@NotBlank String externalId, @NotBlank String tenantId);
}
