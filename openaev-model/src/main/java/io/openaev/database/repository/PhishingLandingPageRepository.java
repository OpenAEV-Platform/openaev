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
}
