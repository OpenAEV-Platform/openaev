package io.openaev.database.repository;

import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotifierRepository
    extends CrudRepository<Notifier, String>, JpaSpecificationExecutor<Notifier> {

  /**
   * Per-tenant built-in lookup, deliberately keyed on the tenant rather than left to the inspector:
   * it decides whether a built-in must be INSERTED for that exact tenant, which is a write-side
   * question the request scope alone does not answer (see the find-or-create rule in the
   * activate-tenant-table runbook).
   */
  Optional<Notifier> findFirstByTenantIdAndTypeAndBuiltInTrue(
      @NotNull String tenantId, @NotNull NotifierType type);
}
