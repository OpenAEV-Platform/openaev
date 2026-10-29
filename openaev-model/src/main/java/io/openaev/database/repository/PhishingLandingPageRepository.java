package io.openaev.database.repository;

import io.openaev.database.model.PhishingLandingPage;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhishingLandingPageRepository
    extends CrudRepository<PhishingLandingPage, String>,
        JpaSpecificationExecutor<PhishingLandingPage> {

  /**
   * Looks up a landing page by its ID using a JPQL query so that the active Hibernate tenant filter
   * ({@code tenantFilter}) is applied. {@code EntityManager.find()} (used by the default
   * CrudRepository#findById) bypasses Hibernate filters and must not be used directly for
   * tenant-scoped entities (same fix as ChannelRepository / ChallengeRepository #6027).
   */
  @NotNull
  @Query("SELECT p FROM PhishingLandingPage p WHERE p.id = :id")
  Optional<PhishingLandingPage> findById(@NotNull @Param("id") String id);

  List<PhishingLandingPage> findByNameIgnoreCase(String name);

  /**
   * Explicit tenant-scoped listing, used by contract re-sync paths that must not rely on the
   * ambient scope of a plain {@code findAll()} (background onboarding sets the v2 scope, never the
   * v1 {@code TenantContext} a Hibernate filter would need, and a caller running before this table
   * is active in its own context has no scope at all).
   */
  @Query("SELECT p FROM PhishingLandingPage p WHERE p.tenant.id = :tenantId")
  List<PhishingLandingPage> findAllByTenantId(@Param("tenantId") String tenantId);
}
