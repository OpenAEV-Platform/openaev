package io.openbas.database.repository;

import io.openbas.database.model.Injector;
import io.openbas.database.model.InjectorContract;
import io.openbas.database.model.Payload;
import io.openbas.database.raw.RawInjectorsContrats;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InjectorContractRepository
    extends CrudRepository<InjectorContract, String>, JpaSpecificationExecutor<InjectorContract> {

  @Query(
      value =
          "SELECT injcon.injector_contract_id, "
              + "array_remove(array_agg(attpatt.attack_pattern_external_id), NULL) AS injector_contract_attack_patterns_external_id "
              + "FROM injectors_contracts injcon "
              + "LEFT JOIN injectors_contracts_attack_patterns injconatt ON injcon.injector_contract_id = injconatt.injector_contract_id "
              + "LEFT JOIN attack_patterns attpatt ON injconatt.attack_pattern_id = attpatt.attack_pattern_id "
              + "GROUP BY injcon.injector_contract_id",
      nativeQuery = true)
  List<RawInjectorsContrats> getAllRawInjectorsContracts();

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
  List<RawInjectorsContrats> getAllRawInjectorsContractsWithoutPayloadOrGranted(
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

  @Query(
      value =
          """
        SELECT *
        FROM (
            SELECT ic.*,
                   ROW_NUMBER() OVER (
                       PARTITION BY cve.cve_external_id
                       ORDER BY ic.injector_contract_updated_at DESC
                   ) AS rn
            FROM injectors_contracts ic
            JOIN injectors_contracts_vulnerabilities icv
              ON ic.injector_contract_id = icv.injector_contract_id
            JOIN cves cve
              ON icv.vulnerability_id = cve.cve_id
            WHERE LOWER(cve.cve_external_id) IN (:externalIds)
        ) ranked
        WHERE ranked.rn <= :contractsPerVulnerability
        """,
      nativeQuery = true)
  Set<InjectorContract> findInjectorContractsByVulnerabilityIdIn(
      @Param("externalIds") Set<String> externalIds,
      @Param("contractsPerVulnerability") Integer contractsPerVulnerability);
}
