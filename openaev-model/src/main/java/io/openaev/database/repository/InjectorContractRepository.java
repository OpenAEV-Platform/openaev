package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.Payload;
import io.openaev.database.raw.RawInjectorsContracts;
import io.openaev.database.raw.RawPayloadRelatedIds;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
   * Retrieves injector contracts accessible to a specific user.
   *
   * <p>Returns contracts that either have no payload (public contracts) or where the user has been
   * granted access to the payload through their group memberships.
   *
   * @param userId the ID of the user to check access for
   * @return list of raw injector contract projections the user can access
   */
  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "WHERE injcon.tenant_id = :#{#tenantContext.currentTenant} "
              + "AND (injcon.injector_contract_payload IS NULL "
              + "OR EXISTS ( "
              + "  SELECT 1 FROM users u "
              + "  INNER JOIN users_groups ug ON u.user_id = ug.user_id "
              + "  INNER JOIN groups g ON ug.group_id = g.group_id "
              + "  INNER JOIN grants gr ON g.group_id = gr.grant_group "
              + "  WHERE u.user_id = :userId "
              + "  AND gr.grant_resource = injcon.injector_contract_payload "
              + ")) "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawInjectorsContracts> getAllRawInjectorsContractsWithoutPayloadOrGranted(
      @Param("userId") String userId);

  @NotNull
  @Query("SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id = :id")
  Optional<InjectorContract> findById(@Param("id") @NotNull String id);

  @NotNull
  @Query(
      "SELECT ic FROM InjectorContract ic WHERE ic.compositeId.id = :id OR ic.externalId = :externalId")
  Optional<InjectorContract> findByIdOrExternalId(
      @Param("id") String id, @Param("externalId") String externalId);

  @NotNull
  List<InjectorContract> findByInjectorsContaining(@NotNull Injector injector);

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

  @Modifying
  @Query("DELETE FROM InjectorContract ic WHERE ic.compositeId.id = :id")
  void deleteById(@Param("id") @NotNull String id);

  @Modifying
  @Query("DELETE FROM InjectorContract ic WHERE ic.compositeId.id IN :ids")
  void deleteAllById(@Param("ids") @NotNull List<String> ids);

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
}
