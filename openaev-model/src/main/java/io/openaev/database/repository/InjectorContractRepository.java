package io.openaev.database.repository;

import io.openaev.database.model.*;
import io.openaev.database.model.attackpath.projection.AttackPathInjectorPatternRow;
import io.openaev.database.raw.RawContractTenant;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.database.raw.RawPayloadRelatedIds;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for {@link InjectorContract} entities.
 *
 * <p>This repository provides data access operations for injector contracts, which define the
 * capabilities and parameters of attack simulation injectors. It supports:
 *
 * <ul>
 *   <li>CRUD operations via {@link CrudRepository}
 *   <li>Dynamic filtering via {@link JpaSpecificationExecutor}
 *   <li>Custom queries for contract lookup by various criteria
 *   <li>Access-controlled queries respecting user grants
 * </ul>
 *
 * @see InjectorContract
 * @see Injector
 */
@Repository
public interface InjectorContractRepository
    extends CrudRepository<InjectorContract, InjectorContractId>,
        JpaSpecificationExecutor<InjectorContract> {

  /**
   * Retrieves all injector contracts with their associated attack pattern external IDs.
   *
   * <p>Returns a lightweight projection containing only the contract ID and aggregated attack
   * pattern IDs for efficient bulk operations.
   *
   * @return list of raw injector contract projections
   */
  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "WHERE injcon.tenant_id = :#{#tenantContext.currentTenant} "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawInjectorsContracts> getAllRawInjectorsContracts();

  /**
   * Retrieves the distinct platforms declared by the injector contracts used in an exercise.
   *
   * @param exerciseId the ID of the exercise
   * @return list of distinct platform arrays, one per contract
   */
  @Query(
      "SELECT DISTINCT ic.platforms FROM Inject i JOIN i.injectorContract ic"
          + " WHERE i.exercise.id = :exerciseId"
          + " AND ic.platforms IS NOT NULL")
  List<Endpoint.PLATFORM_TYPE[]> findDistinctPlatformsByExerciseId(
      @Param("exerciseId") String exerciseId);

  /**
   * Retrieves injector contracts that a specific user has been granted access to.
   *
   * <p>Returns only contracts where the user has a THREAT_ARSENAL grant on the injector contract ID
   * through their group memberships.
   *
   * @param userId the ID of the user to check access for
   * @return list of raw injector contract projections the user has been granted access to
   */
  default List<RawInjectorsContracts> getAllRawInjectorsContractsGranted(String userId) {
    return getAllRawInjectorsContractsGranted(userId, ResourceType.THREAT_ARSENAL.name());
  }

  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "WHERE injcon.tenant_id = :#{#tenantContext.currentTenant} "
              + "AND EXISTS ( "
              + "  SELECT 1 FROM users u "
              + "  INNER JOIN users_groups ug ON u.user_id = ug.user_id "
              + "  INNER JOIN groups g ON ug.group_id = g.group_id "
              + "  INNER JOIN grants gr ON g.group_id = gr.grant_group "
              + "  WHERE u.user_id = :userId "
              + "  AND gr.grant_resource = injcon.injector_contract_id "
              + "  AND gr.grant_resource_type = :resourceType "
              + ") "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawInjectorsContracts> getAllRawInjectorsContractsGranted(
      @Param("userId") String userId, @Param("resourceType") String resourceType);

  @NotNull
  @Query("SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id = :id")
  Optional<InjectorContract> findById(@Param("id") @NotNull String id);

  @Query("SELECT COUNT(ic) > 0 FROM InjectorContract ic WHERE ic.compositeId.id = :id")
  boolean existsByContractId(@Param("id") @NotNull String id);

  @Query(
      "SELECT COUNT(ic) > 0 FROM InjectorContract ic WHERE ic.compositeId.id = :id AND ic.compositeId.tenantId = :tenantId")
  boolean existsByContractIdAndTenant(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  /**
   * Existence check taken under a pessimistic share lock ({@code SELECT ... FOR SHARE} on
   * Postgres), used at chaining step authoring time to serialize with the contract-delete
   * transaction (#7418).
   *
   * <p>A chaining step references its injector contract only through the JSON snapshot in {@code
   * step_data} - there is no foreign key for a database cascade to act on - so nothing otherwise
   * stops a stale (or racing) client from authoring a step against a contract that is being
   * deleted, recreating the ghost-step state that {@code ChainingStepCleanupService} (#7413)
   * sweeps. FOR SHARE makes the two transactions serialize: if the delete committed first the row
   * is gone and authoring is rejected; if authoring takes the lock first, the delete (and its #7413
   * sweep) waits for authoring to commit and then sees the newly created step.
   *
   * <p>Deliberately a scalar id projection, not an entity select: {@link InjectorContract} eagerly
   * loads several collections, and a {@code SELECT ic ... FOR SHARE} would outer-join them and fail
   * on Postgres ("FOR SHARE cannot be applied to the nullable side of an outer join"). Projecting a
   * single column keeps the locking query on the {@code injectors_contracts} row alone. Tenant-
   * scoped JPQL (so Hibernate's {@code tenantFilter} also applies) because the default contracts
   * share their ids across every tenant.
   *
   * @param id the injector contract id referenced by the step being authored
   * @param tenantId the current tenant
   * @return the contract id if it exists in the tenant, empty otherwise
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "SELECT ic.compositeId.id FROM InjectorContract ic WHERE ic.compositeId.id = :id AND ic.compositeId.tenantId = :tenantId")
  Optional<String> lockContractIdForAuthoring(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  /**
   * Tenant-scoped counterpart of {@link #findById(String)}: the composite PK is (tenant_id, id), so
   * a bare-id lookup can match another tenant's contract (or throw on duplicate ids across tenants
   * when the Hibernate tenant filter is not enabled). Use for user-supplied ids (e.g. imports).
   */
  @NotNull
  @Query(
      "SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id = :id AND ic.compositeId.tenantId = :tenantId")
  Optional<InjectorContract> findByContractIdAndTenant(
      @Param("id") @NotNull String id, @Param("tenantId") @NotNull String tenantId);

  @NotNull
  @Query(
      "SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id = :id OR ic.externalId = :externalId")
  Optional<InjectorContract> findByIdOrExternalId(
      @Param("id") String id, @Param("externalId") String externalId);

  /**
   * Batched form of {@link #findByIdOrExternalId}: every contract whose id OR external id is in the
   * set.
   */
  @Query(
      "SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id IN :ids OR ic.externalId IN :ids")
  List<InjectorContract> findAllByIdOrExternalIdIn(@Param("ids") Collection<String> ids);

  /**
   * Batched ATT&CK techniques for the attack-path injector nodes: one flat query returning the
   * (contract external id, pattern) pairs for a set of contracts. A flat projection, not the
   * entity, so it never pulls the contract's other eager collections into secondary selects.
   */
  @Query(
      "SELECT new io.openaev.database.model.attackpath.projection.AttackPathInjectorPatternRow("
          + "ic.externalId, ap.externalId, ap.name) "
          + "FROM InjectorContract ic JOIN ic.attackPatterns ap "
          + "WHERE ic.externalId IN :externalIds")
  List<AttackPathInjectorPatternRow> findInjectorAttackPatternsByExternalIdIn(
      @Param("externalIds") Collection<String> externalIds);

  @NotNull
  @Query("SELECT ic FROM InjectorContract ic JOIN ic.injectorLinks l WHERE l.injector = :injector")
  List<InjectorContract> findByInjectorsContaining(@NotNull @Param("injector") Injector injector);

  @NotNull
  Optional<InjectorContract> findInjectorContractByPayload(@NotNull Payload payload);

  @Query(
      value =
          """
      SELECT ic.injector_contract_payload AS payload_id,
             COALESCE(ap.attack_pattern_ids, ARRAY[]::text[]) AS attack_pattern_ids,
             COALESCE(d.domain_ids, ARRAY[]::text[]) AS domain_ids,
             COALESCE(t.tag_ids, ARRAY[]::text[]) AS tag_ids
      FROM injectors_contracts ic
      LEFT JOIN LATERAL (
          SELECT array_remove(array_agg(icap.attack_pattern_id), NULL) AS attack_pattern_ids
          FROM injectors_contracts_attack_patterns icap
          WHERE icap.injector_contract_id = ic.injector_contract_id
      ) ap ON true
      LEFT JOIN LATERAL (
          SELECT array_remove(array_agg(icd.domain_id), NULL) AS domain_ids
          FROM injectors_contracts_domains icd
          WHERE icd.injector_contract_id = ic.injector_contract_id
      ) d ON true
      LEFT JOIN LATERAL (
          SELECT array_remove(array_agg(ict.tag_id), NULL) AS tag_ids
          FROM injector_contract_tags ict
          WHERE ict.injector_contract_id = ic.injector_contract_id
      ) t ON true
      WHERE ic.injector_contract_payload = :payloadId
      """,
      nativeQuery = true)
  Optional<RawPayloadRelatedIds> findRelatedIdsByPayloadId(@Param("payloadId") String payloadId);

  /**
   * Returns the (injector_contract_id, tenant_id) pair of EVERY contract row backed by the given
   * payload, across all tenants. Deleting a payload cascades to its contracts at the database level
   * ({@code injector_contract_payload ... ON DELETE CASCADE}), so the payload-delete path must
   * resolve these pairs BEFORE the delete to cascade-clean the chaining steps that reference the
   * doomed contracts (the step -> contract link lives only inside {@code step_data}, with no FK to
   * cascade on).
   *
   * <p>Deliberately NOT tenant-scoped, unlike the other native queries here: the sweep set must be
   * exactly what the database cascade deletes, whatever tenant it lives in. Today the schema
   * guarantees at most one row - {@code unique_injector_contract_payload} (V4_98) is a
   * single-column unique on the payload FK, so a payload backs one contract platform-wide - but
   * returning the (contract, tenant) pair keeps the delete path correct even if that 1:1 constraint
   * is ever relaxed. The caller sweeps each returned tenant separately; the sweep itself stays
   * strictly tenant-scoped per pair.
   *
   * @param payloadId the payload whose contract rows are enumerated
   * @return one (contract id, tenant id) pair per contract row referencing the payload
   */
  @Query(
      value =
          "SELECT ic.injector_contract_id AS injector_contract_id, ic.tenant_id AS tenant_id FROM injectors_contracts ic WHERE ic.injector_contract_payload = :payloadId",
      nativeQuery = true)
  List<RawContractTenant> findContractTenantPairsByPayloadId(@Param("payloadId") String payloadId);

  @Query(
      "SELECT CASE WHEN COUNT(ic) > 0 THEN true ELSE false END "
          + "FROM InjectorContract ic WHERE ic.compositeId.id = :id AND ic.payload IS NOT NULL")
  boolean existsByIdAndPayloadIsNotNull(@Param("id") String id);

  /**
   * Returns the ids of payload-bearing contracts that are not linked to any injector, scoped to a
   * tenant. Starter-pack imports create such orphan contracts before any payload-supporting
   * injector is registered (fresh platform); the payload injector adopts them on registration.
   */
  @Query(
      "SELECT ic.compositeId.id FROM InjectorContract ic "
          + "WHERE ic.compositeId.tenantId = :tenantId "
          + "AND ic.payload IS NOT NULL "
          + "AND ic.injectorLinks IS EMPTY")
  List<String> findContractIdsWithPayloadAndNoInjector(@Param("tenantId") String tenantId);

  /**
   * Returns the non-custom contracts that have neither a payload nor any injector link, scoped to a
   * tenant. A (now fixed) regression in the starter-pack import persisted payload contracts without
   * their payload reference on fresh platforms; those broken contracts are repaired by the {@code
   * V20260725_Fix_starter_pack_payload_contracts} runtime migration. Static injector contracts
   * imported before their injector registers (e.g. nmap/nuclei) also match this query; the
   * migration leaves them untouched because no orphan payload exists for them.
   */
  @Query(
      "SELECT ic FROM InjectorContract ic "
          + "WHERE ic.compositeId.tenantId = :tenantId "
          + "AND ic.payload IS NULL "
          + "AND (ic.custom IS NULL OR ic.custom = false) "
          + "AND ic.injectorLinks IS EMPTY")
  List<InjectorContract> findContractsWithoutPayloadAndInjector(@Param("tenantId") String tenantId);

  @Modifying
  @Query("DELETE FROM InjectorContract ic WHERE ic.compositeId.id IN :ids")
  void deleteAllById(@Param("ids") @NotNull List<String> ids);

  /**
   * Deletes injector contracts by their IDs scoped to a specific tenant.
   *
   * <p>Uses a native query intentionally to bypass Hibernate's SQM DELETE machinery ({@code
   * SqmMutationStrategyHelper.cleanUpCollectionTables}), which triggers an auto-flush
   * mid-execution. That auto-flush batches UPDATEs for dirty {@link InjectorContract} entities and
   * — due to a Hibernate 6.x behaviour with {@code @EmbeddedId} fields marked {@code updatable =
   * false} — can generate an UPDATE WHERE clause that omits {@code tenant_id}, matching multiple
   * rows and throwing {@code BatchedTooManyRowsAffectedException}.
   *
   * <p>The native DELETE avoids that path entirely; join-table rows are cleaned up automatically
   * via the database {@code ON DELETE CASCADE} constraints.
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM injectors_contracts"
              + " WHERE (injector_contract_id, tenant_id)"
              + " IN (SELECT unnest(CAST(:ids AS text[])), CAST(:tenantId AS text))",
      nativeQuery = true)
  void deleteAllByIdAndTenantId(
      @Param("ids") @NotNull String[] ids, @Param("tenantId") @NotNull String tenantId);

  @Query(
      value =
          """
        SELECT *
        FROM (
            SELECT ic.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY vulnerability.vulnerability_external_id
                       ORDER BY ic.injector_contract_updated_at DESC
                   ) AS rn
            FROM injectors_contracts ic
            JOIN injectors_contracts_vulnerabilities icv
              ON ic.injector_contract_id = icv.injector_contract_id
            JOIN vulnerabilities vulnerability
              ON icv.vulnerability_id = vulnerability.vulnerability_id
            WHERE LOWER(vulnerability.vulnerability_external_id) IN (:externalIds)
              AND ic.tenant_id = :#{#tenantContext.currentTenant}
        ) ranked
        WHERE ranked.rn <= :contractsPerVulnerability
        """,
      nativeQuery = true)
  Set<InjectorContract> findInjectorContractsByVulnerabilityIdIn(
      @Param("externalIds") Set<String> externalIds,
      @Param("contractsPerVulnerability") Integer contractsPerVulnerability);

  /**
   * Associates an injector contract to all injectors matching the given criteria, skipping those
   * that already have the contract assigned.
   *
   * <p>This method executes a native SQL {@code INSERT} that targets the {@code
   * injectors_injector_contracts} join table. For each injector belonging to the specified tenant
   * and matching the {@code payloads} flag, a new row is inserted only if no association with the
   * given contract already exists (idempotent by design).
   *
   * @param injectorIds the identifier of the injectors who should be considered.
   * @param contractId the identifier of the injector contract to associate with the matching
   *     injectors.
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO injectors_injector_contracts (injector_id, injector_contract_id, tenant_id)
          SELECT i.injector_id, :contractId, ic_ref.tenant_id
          FROM injectors i
          JOIN injectors_contracts ic_ref ON ic_ref.injector_contract_id = :contractId AND ic_ref.tenant_id = i.tenant_id
          WHERE i.injector_id IN (:injectorIds)
          AND NOT EXISTS (
            SELECT 1 FROM injectors_injector_contracts ic
            WHERE ic.injector_id = i.injector_id
            AND ic.injector_contract_id = :contractId
            AND ic.tenant_id = i.tenant_id
          )
        """,
      nativeQuery = true)
  void addContractToPayloadsInjectors(
      @Param("injectorIds") Set<String> injectorIds, @Param("contractId") String contractId);
}
