package io.openaev.database.repository;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.raw.RawAttackPatternIndexing;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttackPatternRepository
    extends CrudRepository<AttackPattern, String>, JpaSpecificationExecutor<AttackPattern> {

  @NotNull
  Optional<AttackPattern> findById(@NotNull String id);

  /**
   * Tenant-scoped primary-key lookup. Hibernate's {@code tenantFilter} does not apply to {@code
   * findById} (filters never apply to primary-key loads), so callers resolving an id received from
   * user input (e.g. import files) must use this method to avoid reading another tenant's attack
   * pattern.
   */
  @NotNull
  Optional<AttackPattern> findByIdAndTenantId(@NotNull String id, @NotNull String tenantId);

  List<AttackPattern> findAllByIdIn(List<String> ids);

  /**
   * Load attack patterns together with their kill chain phases in a single query. Avoids the N+1
   * pattern that would otherwise occur when iterating the LAZY {@code killChainPhases} association
   * of each result (e.g. when building the global ATT&CK coverage matrix).
   */
  @Query(
      "SELECT DISTINCT ap FROM AttackPattern ap LEFT JOIN FETCH ap.killChainPhases WHERE ap.id IN :ids")
  List<AttackPattern> findAllByIdInWithKillChainPhases(@Param("ids") Collection<String> ids);

  Optional<AttackPattern> findByExternalId(@NotNull String externalId);

  List<AttackPattern> findAllByExternalIdInIgnoreCaseAndTenantId(
      List<String> externalIds, String tenantId);

  Optional<AttackPattern> findByStixId(@NotNull String stixId);

  @Query(
      value =
          "select ap.*, array_remove(array_agg(apphase.phase_id), NULL) as attack_pattern_kill_chain_phases from attack_patterns ap "
              + "left join attack_patterns_kill_chain_phases apphase ON ap.attack_pattern_id = apphase.attack_pattern_id WHERE ap.tenant_id = :#{#tenantContext.currentTenant} GROUP BY ap.attack_pattern_id",
      nativeQuery = true)
  List<RawAttackPatternIndexing> rawAll();

  // -- INDEXING --

  @Query(
      value =
          "SELECT ap.attack_pattern_id, ap.attack_pattern_stix_id, ap.attack_pattern_name,"
              + " ap.attack_pattern_description, ap.attack_pattern_external_id, ap.attack_pattern_platforms, "
              + " ap.attack_pattern_created_at, ap.attack_pattern_updated_at, ap.attack_pattern_parent, ap.tenant_id, apkcp.phase_id AS attack_pattern_kill_chain_phases "
              + "FROM attack_patterns ap "
              + "LEFT JOIN attack_patterns_kill_chain_phases apkcp ON apkcp.attack_pattern_id = ap.attack_pattern_id "
              + "WHERE ap.attack_pattern_updated_at > :from ORDER BY ap.attack_pattern_updated_at LIMIT :limit;",
      nativeQuery = true)
  List<RawAttackPatternIndexing> findForIndexing(
      @Param("from") Instant from, @Param("limit") int limit);
}
