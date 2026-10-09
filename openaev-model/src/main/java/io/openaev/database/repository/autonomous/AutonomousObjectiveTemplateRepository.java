package io.openaev.database.repository.autonomous;

import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Tenant-active store for autonomous objective templates (built-ins + admin-created).
 *
 * <p>The table is tenant-active, so every read here is rewritten by the statement inspector and
 * returns only the rows inside the current scope. {@code findByKey} relies on that: the only unique
 * business key is {@code (tenant_id, key)}, so the lookup is single-valued under a single-tenant
 * scope and ambiguous under a broader one.
 */
@Repository
public interface AutonomousObjectiveTemplateRepository
    extends CrudRepository<AutonomousObjectiveTemplate, String> {

  List<AutonomousObjectiveTemplate> findByEnabledTrueOrderByOrderAsc();

  Optional<AutonomousObjectiveTemplate> findByKey(String key);

  List<AutonomousObjectiveTemplate> findByKeyIn(Collection<String> keys);

  /**
   * Takes a transaction-scoped Postgres advisory lock on {@code key}, so two first reads of the
   * same tenant's gallery serialise instead of both deciding the same built-in is missing. The lock
   * auto-releases at commit or rollback and is cluster-wide, unlike a JVM lock; it complements the
   * UNIQUE {@code (tenant_id, key)} index, which is the hard backstop. Wrapped as {@code SELECT 1
   * FROM (...)} so the {@code void} lock function maps to a plain scalar, the volatile function
   * still being evaluated to produce the row. Must run inside a transaction.
   */
  @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked", nativeQuery = true)
  Integer lockTenantGallerySeed(@Param("key") long key);
}
