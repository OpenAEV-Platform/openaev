package io.openaev.database.repository;

import io.openaev.database.model.Domain;
import io.openaev.database.raw.RawDomainIndexing;
import jakarta.annotation.Nonnull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface DomainRepository
    extends CrudRepository<Domain, String>, JpaSpecificationExecutor<Domain> {

  @Nonnull
  @Transactional(readOnly = true)
  Optional<Domain> findByName(@Nonnull String name);

  @Nonnull
  @Transactional(readOnly = true)
  Optional<Domain> findByNameAndTenantId(@Nonnull String name, @Nonnull String tenantId);

  @Nonnull
  @Transactional(readOnly = true)
  List<Domain> findByNameIn(Collection<String> names);

  @Nonnull
  @Transactional(readOnly = true)
  List<Domain> findByNameInAndTenantId(Collection<String> names, String tenantId);

  @Nonnull
  @Transactional(readOnly = true)
  List<Domain> findByTenantId(String tenantId);

  /**
   * Takes a PostgreSQL transaction-scoped advisory lock keyed on {@code key} and returns once it is
   * held. {@code DomainService} calls this before creating a domain, so two transactions creating
   * spellings of the same name in a tenant serialise and the second one finds the first one's
   * domain. The lock auto-releases at commit or rollback and holds across API nodes. Must run
   * inside a transaction. Native because JPQL cannot call {@code pg_advisory_xact_lock}; it reads
   * and writes no row, so no session side effect is lost.
   */
  @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:key)) AS locked", nativeQuery = true)
  Integer lockDomainCreation(@Param("key") long key);

  @Query(
      value =
          "SELECT d.domain_id, d.domain_name, d.domain_color, "
              + "d.domain_created_at, d.domain_updated_at, d.tenant_id "
              + "FROM domains d "
              + "WHERE d.domain_updated_at > :from ORDER BY d.domain_updated_at LIMIT :limit;",
      nativeQuery = true)
  List<RawDomainIndexing> findForIndexing(@Param("from") Instant from, @Param("limit") int limit);
}
