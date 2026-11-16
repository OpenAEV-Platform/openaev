package io.openaev.database.repository;

import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

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

  /** The notifiers among the given ids that belong to the given tenant. */
  @NotNull
  List<Notifier> findAllByIdInAndTenantId(
      @NotNull Collection<String> ids, @NotNull String tenantId);

  /**
   * The notifiers among the given ids that belong to the given tenant. Refuses a null id list, as
   * {@code findAllById} does.
   */
  @NotNull
  default List<Notifier> findAllByIdInTenant(Collection<String> ids, @NotNull String tenantId) {
    Assert.notNull(ids, "Ids must not be null");
    return findAllByIdInAndTenantId(ids, tenantId);
  }
}
