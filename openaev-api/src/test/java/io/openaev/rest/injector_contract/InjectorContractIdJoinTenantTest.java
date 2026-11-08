package io.openaev.rest.injector_contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.helper.InjectorContractRepositoryHelper;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawScenario;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A contract id repeats across tenants by design, so a query that joins on it must also join on the
 * tenant. The reads below run under a scope that holds both tenants, the scope of a caller who is a
 * member of both, so the statement inspector admits rows of either tenant and only the query's own
 * predicate keeps the second tenant's rows out.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Joins on an injector contract id stay inside the contract's tenant")
class InjectorContractIdJoinTenantTest extends IntegrationTest {

  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractRepositoryHelper injectorContractRepositoryHelper;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private String contractId;

  @BeforeEach
  void setup() throws Exception {
    tenantA = tenantHelper.createTenant("Contract id join tenant A");
    tenantB = tenantHelper.createTenant("Contract id join tenant B");
    DefaultTenantScopeTestListener.setAmbientScope(
        entityManager, tenantA.getId() + "," + tenantB.getId());
    TenantContext.setCurrentTenant(tenantA.getId());
    contractId = UUID.randomUUID().toString();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Given the contract search by attack pattern")
  class SearchByAttackPattern {

    private String payloadId;
    private String externalId;

    @BeforeEach
    void seedContracts() {
      payloadId = UUID.randomUUID().toString();
      externalId = "T9" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
      execute(
          "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
              + " VALUES (?1, ?2, 'MANUAL')",
          payloadId,
          tenantA.getId());
      execute(
          "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id, payload_identity,"
              + " payload_version) VALUES (?1, 'Command', 'Contract id join payload', ?2, ?1,"
              + " '1.0.0')",
          payloadId,
          tenantA.getId());
      insertContract(tenantA, payloadId, "Linux");
      insertContract(tenantB, null, "Linux");
    }

    @Test
    @DisplayName("a mapping held by the other tenant does not select this tenant's contract")
    void given_mappingOnlyInOtherTenant_should_notSelectContract() {
      // Arrange
      mapAttackPattern(tenantB);

      // Act
      List<InjectorContract> found = search();

      // Assert
      assertThat(found).extracting(InjectorContract::getId).doesNotContain(contractId);
    }

    @Test
    @DisplayName("a mapping held by the contract's own tenant selects the contract")
    void given_mappingInOwnTenant_should_selectContract() {
      // Arrange
      mapAttackPattern(tenantA);

      // Act
      List<InjectorContract> found = search();

      // Assert
      assertThat(found).extracting(InjectorContract::getId).containsExactly(contractId);
    }

    private List<InjectorContract> search() {
      entityManager.flush();
      return injectorContractRepositoryHelper.searchInjectorContractsByAttackPatternAndEnvironment(
          externalId, List.of("Linux"), 10, tenantA.getId());
    }

    private void mapAttackPattern(Tenant tenant) {
      String attackPatternId = UUID.randomUUID().toString();
      execute(
          "INSERT INTO attack_patterns"
              + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id, tenant_id)"
              + " VALUES (?1, 'Contract id join pattern', ?2, ?3)",
          attackPatternId,
          externalId,
          tenant.getId());
      execute(
          "INSERT INTO injectors_contracts_attack_patterns"
              + " (injector_contract_id, attack_pattern_id, tenant_id) VALUES (?1, ?2, ?3)",
          contractId,
          attackPatternId,
          tenant.getId());
    }
  }

  @Nested
  @DisplayName("Given the contract search by vulnerability")
  class SearchByVulnerability {

    private String externalId;

    @BeforeEach
    void seedContracts() {
      externalId = "cve-9999-" + UUID.randomUUID().toString().substring(0, 8);
      insertContract(tenantA, null, "Linux");
      insertContract(tenantB, null, "Linux");
    }

    @Test
    @DisplayName("a mapping held by the other tenant does not select this tenant's contract")
    void given_mappingOnlyInOtherTenant_should_notSelectContract() {
      // Arrange
      mapVulnerability(tenantB);

      // Act
      Set<InjectorContract> found = search();

      // Assert
      assertThat(found).extracting(InjectorContract::getId).doesNotContain(contractId);
    }

    @Test
    @DisplayName("a mapping held by the contract's own tenant selects the contract")
    void given_mappingInOwnTenant_should_selectContract() {
      // Arrange
      mapVulnerability(tenantA);

      // Act
      Set<InjectorContract> found = search();

      // Assert
      assertThat(found).extracting(InjectorContract::getId).containsExactly(contractId);
    }

    private Set<InjectorContract> search() {
      entityManager.flush();
      return injectorContractRepository.findInjectorContractsByVulnerabilityIdIn(
          Set.of(externalId), 10, tenantA.getId());
    }

    private void mapVulnerability(Tenant tenant) {
      String vulnerabilityId = UUID.randomUUID().toString();
      execute(
          "INSERT INTO vulnerabilities (vulnerability_id, tenant_id, vulnerability_external_id)"
              + " VALUES (?1, ?2, ?3)",
          vulnerabilityId,
          tenant.getId(),
          externalId);
      execute(
          "INSERT INTO injectors_contracts_vulnerabilities"
              + " (injector_contract_id, vulnerability_id, tenant_id) VALUES (?1, ?2, ?3)",
          contractId,
          vulnerabilityId,
          tenant.getId());
    }
  }

  @Nested
  @DisplayName("Given the scenario detail")
  class ScenarioDetail {

    private String scenarioId;

    @BeforeEach
    void seedScenario() {
      scenarioId = UUID.randomUUID().toString();
      insertContract(tenantA, null, "Linux");
      execute(
          "INSERT INTO scenarios (scenario_id, scenario_name, tenant_id)"
              + " VALUES (?1, 'Contract id join scenario', ?2)",
          scenarioId,
          tenantA.getId());
      execute(
          "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
              + " inject_depends_duration, inject_scenario, inject_injector_contract, tenant_id)"
              + " VALUES (?1, 'Contract id join inject', false, true, 0, ?2, ?3, ?4)",
          UUID.randomUUID().toString(),
          scenarioId,
          contractId,
          tenantA.getId());
    }

    @Test
    @DisplayName("the platforms of the other tenant's contract with the same id are not projected")
    void given_sameContractIdWithOtherPlatformsInOtherTenant_should_projectOwnPlatformsOnly() {
      // Arrange
      insertContract(tenantB, null, "Container");

      // Act
      RawScenario scenario = scenarioDetail();

      // Assert
      assertThat(scenario.getScenario_platforms()).containsExactly("Linux");
    }

    @Test
    @DisplayName("the platforms of the scenario's own contract are projected")
    void given_contractOnlyInOwnTenant_should_projectItsPlatforms() {
      // Act
      RawScenario scenario = scenarioDetail();

      // Assert
      assertThat(scenario.getScenario_platforms()).containsExactly("Linux");
    }

    @Test
    @DisplayName("the kill chain phases mapped by the other tenant's contract are not projected")
    void given_sameContractIdMappedInOtherTenant_should_notProjectItsKillChainPhase() {
      // Arrange
      insertContract(tenantB, null, "Linux");
      String foreignPhaseId = mapKillChainPhase(tenantB);

      // Act
      RawScenario scenario = scenarioDetail();

      // Assert
      assertThat(Objects.toString(scenario.getScenario_kill_chain_phases(), ""))
          .doesNotContain(foreignPhaseId);
    }

    @Test
    @DisplayName("the kill chain phases mapped by the scenario's own contract are projected")
    void given_contractMappedInOwnTenant_should_projectItsKillChainPhase() {
      // Arrange
      String ownPhaseId = mapKillChainPhase(tenantA);

      // Act
      RawScenario scenario = scenarioDetail();

      // Assert
      assertThat(scenario.getScenario_kill_chain_phases()).contains(ownPhaseId);
    }

    private String mapKillChainPhase(Tenant tenant) {
      String attackPatternId = UUID.randomUUID().toString();
      String phaseId = UUID.randomUUID().toString();
      execute(
          "INSERT INTO kill_chain_phases (phase_id, phase_name, phase_kill_chain_name,"
              + " phase_order, phase_shortname, phase_external_id, phase_description,"
              + " phase_stix_id, tenant_id)"
              + " VALUES (?1, 'Contract id join phase', 'mitre-attack', 1, 'contract-id-join',"
              + " ?1, 'Contract id join phase', ?1, ?2)",
          phaseId,
          tenant.getId());
      execute(
          "INSERT INTO attack_patterns"
              + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id, tenant_id)"
              + " VALUES (?1, 'Contract id join pattern', ?2, ?3)",
          attackPatternId,
          "T9" + attackPatternId.substring(0, 8),
          tenant.getId());
      execute(
          "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)"
              + " VALUES (?1, ?2)",
          attackPatternId,
          phaseId);
      execute(
          "INSERT INTO injectors_contracts_attack_patterns"
              + " (injector_contract_id, attack_pattern_id, tenant_id) VALUES (?1, ?2, ?3)",
          contractId,
          attackPatternId,
          tenant.getId());
      return phaseId;
    }

    private RawScenario scenarioDetail() {
      entityManager.flush();
      return scenarioRepository.getScenarioByIdAndTenantId(scenarioId);
    }
  }

  private void insertContract(Tenant tenant, String payloadId, String platform) {
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_platforms, injector_contract_payload, tenant_id)"
            + " VALUES (?1, '{}', ARRAY[?2], ?3, ?4)",
        contractId,
        platform,
        payloadId,
        tenant.getId());
  }

  private void execute(String sql, Object... parameters) {
    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < parameters.length; i++) {
      query.setParameter(i + 1, parameters[i]);
    }
    query.executeUpdate();
  }
}
