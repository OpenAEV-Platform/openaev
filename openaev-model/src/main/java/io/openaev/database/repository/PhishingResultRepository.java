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

  /** Lookup by id through JPQL, so the statement inspector scopes it. */
  @NotNull
  @Query("SELECT r FROM PhishingResult r WHERE r.id = :id")
  Optional<PhishingResult> findById(@NotNull @Param("id") String id);

  @Query("SELECT r FROM PhishingResult r WHERE r.token = :token")
  Optional<PhishingResult> findByToken(@NotNull @Param("token") String token);

  /**
   * Resolves the owning tenant of a tracking token. The public, unauthenticated landing and
   * tracking endpoints do not carry the tenant in the URL (the token is globally unique, 192 bits),
   * so the tenant is recovered from the token here before any tenant-scoped work runs. {@code
   * phishing_results} is tenant-active, so the statement inspector rewrites this native query too:
   * the caller runs it under {@code TxCtx.allTenants()} ({@code
   * PhishingTrackingPublicLookupService.tenantIdByToken}), without which it finds no row.
   */
  @Query(
      value = "SELECT tenant_id FROM phishing_results WHERE phishing_result_token = :token",
      nativeQuery = true)
  Optional<String> findTenantIdByToken(@NotNull @Param("token") String token);

  @Query("SELECT r FROM PhishingResult r WHERE r.inject.id = :injectId")
  List<PhishingResult> findByInjectId(@NotNull @Param("injectId") String injectId);
}
