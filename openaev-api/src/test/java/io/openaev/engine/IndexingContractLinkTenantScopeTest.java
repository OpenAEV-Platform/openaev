package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.inject.InjectHandler;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.injectexpectation.InjectExpectationHandler;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * The indexing sweep aggregates an inject's attack patterns, kill chain phases and security domains
 * from the injector contract link tables ({@code injectors_contracts_attack_patterns}, {@code
 * injectors_contracts_domains}).
 *
 * <p>Those link tables are keyed {@code (..., injector_contract_id, tenant_id)}, because an
 * injector contract id is deliberately shared across tenants: {@code injectors_contracts} has the
 * composite primary key {@code (injector_contract_id, tenant_id)} and every tenant registers the
 * same published contract declarations. An aggregation that joins such a link table on {@code
 * injector_contract_id} alone therefore collects every tenant's rows for that contract.
 *
 * <p>The sweep runs under {@code TxCtx.allTenants()} ({@code EngineSyncExecutionJob}), so nothing
 * narrows the aggregation for it, and the values land in the indexed document of the owning tenant,
 * where they are filterable attributes.
 *
 * <p>Neither link table is a tenant-active table and the aggregated ids are returned without
 * joining their parent table, so the statement inspector does not narrow them either. These tests
 * consequently hold whatever the active-tables list is.
 */
@Transactional
@WithMockUser
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Indexing aggregations over the injector contract link tables")
class IndexingContractLinkTenantScopeTest extends IntegrationTest {

  @Autowired private InjectHandler injectHandler;
  @Autowired private InjectExpectationHandler injectExpectationHandler;

  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectorFixture injectorFixture;

  /** The contract id held by both tenants, as a published contract's id is. */
  private final String sharedContractId = UUID.randomUUID().toString();

  private final String foreignTenantId = UUID.randomUUID().toString();
  private final String foreignPatternId = UUID.randomUUID().toString();
  private final String foreignPhaseId = UUID.randomUUID().toString();
  private final String foreignDomainId = UUID.randomUUID().toString();

  private Instant from;
  private String ownPatternId;
  private String ownPhaseId;
  private String ownDomainId;
  private InjectorContractComposer.Composer ownContract;

  @BeforeEach
  void setUp() {
    attackPatternComposer.reset();
    domainComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    injectorContractComposer.reset();
    scenarioComposer.reset();
    // Bounds the indexing window to the rows this test writes, so the sweep's LIMIT cannot push
    // them out behind rows left by earlier classes.
    from = Instant.now().minusSeconds(5);
    seedOwnTenant();
    seedForeignTenant();
  }

  @Nested
  @DisplayName("An inject's indexed document")
  class AnInjectsIndexedDocument {

    @Test
    @DisplayName("carries only the attack patterns mapped to the contract in the inject's tenant")
    void given_theSameContractIdInTwoTenants_should_indexOnlyTheInjectsOwnAttackPatterns() {
      // Arrange
      String injectId = persistInject().getId();

      // Act
      EsInject document = fetchInject(injectId);

      // Assert
      assertThat(document.getBase_attack_patterns_side()).containsExactly(ownPatternId);
    }

    @Test
    @DisplayName("carries only the kill chain phases reached through the inject's own tenant")
    void given_theSameContractIdInTwoTenants_should_indexOnlyOwnKillChainPhases() {
      // Arrange
      String injectId = persistInject().getId();

      // Act
      EsInject document = fetchInject(injectId);

      // Assert
      assertThat(document.getBase_kill_chain_phases_side()).containsExactly(ownPhaseId);
    }
  }

  @Nested
  @DisplayName("An expectation's indexed document")
  class AnExpectationsIndexedDocument {

    @Test
    @DisplayName("carries only the attack patterns mapped to the contract in its own tenant")
    void given_theSameContractIdInTwoTenants_should_indexOnlyTheExpectationsOwnAttackPatterns() {
      // Arrange
      String expectationId = persistExpectation();

      // Act
      EsInjectExpectation document = fetchExpectation(expectationId);

      // Assert
      assertThat(document.getBase_attack_patterns_side()).containsExactly(ownPatternId);
    }

    @Test
    @DisplayName("carries only the security domains mapped to the contract in its own tenant")
    void given_theSameContractIdInTwoTenants_should_indexOnlyOwnSecurityDomains() {
      // Arrange
      String expectationId = persistExpectation();

      // Act
      EsInjectExpectation document = fetchExpectation(expectationId);

      // Assert
      assertThat(document.getBase_security_domains_side()).containsExactly(ownDomainId);
    }
  }

  // -- ARRANGE --

  /** The contract under the test user's tenant, mapped to that tenant's own pattern and domain. */
  private void seedOwnTenant() {
    AttackPatternComposer.Composer pattern =
        attackPatternComposer
            .forAttackPattern(AttackPatternFixture.createAttackPatternsWithExternalId("T9101.001"))
            .persist();
    DomainComposer.Composer domain =
        domainComposer
            .forDomain(DomainFixture.getDomainWithNameAndColour("own-domain", "#111111"))
            .persist();

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setId(sharedContractId);
    ownContract =
        injectorContractComposer
            .forInjectorContract(contract)
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withAttackPattern(pattern)
            .withDomain(domain)
            .persist();
    entityManager.flush();

    AttackPattern ownPattern = pattern.get();
    ownPatternId = ownPattern.getId();
    Domain ownDomain = domain.get();
    ownDomainId = ownDomain.getId();
    ownPhaseId = insertKillChainPhase("own-phase", ownPattern.getTenant().getId());
    linkPatternToPhase(ownPatternId, ownPhaseId);
    entityManager.flush();
  }

  /**
   * The same contract id under another tenant, mapped to that tenant's own pattern, phase and
   * domain. Written in SQL because the entity path would stamp the ambient tenant.
   */
  private void seedForeignTenant() {
    execute(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES (:id, :name)",
        "id",
        foreignTenantId,
        "name",
        "indexing-link-foreign-" + foreignTenantId);
    execute(
        "INSERT INTO attack_patterns (attack_pattern_id, tenant_id, attack_pattern_name,"
            + " attack_pattern_external_id) VALUES (:id, :tenant, 'foreign pattern', 'T9102.002')",
        "id",
        foreignPatternId,
        "tenant",
        foreignTenantId);
    execute(
        "INSERT INTO domains (domain_id, tenant_id, domain_name) VALUES (:id, :tenant,"
            + " 'foreign-domain')",
        "id",
        foreignDomainId,
        "tenant",
        foreignTenantId);
    insertKillChainPhase("foreign-phase", foreignTenantId, foreignPhaseId);
    linkPatternToPhase(foreignPatternId, foreignPhaseId);
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available) VALUES (:id,"
            + " :tenant, '{}', false)",
        "id",
        sharedContractId,
        "tenant",
        foreignTenantId);
    execute(
        "INSERT INTO injectors_contracts_attack_patterns (injector_contract_id, attack_pattern_id,"
            + " tenant_id) VALUES (:contract, :pattern, :tenant)",
        "contract",
        sharedContractId,
        "pattern",
        foreignPatternId,
        "tenant",
        foreignTenantId);
    execute(
        "INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id, tenant_id)"
            + " VALUES (:contract, :domain, :tenant)",
        "contract",
        sharedContractId,
        "domain",
        foreignDomainId,
        "tenant",
        foreignTenantId);
    seedForeignInjectAndExpectation();
    entityManager.flush();
  }

  /**
   * An inject and an agentless expectation of the other tenant, both on the shared contract. The
   * sweep batches every tenant at once, so the aggregations see the same contract id under two
   * tenants: without that, a per-contract aggregation looks correct simply because only one
   * tenant's rows are in the batch.
   */
  private void seedForeignInjectAndExpectation() {
    String injectId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_injector_contract) VALUES (:id, :tenant, 'foreign"
            + " inject', false, true, 0, :contract)",
        "id",
        injectId,
        "tenant",
        foreignTenantId,
        "contract",
        sharedContractId);
    execute(
        "INSERT INTO injects_expectations (inject_expectation_id, inject_id,"
            + " inject_expectation_type, inject_expiration_time) VALUES (:id, :inject,"
            + " 'DETECTION', 3600)",
        "id",
        UUID.randomUUID().toString(),
        "inject",
        injectId);
  }

  private Inject persistInject() {
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(ownContract);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(inject)
        .persist();
    entityManager.flush();
    entityManager.clear();
    return inject.get();
  }

  private String persistExpectation() {
    EndpointComposer.Composer endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
    endpoint.persist();
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createDefaultDetectionInjectExpectation();
    InjectExpectationComposer.Composer expectationWrapper =
        injectExpectationComposer.forExpectation(expectation).withEndpoint(endpoint);
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectorContract(ownContract)
            .withExpectation(expectationWrapper);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(inject)
        .persist();
    entityManager.flush();
    entityManager.clear();
    return expectation.getId();
  }

  // -- ACT --

  /**
   * Exactly one document per id: an aggregation that is not keyed on the tenant does not always
   * merge the foreign rows into the array, it can instead fan the row out into one document per
   * tenant, which a {@code findFirst} would hide.
   */
  private EsInject fetchInject(String injectId) {
    List<EsInject> documents =
        injectHandler.fetch(from, 5000).stream()
            .filter(document -> injectId.equals(document.getBase_id()))
            .toList();
    assertThat(documents).hasSize(1);
    return documents.getFirst();
  }

  private EsInjectExpectation fetchExpectation(String expectationId) {
    List<EsInjectExpectation> documents =
        injectExpectationHandler.fetch(from, 5000).stream()
            .filter(document -> expectationId.equals(document.getBase_id()))
            .toList();
    assertThat(documents).hasSize(1);
    return documents.getFirst();
  }

  // -- SQL helpers --

  private String insertKillChainPhase(String shortname, String tenantId) {
    return insertKillChainPhase(shortname, tenantId, UUID.randomUUID().toString());
  }

  private String insertKillChainPhase(String shortname, String tenantId, String phaseId) {
    execute(
        "INSERT INTO kill_chain_phases (phase_id, tenant_id, phase_name, phase_kill_chain_name,"
            + " phase_order, phase_shortname, phase_external_id) VALUES (:id, :tenant, :name,"
            + " 'indexing-link-test', 1, :shortname, :externalId)",
        "id",
        phaseId,
        "tenant",
        tenantId,
        "name",
        shortname,
        "shortname",
        shortname + "-" + phaseId,
        "externalId",
        shortname + "-" + phaseId);
    return phaseId;
  }

  private void linkPatternToPhase(String patternId, String phaseId) {
    execute(
        "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id) VALUES"
            + " (:pattern, :phase)",
        "pattern",
        patternId,
        "phase",
        phaseId);
  }

  private void execute(String sql, String... nameThenValue) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < nameThenValue.length; i += 2) {
      query.setParameter(nameThenValue[i], nameThenValue[i + 1]);
    }
    query.executeUpdate();
  }
}
