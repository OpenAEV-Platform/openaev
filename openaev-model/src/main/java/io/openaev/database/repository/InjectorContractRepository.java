package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.raw.RawInjectorsContracts;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
    extends CrudRepository<InjectorContract, String>, JpaSpecificationExecutor<InjectorContract> {

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
              + "WHERE injcon.injector_contract_payload IS NULL "
              + "OR EXISTS ( "
              + "  SELECT 1 FROM users u "
              + "  INNER JOIN users_groups ug ON u.user_id = ug.user_id "
              + "  INNER JOIN groups g ON ug.group_id = g.group_id "
              + "  INNER JOIN grants gr ON g.group_id = gr.grant_group "
              + "  WHERE u.user_id = :userId "
              + "  AND gr.grant_resource = injcon.injector_contract_payload "
              + ") "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawInjectorsContracts> getAllRawInjectorsContractsWithoutPayloadOrGranted(
      @Param("userId") String userId);

  @NotNull
  Optional<InjectorContract> findById(@NotNull String id);

  @NotNull
  Optional<InjectorContract> findByIdOrExternalId(String id, String externalId);

  @NotNull
  List<InjectorContract> findInjectorContractsByInjector(@NotNull Injector injector);

  @NotNull
  Optional<InjectorContract> findInjectorContractByInjectorAndPayload(
      @NotNull Injector injector, @NotNull Payload payload);

  @NotNull
  List<InjectorContract> findInjectorContractsByPayload(@NotNull Payload payload);

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
        ) ranked
        WHERE ranked.rn <= :contractsPerVulnerability
        """,
      nativeQuery = true)
  Set<InjectorContract> findInjectorContractsByVulnerabilityIdIn(
      @Param("externalIds") Set<String> externalIds,
      @Param("contractsPerVulnerability") Integer contractsPerVulnerability);
}
