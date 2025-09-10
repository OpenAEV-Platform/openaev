package io.openbas.database.repository;

import io.openbas.database.model.Injector;
import io.openbas.database.model.InjectorContract;
import io.openbas.database.model.Payload;
import io.openbas.database.raw.RawInjectorsContrats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
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
          "SELECT injcont.* FROM injectors_contracts injcont "
              + "LEFT JOIN injectors_contracts_vulnerabilities injconvuln ON injcont.injector_contract_id = injconvuln.injector_contract_id "
              + "LEFT JOIN cves cve ON injconvuln.vulnerability_id = cve.cve_id "
              + "WHERE cve.cve_external_id = :externalId "
              + "ORDER BY injcont.injector_contract_updated_at LIMIT :injectsPerVulnerability",
      nativeQuery = true)
  Set<InjectorContract> findInjectorContractsByVulnerabilityId(
      @NotBlank String externalId, @NotNull Integer injectsPerVulnerability);
}
