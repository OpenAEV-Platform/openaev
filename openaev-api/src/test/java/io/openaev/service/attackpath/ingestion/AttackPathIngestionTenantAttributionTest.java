package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectExpectationTrace;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.VulnerabilityInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationTraceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The create as production actually runs it. {@code InjectsExecutionJob} is a cross-tenant
 * executor: it sets no tenant scope, so anything the run writes must carry its own attribution. The
 * existing ingestion tests set {@link TenantContext} by hand before calling the service, so they
 * cannot see what production does.
 *
 * <p>What production does is worse than losing the row. {@code TenantContext.getCurrentTenant()}
 * never returns null, it falls back to the default tenant, and {@code TenantBaseListener} fills any
 * null entity tenant from it. So an unscoped write is not rejected: it is silently attributed to
 * the default tenant. The owning tenant sees nothing, and every tenant's rows pile up under one
 * that should not hold them.
 *
 * <p>Hence the shape of this test: no ambient scope, and ground truth read through raw JDBC. A
 * scoped read could not distinguish a misplaced row from a missing one, which is exactly how this
 * stayed invisible.
 *
 * <p>Deliberately not {@code @Transactional}: once the write opens its own scoped transaction it
 * commits independently of any test transaction, so the rows outlive a rollback. Explicit cleanup
 * instead, per the activate-tenant-table runbook.
 */
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path Phase A: the create attributes rows to the inject's tenant")
class AttackPathIngestionTenantAttributionTest extends IntegrationTest {

  private static final String SIM = "SIM-REALPATH";
  private static final String INJECT_ID = "inj-realpath";

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationTraceRepository injectExpectationTraceRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;

  private JdbcTemplate jdbc;
  private Tenant tenant;

  @BeforeEach
  void createTenantAndDropAnyAmbientScope() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("ap-realpath");
    // The point of the test: reproduce the executor's context, which has no scope. Clearing it
    // explicitly stops a scope leaked by another test from handing us a false green.
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanCommittedRows() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?", SIM);
    tenantHelper.deleteCommittedTenants(tenant.getId());
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("with no ambient scope, the run's row lands under the inject's own tenant")
  void createAttributesTheRowToTheInjectTenant() {
    Target target = persistTarget();

    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    String rowId =
        AttackPathIds.executionNode(INJECT_ID, target.endpoint().getId(), target.agent().getId());
    assertThat(rawTenantOf(rowId))
        .as("the run's row must exist and carry the inject's tenant, with no ambient scope set")
        .isEqualTo(tenant.getId());
  }

  @Test
  @DisplayName("given_resultsWithoutSourceId_should_persistCollectorRowsUsingSourceNameFallback")
  void given_resultsWithoutSourceId_should_persistCollectorRowsUsingSourceNameFallback() {
    // Arrange
    Target target = persistTarget();
    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    PreventionInjectExpectation prevention = new PreventionInjectExpectation();
    prevention.setAgent(target.agent());
    InjectExpectationResult first = new InjectExpectationResult();
    first.setSourceName("Defender");
    first.setResult("Prevented");
    first.setDate("2026-07-28T10:00:00Z");
    InjectExpectationResult second = new InjectExpectationResult();
    second.setSourceName("Defender");
    second.setResult("Partially Prevented");
    second.setDate("2026-07-28T10:01:00Z");
    prevention.setResults(List.of(first, second));

    // Act
    runAsTheExecutorWould(
        () -> ingestionService.upsertExecutionCollectors(inject, List.of(prevention)));

    // Assert
    String executionId =
        AttackPathIds.executionNode(INJECT_ID, target.endpoint().getId(), target.agent().getId());
    Integer rowCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM attackpath_execution_collector "
                + "WHERE attackpath_execution_collector_simulation_id = ? "
                + "AND attackpath_execution_id = ? AND tenant_id = ?",
            Integer.class,
            SIM,
            executionId,
            tenant.getId());

    assertThat(rowCount)
        .as(
            "source-less rows are kept with sourceName fallback, so expected buckets remain visible")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("given_vulnerabilityCollectorResult_should_persistVulnerabilityCollectorRow")
  void given_vulnerabilityCollectorResult_should_persistVulnerabilityCollectorRow() {
    // Arrange
    Target target = persistTarget();
    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    TenantContext.setCurrentTenant(tenant.getId());
    Inject persistedInject = InjectFixture.getDefaultInject();
    persistedInject.setTenant(tenant);
    persistedInject = injectRepository.save(persistedInject);
    VulnerabilityInjectExpectation vulnerability =
        InjectExpectationFixture.createVulnerabilityInjectExpectation(
            persistedInject, target.agent());
    InjectExpectationResult result = new InjectExpectationResult();
    result.setSourceId("collector-vuln-1");
    result.setSourceName("Defender");
    result.setSourceType("EDR");
    result.setResult("Vulnerable");
    result.setDate("2026-07-29T10:00:00Z");
    vulnerability.setResults(List.of(result));
    VulnerabilityInjectExpectation savedExpectation =
        injectExpectationRepository.save(vulnerability);
    TenantContext.clearCurrentTenant();

    // Act
    runAsTheExecutorWould(
        () -> ingestionService.upsertExecutionCollectors(inject, List.of(savedExpectation)));

    // Assert
    String executionId =
        AttackPathIds.executionNode(INJECT_ID, target.endpoint().getId(), target.agent().getId());
    Integer rowCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM attackpath_execution_collector "
                + "WHERE attackpath_execution_collector_simulation_id = ? "
                + "AND attackpath_execution_id = ? AND tenant_id = ? "
                + "AND attackpath_execution_collector_expectation_type = 'VULNERABILITY'",
            Integer.class,
            SIM,
            executionId,
            tenant.getId());

    assertThat(rowCount).isEqualTo(1);
  }

  @Test
  @DisplayName("given_traceForSourceId_should_persistCollectorAlertsJson")
  void given_traceForSourceId_should_persistCollectorAlertsJson() {
    // Arrange
    Target target = persistTarget();
    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    SecurityPlatform platform = new SecurityPlatform();
    platform.setExternalReference("platform-trace-1");
    platform.setName("Trace platform");
    platform.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR);
    TenantContext.setCurrentTenant(tenant.getId());
    platform = securityPlatformRepository.save(platform);
    Inject persistedInject = InjectFixture.getDefaultInject();
    persistedInject.setTenant(tenant);
    persistedInject = injectRepository.save(persistedInject);

    PreventionInjectExpectation prevention =
        InjectExpectationFixture.createPreventionInjectExpectation(persistedInject, target.agent());
    InjectExpectationResult result = new InjectExpectationResult();
    result.setSourceId(platform.getId());
    result.setSourceName("Trace platform");
    result.setSourceType("EDR");
    result.setResult("Prevented");
    result.setDate("2026-07-31T10:00:00Z");
    prevention.setResults(List.of(result));
    PreventionInjectExpectation savedExpectation = injectExpectationRepository.save(prevention);

    InjectExpectationTrace trace = new InjectExpectationTrace();
    trace.setInjectExpectation(savedExpectation);
    trace.setSecurityPlatform(platform);
    trace.setAlertName("Trace Alert A");
    trace.setAlertLink("https://security.microsoft.com/alerts/trace-a");
    trace.setAlertDate(java.time.Instant.parse("2026-07-31T10:00:00Z"));
    injectExpectationTraceRepository.save(trace);
    TenantContext.clearCurrentTenant();

    // Act
    runAsTheExecutorWould(
        () -> ingestionService.upsertExecutionCollectors(inject, List.of(savedExpectation)));

    // Assert
    String executionId =
        AttackPathIds.executionNode(INJECT_ID, target.endpoint().getId(), target.agent().getId());
    String alertsJson =
        jdbc.queryForObject(
            "SELECT attackpath_execution_collector_alerts FROM attackpath_execution_collector "
                + "WHERE attackpath_execution_collector_simulation_id = ? "
                + "AND attackpath_execution_id = ? AND tenant_id = ? "
                + "AND attackpath_execution_collector_expectation_type = 'PREVENTION'",
            String.class,
            SIM,
            executionId,
            tenant.getId());
    assertThat(alertsJson).contains("Trace Alert A").contains("trace-a");
    // Guard against double-encoding: the column must hold a real jsonb array, not a jsonb
    // string wrapping the serialized alerts payload.
    String alertsJsonbType =
        jdbc.queryForObject(
            "SELECT jsonb_typeof(attackpath_execution_collector_alerts) "
                + "FROM attackpath_execution_collector "
                + "WHERE attackpath_execution_collector_simulation_id = ? "
                + "AND attackpath_execution_id = ? AND tenant_id = ? "
                + "AND attackpath_execution_collector_expectation_type = 'PREVENTION'",
            String.class,
            SIM,
            executionId,
            tenant.getId());
    assertThat(alertsJsonbType).isEqualTo("array");
  }

  @Test
  @DisplayName("given_collectorSourceId_should_resolveTraceViaSecurityPlatformExternalReference")
  void given_collectorSourceId_should_resolveTraceViaSecurityPlatformExternalReference() {
    // Arrange
    Target target = persistTarget();
    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    String collectorExternalReference = "collector-ext-ref-1";
    SecurityPlatform platform = new SecurityPlatform();
    platform.setExternalReference(collectorExternalReference);
    platform.setName("Ext-ref platform");
    platform.setSecurityPlatformType(SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM);
    TenantContext.setCurrentTenant(tenant.getId());
    platform = securityPlatformRepository.save(platform);
    Inject persistedInject = InjectFixture.getDefaultInject();
    persistedInject.setTenant(tenant);
    persistedInject = injectRepository.save(persistedInject);

    PreventionInjectExpectation prevention =
        InjectExpectationFixture.createPreventionInjectExpectation(persistedInject, target.agent());
    InjectExpectationResult result = new InjectExpectationResult();
    result.setSourceId(collectorExternalReference);
    result.setSourceName("Collector source");
    result.setSourceType("SIEM");
    result.setResult("Prevented");
    result.setDate("2026-08-01T11:00:00Z");
    prevention.setResults(List.of(result));
    PreventionInjectExpectation savedExpectation = injectExpectationRepository.save(prevention);

    InjectExpectationTrace trace = new InjectExpectationTrace();
    trace.setInjectExpectation(savedExpectation);
    trace.setSecurityPlatform(platform);
    trace.setAlertName("External reference alert");
    trace.setAlertLink("https://security.microsoft.com/alerts/ext-ref");
    trace.setAlertDate(java.time.Instant.parse("2026-08-01T11:00:00Z"));
    injectExpectationTraceRepository.save(trace);
    TenantContext.clearCurrentTenant();

    // Act
    runAsTheExecutorWould(
        () -> ingestionService.upsertExecutionCollectors(inject, List.of(savedExpectation)));

    // Assert
    String executionId =
        AttackPathIds.executionNode(INJECT_ID, target.endpoint().getId(), target.agent().getId());
    String alertsJson =
        jdbc.queryForObject(
            "SELECT attackpath_execution_collector_alerts FROM attackpath_execution_collector "
                + "WHERE attackpath_execution_collector_simulation_id = ? "
                + "AND attackpath_execution_id = ? AND tenant_id = ? "
                + "AND attackpath_execution_collector_expectation_type = 'PREVENTION'",
            String.class,
            SIM,
            executionId,
            tenant.getId());
    assertThat(alertsJson).contains("External reference alert").contains("ext-ref");
  }

  @Test
  @DisplayName("the row is readable under its own tenant's scope and invisible under another's")
  void theRowIsVisibleOnlyUnderItsOwnTenantScope() throws Exception {
    Inject inject = injectFor(persistTarget().endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));
    Tenant other = tenantHelper.createTenantWithCurrentUser("ap-realpath-other");
    try {
      // Positive control first: without it, an empty cross-tenant read would also be satisfied by
      // a write that never happened, which is precisely the bug this whole pass is about.
      assertThat(scopedGraphRowsFor(tenant.getId())).isNotEmpty();
      assertThat(scopedGraphRowsFor(other.getId())).isEmpty();
    } finally {
      tenantHelper.deleteCommittedTenants(other.getId());
    }
  }

  private List<?> scopedGraphRowsFor(String tenantId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId), () -> executionRepository.findGraphRows(SIM));
  }

  @Test
  @DisplayName("an inject targeting an asset group records its member endpoints")
  void assetGroupMembersAreRecorded() {
    // Persist a group with one member endpoint (agent-backed), under the tenant, committed so the
    // run's readOnly resolution sees it.
    TenantContext.setCurrentTenant(tenant.getId());
    AssetGroupComposer.Composer groupComposer =
        assetGroupComposer
            .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("ap-grp"))
            .withAsset(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(
                        agentComposer.forAgent(
                            AgentFixture.createDefaultAgentSession(
                                executorFixture.getDefaultExecutor(tenant.getId())))));
    groupComposer.persist();
    AssetGroup group = groupComposer.get();
    TenantContext.clearCurrentTenant();

    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    Exercise exercise = new Exercise();
    exercise.setId(SIM);
    inject.setExercise(exercise);
    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");
    injector.setType("openaev_implant");
    inject.setInjector(injector);
    inject.setInjectorContract(contract());
    inject.setAssetGroups(java.util.List.of(group)); // group only, no direct assets
    inject.setTenant(tenant);

    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    assertThat(rawRowCountForStep("step-realpath"))
        .as("an inject targeting only an asset group must still record its member endpoints")
        .isPositive();
  }

  @Test
  @DisplayName("an agentless injector inject targeting assets records injector->asset rows")
  void injectorAssetsAreRecorded() {
    // Regression for the target_selector "asset" vs stored "assets" mismatch: an agentless injector
    // inject (needsExecutor=false) with target_selector="assets" must record one injector->asset
    // row
    // per targeted asset. The source is the injector, so the endpoint needs no agent.
    TenantContext.setCurrentTenant(tenant.getId());
    io.openaev.database.model.Endpoint endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
    TenantContext.clearCurrentTenant();

    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    Exercise exercise = new Exercise();
    exercise.setId(SIM);
    inject.setExercise(exercise);
    Injector injector = new Injector();
    injector.setId("injector-nmap");
    injector.setName("Nmap");
    injector.setType("openaev_nmap");
    inject.setInjector(injector);
    inject.setInjectorContract(injectorContract());
    inject.setAssets(java.util.List.of(endpoint));
    inject.setAssetGroups(java.util.List.of());
    inject.setContent(
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
            .objectNode()
            .put("target_selector", "assets"));
    inject.setTenant(tenant);

    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    String rowId = AttackPathIds.executionNode(INJECT_ID, endpoint.getId(), "injector-nmap");
    assertThat(rawTenantOf(rowId))
        .as("the injector 'assets' selector must record an injector->asset row for the target")
        .isEqualTo(tenant.getId());
  }

  private InjectorContract injectorContract() {
    InjectorContract contract = new InjectorContract();
    contract.setNeedsExecutor(false);
    contract.setExternalId("contract-nmap");
    return contract;
  }

  // No test pins the direct+grouped dedup on purpose: the deterministic row id plus ON CONFLICT DO
  // NOTHING already collapse any duplicate to one row, so the dedup has no observable effect on the
  // result and a behaviour test cannot distinguish it. It stays in the code as an efficiency
  // measure
  // (it avoids building duplicate edges), documented on resolvedEndpoints.

  @Test
  @DisplayName("a direct asset that is a lazy proxy is unproxied, not dropped")
  void proxiedDirectAssetIsNotDropped() {
    TenantContext.setCurrentTenant(tenant.getId());
    io.openaev.database.model.Endpoint persisted =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(
                agentComposer.forAgent(
                    AgentFixture.createDefaultAgentSession(
                        executorFixture.getDefaultExecutor(tenant.getId()))))
            .persist()
            .get();
    String endpointId = persisted.getId();
    TenantContext.clearCurrentTenant();

    runAsTheExecutorWould(
        () -> {
          // A reference typed as the Asset base class is a proxy: `instanceof Endpoint` is false on
          // it until unproxied. This is what the resolver's Hibernate.unproxy guards against.
          Asset proxy = entityManager.getReference(Asset.class, endpointId);
          Inject inject = new Inject();
          inject.setId(INJECT_ID);
          Exercise exercise = new Exercise();
          exercise.setId(SIM);
          inject.setExercise(exercise);
          Injector injector = new Injector();
          injector.setName("OpenAEV Implant");
          injector.setType("openaev_implant");
          inject.setInjector(injector);
          inject.setInjectorContract(contract());
          inject.setAssets(java.util.List.of(proxy));
          inject.setTenant(tenant);
          // Resolves directly rather than through onRun on purpose: this proxy is bound to the
          // ambient session, so it must be unproxied in this same transaction, not across onRun's
          // REQUIRES_NEW boundary. The concern under test is the resolver's unproxy, not the tx.
          ingestionService.persistExecution(
              ingestionService.getAttackPathExecution(inject, step(), ""));
        });

    assertThat(rawRowCountForStep("step-realpath"))
        .as("a proxied endpoint must be recognised and recorded, not dropped by a bare instanceof")
        .isPositive();
  }

  @Test
  @DisplayName("a failing create cannot roll back the run that triggered it")
  void aFailingCreateDoesNotPoisonTheRunTransaction() {
    Inject doomed = injectFor(persistTarget().endpoint());
    // A tenant that does not exist: the endpoint resolves only under the real tenant, so reading it
    // under this one throws inside the create, whatever the reason.
    doomed.setTenant(new Tenant("no-such-tenant"));
    String marker = "ap-poison-marker";

    // The create's failure must not poison the run's own transaction: the caller catches it and the
    // run carries on, exactly as InjectExecutionStep's guard lets it.
    runAsTheExecutorWould(
        () -> {
          // Asserted, not assumed: if the doomed create silently succeeded, the catch below would
          // never fire and this test would prove nothing at all.
          assertThatThrownBy(() -> ingestionService.onRun(doomed, step(), ""))
              .isInstanceOf(RuntimeException.class);
          // Then the run carries on, exactly as InjectExecutionStep's guard lets it.
          tenantRepository.save(TenantFixture.getTenant(marker));
        });

    assertThat(rawTenantCountByName(marker))
        .as("the run's own work must have committed despite the create failing")
        .isEqualTo(1);
    jdbc.update("DELETE FROM tenants WHERE tenant_name = ?", marker);
  }

  @Test
  @DisplayName("the run's row carries the columns extracted from the inject")
  void createFreezesTheColumnsExtractedFromTheInject() {
    Target target = persistTarget();
    Inject inject = injectFor(target.endpoint());
    runAsTheExecutorWould(() -> ingestionService.onRun(inject, step(), ""));

    // Read back from the database rather than through the ORM: a scoped read would be fail-closed
    // here, and the point is what actually landed on disk.
    Map<String, Object> row =
        rawRow(
            AttackPathIds.executionNode(
                INJECT_ID, target.endpoint().getId(), target.agent().getId()));
    assertThat(row)
        .containsEntry("attackpath_execution_simulation_id", SIM)
        .containsEntry("attackpath_execution_step_id", "step-realpath")
        .containsEntry("attackpath_execution_contract_external_id", "contract-realpath")
        .containsEntry("attackpath_execution_payload_id", "cmd-realpath")
        .containsEntry("attackpath_execution_payload_name", "crackmapexec")
        .containsEntry("attackpath_execution_injector_type", "openaev_implant")
        .containsEntry("attackpath_execution_step_template_id", "tmpl-realpath")
        // Local command on an asset: source is the agent's endpoint, target is that endpoint.
        .containsEntry("attackpath_execution_source_kind", "AGENT")
        .containsEntry("attackpath_execution_source_asset_id", target.endpoint().getId())
        .containsEntry("attackpath_execution_source_hostname", target.endpoint().getHostname())
        .containsEntry(
            "attackpath_execution_source_ip", String.join(",", target.endpoint().getIps()))
        .containsEntry(
            "attackpath_execution_source_platform", target.endpoint().getPlatform().name())
        .containsEntry("attackpath_execution_agent_id", target.agent().getId())
        .containsEntry("attackpath_execution_agent_privilege", "admin")
        .containsEntry("attackpath_execution_target_kind", "ASSET")
        .containsEntry("attackpath_execution_target_key", target.endpoint().getId());
  }

  @Test
  @DisplayName("an inject with no simulation is out of the attack path and records nothing")
  void createRecordsNothingWithoutASimulation() {
    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setTenant(tenant);
    // no exercise set

    runAsTheExecutorWould(() -> ingestionService.onRun(inject, new Step(), ""));

    assertThat(rawRowCountForStep("step-realpath")).isZero();
  }

  @Test
  @DisplayName(
      "delete clears the simulation's executions, findings and links under its tenant, and leaves"
          + " another tenant's rows")
  void deleteClearsTheSimulationScopedToItsTenant() throws Exception {
    Tenant other = tenantHelper.createTenantWithCurrentUser("ap-realpath-del-other");
    try {
      // The same simulation id under two tenants, seeded by native INSERT with an explicit
      // tenant_id
      // (the inspector does not block VALUES inserts): one execution + one finding + their link
      // each.
      seedExecutionFindingLink(tenant.getId(), "exec-own", "find-own");
      seedExecutionFindingLink(other.getId(), "exec-other", "find-other");

      // The delete runs with no ambient scope, exactly as the reset/delete flow triggers it.
      runAsTheExecutorWould(() -> ingestionService.deleteAllBySimulationId(SIM, tenant.getId()));

      assertThat(rawExecCount(tenant.getId())).as("own executions deleted").isZero();
      assertThat(rawFindingCount(tenant.getId())).as("own findings deleted").isZero();
      assertThat(rawCollectorCount(tenant.getId())).as("own collectors deleted").isZero();
      assertThat(rawLinkCount("exec-own")).as("own link cascade-cleared").isZero();
      assertThat(rawExecCount(other.getId()))
          .as("other tenant's executions untouched")
          .isEqualTo(1);
      assertThat(rawFindingCount(other.getId()))
          .as("other tenant's findings untouched")
          .isEqualTo(1);
      assertThat(rawCollectorCount(other.getId()))
          .as("other tenant's collectors untouched")
          .isEqualTo(1);
      assertThat(rawLinkCount("exec-other")).as("other tenant's link untouched").isEqualTo(1);
    } finally {
      tenantHelper.deleteCommittedTenants(other.getId());
    }
  }

  @Test
  @DisplayName("hard-deleting a simulation clears its attack-path executions and findings")
  void hardDeleteClearsTheSimulationsAttackPath() {
    // Attack-path rows have no FK to the simulation, so exercise deletion does not cascade them.
    // deleteById must clear them explicitly, else they orphan once the simulation is gone.
    TenantContext.setCurrentTenant(tenant.getId());
    Exercise exercise =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
    String simId = exercise.getId();
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
            + " attackpath_execution_target_kind, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at) VALUES ('hd-exec', ?, ?, 'INJECTOR', 'ASSET', 'k',"
            + " now())",
        tenant.getId(),
        simId);
    jdbc.update(
        "INSERT INTO attackpath_finding (attackpath_finding_id, tenant_id,"
            + " attackpath_finding_simulation_id, attackpath_finding_type,"
            + " attackpath_finding_value, attackpath_finding_endpoint_key)"
            + " VALUES ('hd-find', ?, ?, 'cve', 'CVE-1', 'k')",
        tenant.getId(),
        simId);
    jdbc.update(
        "INSERT INTO attackpath_execution_finding (execution_id, finding_id)"
            + " VALUES ('hd-exec', 'hd-find')");

    exerciseService.deleteById(simId);
    TenantContext.clearCurrentTenant();

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM attackpath_execution"
                    + " WHERE attackpath_execution_simulation_id = ?",
                Integer.class,
                simId))
        .as("executions cleared on hard delete")
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM attackpath_finding"
                    + " WHERE attackpath_finding_simulation_id = ?",
                Integer.class,
                simId))
        .as("findings cleared on hard delete")
        .isZero();
  }

  /**
   * Reproduces the executor's shape, which the attribution depends on: {@code
   * InjectExecutionStep.run} is {@code @Transactional(rollbackFor = Exception.class)}, so the hook
   * always runs inside an already-open transaction. Calling the service bare would be a different
   * situation from production, and would make the scoped-transaction primitive refuse the nesting
   * it is designed for.
   */
  private void runAsTheExecutorWould(Runnable hook) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> hook.run());
  }

  /** Ground truth: raw JDBC sees the row whatever the scope, so a misplaced row cannot hide. */
  private String rawTenantOf(String rowId) {
    return jdbc.query(
        "SELECT tenant_id FROM attackpath_execution WHERE attackpath_execution_id = ?",
        rs -> rs.next() ? rs.getString(1) : null,
        rowId);
  }

  private Map<String, Object> rawRow(String rowId) {
    return jdbc.queryForMap(
        "SELECT * FROM attackpath_execution WHERE attackpath_execution_id = ?", rowId);
  }

  private Integer rawTenantCountByName(String name) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM tenants WHERE tenant_name = ?", Integer.class, name);
  }

  private Integer rawRowCountForStep(String stepId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution WHERE attackpath_execution_step_id = ?",
        Integer.class,
        stepId);
  }

  // Seeds one execution + one finding + their link for SIM under the given tenant, native INSERTs
  // with an explicit tenant_id (VALUES inserts pass the inspector; this is ground-truth seeding).
  private void seedExecutionFindingLink(String tenantId, String execId, String findId) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
            + " attackpath_execution_target_kind, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at) VALUES (?, ?, ?, 'INJECTOR', 'ASSET', 'dc-01',"
            + " now())",
        execId,
        tenantId,
        SIM);
    jdbc.update(
        "INSERT INTO attackpath_finding (attackpath_finding_id, tenant_id,"
            + " attackpath_finding_simulation_id, attackpath_finding_type,"
            + " attackpath_finding_value, attackpath_finding_endpoint_key)"
            + " VALUES (?, ?, ?, 'cve', 'CVE-1', 'dc-01')",
        findId,
        tenantId,
        SIM);
    jdbc.update(
        "INSERT INTO attackpath_execution_finding (execution_id, finding_id) VALUES (?, ?)",
        execId,
        findId);
    jdbc.update(
        "INSERT INTO attackpath_execution_collector ("
            + "attackpath_execution_collector_id, tenant_id, attackpath_execution_collector_simulation_id,"
            + "attackpath_execution_id, attackpath_execution_collector_expectation_type,"
            + "attackpath_execution_collector_result_status_label) VALUES (?, ?, ?, ?, 'DETECTION', 'Detected')",
        "col-" + execId,
        tenantId,
        SIM,
        execId);
  }

  private Integer rawExecCount(String tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution"
            + " WHERE attackpath_execution_simulation_id = ? AND tenant_id = ?",
        Integer.class,
        SIM,
        tenantId);
  }

  private Integer rawFindingCount(String tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_finding"
            + " WHERE attackpath_finding_simulation_id = ? AND tenant_id = ?",
        Integer.class,
        SIM,
        tenantId);
  }

  private Integer rawLinkCount(String executionId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution_finding WHERE execution_id = ?",
        Integer.class,
        executionId);
  }

  private Integer rawCollectorCount(String tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution_collector"
            + " WHERE attackpath_execution_collector_simulation_id = ? AND tenant_id = ?",
        Integer.class,
        SIM,
        tenantId);
  }

  private record Target(Endpoint endpoint, Agent agent) {}

  // Eva's resolution reads the endpoint, its agent and the agent's executor back through the
  // services, so the whole graph must be persisted under the inject's tenant.
  // executorFixture.getDefaultExecutor(tenantId) creates the executor explicitly under the given
  // tenant (Executor carries no ambient tenant default on v2), so it must run inside this scope
  // (the agents FK is composite on (executor, tenant_id)).
  private Target persistTarget() {
    TenantContext.setCurrentTenant(tenant.getId());
    Agent agent =
        AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor(tenant.getId()));
    Endpoint endpoint =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agentComposer.forAgent(agent))
            .persist()
            .get();
    TenantContext.clearCurrentTenant();
    return new Target(endpoint, agent);
  }

  // The engine hands the inject over in memory with its contract attached; only the EXECUTION rows
  // are persisted. The contract carries a needs-executor Command payload, so the run resolves the
  // agent's own endpoint as both source and target (a local command with no endpoint-typed arg).
  private Inject injectFor(Endpoint endpoint) {
    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");
    injector.setType("openaev_implant");
    Exercise exercise = new Exercise();
    exercise.setId(SIM);
    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setTitle(
        "crackmapexec"); // frozen as payload_name (setGlobalInformation reads getTitle())
    inject.setExercise(exercise);
    inject.setInjector(injector);
    inject.setInjectorContract(contract());
    inject.setAssets(List.of(endpoint));
    inject.setTenant(tenant);
    return inject;
  }

  private InjectorContract contract() {
    Command command = new Command();
    command.setId("cmd-realpath");
    command.setName("crackmapexec");
    command.setContent("cme --local-auth");

    InjectorContract contract = new InjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(command);
    contract.setExternalId("contract-realpath");
    return contract;
  }

  private Step step() {
    Step template = new Step();
    template.setId("tmpl-realpath");
    Step step = new Step();
    step.setId("step-realpath");
    step.setStepTemplate(template);
    return step;
  }
}
