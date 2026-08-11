package io.openaev.database.repository;

import io.openaev.database.model.PhishingResult;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhishingResultRepository
    extends CrudRepository<PhishingResult, String>, JpaSpecificationExecutor<PhishingResult> {

  /** Tenant-filtered lookup by id (JPQL so {@code tenantFilter} applies). */
  @NotNull
  @Query("SELECT r FROM PhishingResult r WHERE r.id = :id")
  Optional<PhishingResult> findById(@NotNull @Param("id") String id);

  @Query("SELECT r FROM PhishingResult r WHERE r.token = :token")
  Optional<PhishingResult> findByToken(@NotNull @Param("token") String token);

  /**
   * Resolves the owning tenant of a tracking token without any tenant context. The public,
   * unauthenticated landing/tracking endpoints no longer carry the tenant in the URL (the token is
   * globally unique, 192 bits), so the tenant is recovered from the token here and then set on the
   * {@code TenantContext} before any tenant-filtered work runs. Native so the Hibernate {@code
   * tenantFilter} is not applied (there is no tenant to filter by yet).
   */
  @Query(
      value = "SELECT tenant_id FROM phishing_results WHERE phishing_result_token = :token",
      nativeQuery = true)
  Optional<String> findTenantIdByToken(@NotNull @Param("token") String token);

  @Query("SELECT r FROM PhishingResult r WHERE r.inject.id = :injectId")
  List<PhishingResult> findByInjectId(@NotNull @Param("injectId") String injectId);
}
