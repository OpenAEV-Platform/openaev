package io.openaev.database.repository;

import io.openaev.database.model.CustomDomain;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomDomainRepository
    extends JpaRepository<CustomDomain, String>, JpaSpecificationExecutor<CustomDomain> {

  /**
   * Tenant-filtered lookup by id (JPQL so the Hibernate {@code tenantFilter} applies - {@code
   * EntityManager.find()} bypasses filters and must not be used for tenant-scoped entities, same
   * fix as PhishingLandingPageRepository #6027).
   */
  @NotNull
  @Query("SELECT d FROM CustomDomain d WHERE d.id = :id")
  Optional<CustomDomain> findById(@NotNull @Param("id") String id);

  /**
   * Tenant-filtered case-insensitive hostname lookup, used to reject duplicates within a tenant.
   */
  @Query("SELECT d FROM CustomDomain d WHERE lower(d.hostname) = lower(:hostname)")
  Optional<CustomDomain> findByHostnameIgnoreCase(@Param("hostname") String hostname);

  /**
   * Global, tenant-filter-bypassing lookup used by the unauthenticated {@code domain-check}
   * endpoint that fronts on-demand TLS. Hostnames are globally unique (DB unique index), so at most
   * one row matches. Native so no Hibernate filter is applied (there is no tenant context on a
   * public request).
   */
  @Query(
      value =
          "SELECT custom_domain_status FROM custom_domains WHERE lower(custom_domain_hostname) = lower(:hostname)",
      nativeQuery = true)
  Optional<String> findStatusByHostname(@Param("hostname") String hostname);
}
