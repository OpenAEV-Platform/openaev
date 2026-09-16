package io.openaev.database.repository;

import io.openaev.database.model.Challenge;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeRepository
    extends CrudRepository<Challenge, String>, JpaSpecificationExecutor<Challenge> {

  // challenges is fully on v2 isolation (TenantStatementInspector): CrudRepository#findById()
  // is scoped like any other query, no JPQL override needed to dodge a v1 @Filter bypass.

  @NotNull
  List<Challenge> findByNameIgnoreCase(@NotNull final String name);

  /**
   * Per-tenant business-key lookup for find-or-create paths (e.g. import): looking up by the bare
   * name under a multi-tenant read scope could match one row per in-scope tenant and silently reuse
   * another tenant's challenge. Callers must resolve the write tenant first and look up scoped to
   * it.
   */
  @NotNull
  List<Challenge> findByNameIgnoreCaseAndTenantId(
      @NotNull final String name, @NotNull final String tenantId);
}
