package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code SecurityCoverageInjectService} prunes a scenario's injects when a security coverage no
 * longer references any vulnerability or attack pattern: it asks which injects of the scenario run
 * a contract that carries such a mapping, then deletes exactly those.
 *
 * <p>The mapping lives in {@code injectors_contracts_vulnerabilities} and {@code
 * injectors_contracts_attack_patterns}, both keyed {@code (injector_contract_id, ..., tenant_id)}
 * because a contract id is deliberately shared across tenants ({@code injectors_contracts} has the
 * composite primary key {@code (injector_contract_id, tenant_id)}, and every tenant registers the
 * same published contract declarations). Matching on the contract id alone lets another tenant's
 * mapping decide whether this tenant's inject is pruned.
 *
 * <p>The scenario mappings diverge per tenant through {@code PUT /injector_contracts/{id}/mapping},
 * which rewrites the attack-pattern and vulnerability mapping of any contract, built-in ones
 * included.
 */
@Transactional
@WithMockUser
@DisplayName("Scenario inject pruning by contract mapping, across tenants")
class InjectCleanupContractLinkTenantScopeTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorFixture injectorFixture;

  /** The contract id held by both tenants, as a published contract's id is. */
  private final String sharedContractId = UUID.randomUUID().toString();

  private final String foreignTenantId = UUID.randomUUID().toString();

  private String scenarioId;

  @BeforeEach
  void setUp() {
    injectComposer.reset();
    injectorContractComposer.reset();
    scenarioComposer.reset();
    seedScenarioWithUnmappedContract();
    seedForeignTenantMappings();
  }

  @Test
  @DisplayName(
      "an inject is not pruned for a vulnerability mapping that only another tenant declares")
  void given_aVulnerabilityMappingInAnotherTenantOnly_should_notSelectTheInject() {
    // Act
    var selected = injectRepository.findInjectIdsWithVulnerableContractsByScenarioId(scenarioId);

    // Assert
    assertThat(selected).isEmpty();
  }

  @Test
  @DisplayName(
      "an inject is not pruned for an attack pattern mapping that only another tenant declares")
  void given_anAttackPatternMappingInAnotherTenantOnly_should_notSelectTheInject() {
    // Act
    var selected = injectRepository.findInjectIdsWithAttackPatternContractsByScenarioId(scenarioId);

    // Assert
    assertThat(selected).isEmpty();
  }

  @Test
  @DisplayName("the delete prunes no inject when only another tenant declares the mapping")
  void given_aVulnerabilityMappingInAnotherTenantOnly_should_deleteNoInject() {
    // Act
    injectRepository.deleteAllInjectsWithVulnerableContractsByScenarioId(scenarioId);
    entityManager.clear();

    // Assert
    assertThat(injectRepository.findByScenarioId(scenarioId)).isNotEmpty();
  }

  /**
   * One inject in the test user's tenant, on a contract that carries NO vulnerability and NO attack
   * pattern mapping in that tenant. Nothing here should ever be pruned.
   */
  private void seedScenarioWithUnmappedContract() {
    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setId(sharedContractId);
    InjectorContractComposer.Composer contractWrapper =
        injectorContractComposer
            .forInjectorContract(contract)
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .persist();

    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(contractWrapper);
    ScenarioComposer.Composer scenario =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(inject);
    scenario.persist();
    entityManager.flush();
    Scenario persisted = scenario.get();
    scenarioId = persisted.getId();
  }

  /**
   * The same contract id under another tenant, this one mapped to a vulnerability and an attack
   * pattern of its own. Written in SQL because the entity path would stamp the ambient tenant.
   */
  private void seedForeignTenantMappings() {
    String vulnerabilityId = UUID.randomUUID().toString();
    String patternId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :name)",
        "id",
        foreignTenantId,
        "name",
        "inject-prune-foreign-" + foreignTenantId);
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available) VALUES (:id,"
            + " :tenant, '{}', false)",
        "id",
        sharedContractId,
        "tenant",
        foreignTenantId);
    execute(
        "INSERT INTO vulnerabilities (vulnerability_id, tenant_id, vulnerability_external_id)"
            + " VALUES (:id, :tenant, :externalId)",
        "id",
        vulnerabilityId,
        "tenant",
        foreignTenantId,
        "externalId",
        "CVE-9999-" + vulnerabilityId.substring(0, 8));
    execute(
        "INSERT INTO injectors_contracts_vulnerabilities (injector_contract_id, vulnerability_id,"
            + " tenant_id) VALUES (:contract, :vulnerability, :tenant)",
        "contract",
        sharedContractId,
        "vulnerability",
        vulnerabilityId,
        "tenant",
        foreignTenantId);
    execute(
        "INSERT INTO attack_patterns (attack_pattern_id, tenant_id, attack_pattern_name,"
            + " attack_pattern_external_id) VALUES (:id, :tenant, 'foreign pattern', :externalId)",
        "id",
        patternId,
        "tenant",
        foreignTenantId,
        "externalId",
        "T9201." + patternId.substring(0, 3));
    execute(
        "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id, attack_pattern_id,"
            + " tenant_id) VALUES (:contract, :pattern, :tenant)",
        "contract",
        sharedContractId,
        "pattern",
        patternId,
        "tenant",
        foreignTenantId);
    entityManager.flush();
  }

  private void execute(String sql, String... nameThenValue) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < nameThenValue.length; i += 2) {
      query.setParameter(nameThenValue[i], nameThenValue[i + 1]);
    }
    query.executeUpdate();
  }
}
