package io.openaev.api.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.executors.Executor;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService;
import io.openaev.service.chaining.ConditionService;
import io.openaev.utils.ConditionUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * End-to-end wiring contract: driving the real {@code InjectExecutionStep.run(step)} drives the
 * attack-path ingestion, closing the inference gap that no test crossed the whole chain (step run
 * -> {@code recordAttackPathExecution} -> {@code onRun} -> persisted rows). The onRun level itself
 * is covered by {@link
 * io.openaev.service.attackpath.ingestion.AttackPathIngestionTenantAttributionTest}; this adds only
 * the run-level gates that live in {@code InjectExecutionStep}: the {@code ATTACK_PATH} flag check
 * and the non-fatal try/catch.
 *
 * <p>Reproduces production: the run executes under the step's resolved tenant (the chaining worker
 * sets it from the step before calling {@code run}, and {@code getInjectFromDataStep} reads the
 * tenant-active {@code injector_contract} under it), the only mocked agent-facing brick is {@code
 * Executor}, and the rows are read back through raw JDBC. {@code run} is {@code @Transactional};
 * the ingestion's {@code onRun} re-scopes to the inject's tenant in a REQUIRES_NEW transaction that
 * commits independently, so the test is deliberately NOT {@code @Transactional} (a rolled-back test
 * transaction would hide that independent commit). The "no ambient scope" resilience is an onRun
 * property, covered at that level by {@code AttackPathIngestionTenantAttributionTest}. {@code
 * injectService} is a spy so its real {@code getAgentsAndAgentlessAssetsByInject} resolution stays
 * live while {@code createInject} is stubbed to hand the run a fully-built fixture inject.
 */
// @WithMockUser provides the user the fixture setup needs (creating the tenant + persisting the
// endpoint/contract). The run then executes under that tenant scope, as the worker does.
@io.openaev.utils.mockUser.WithMockUser(isAdmin = true)
@DisplayName("attack path: a chaining run drives the ingestion (run-level wiring)")
class AttackPathRunWiringIT extends IntegrationTest {

  // Spy, not mock: tenant provisioning (createTenantWithCurrentUser) uses the real injectorContract
  // resolution; a full mock returns null and NPEs on getPayload() during provisioning.
  @MockitoSpyBean private InjectorContractService injectorContractService;
  @MockitoBean private UserService userService;
  @MockitoBean private ConditionService conditionService;
  @MockitoBean private ConditionUtils conditionUtils;
  @MockitoBean private Executor executor;

  // Spies, not full mocks: the real resolution (injectService) and the real ingestion
  // (attackPathIngestion) must run; only createInject / the throw-for-W3 are stubbed.
  @MockitoSpyBean private InjectService injectService;
  @MockitoSpyBean private AttackPathExecutionIngestionService attackPathIngestion;

  @Autowired private InjectExecutionStep injectExecutionStep;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private CacheManager cacheManager;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private PayloadComposer payloadComposer;

  private JdbcTemplate jdbc;
  private Tenant tenant;
  private Endpoint endpoint;
  private Agent agent;
  private Inject fixtureInject;
  private Payload commandPayload;
  private String injectInputJson;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("ap-run-wiring");
    // Keep TenantContext on the new tenant (worker behaviour): run() reads the tenant-active
    // contract under it. See the class javadoc.
    TenantContext.setCurrentTenant(tenant.getId());

    // A committed injector + contract so the create/ready pipeline resolves the step data to a
    // valid inject before createInject swaps in the fixture. Separate from the transient contract
    // the fixture inject carries (which drives the resolution under test).
    Injector resolvableInjector =
        injectorRepository.save(InjectorFixture.createDefaultPayloadInjector());
    InjectorContract resolvableContract = InjectorContractFixture.createImplantInjectorContract();
    resolvableContract.addInjector(resolvableInjector);
    resolvableContract = injectorContractRepository.save(resolvableContract);
    resolvableInjector.linkContract(resolvableContract);
    injectorRepository.save(resolvableInjector);

    // Persist the agent-backed endpoint under the tenant (as the ingestion tenant test does): the
    // fixture must be committed so the run's read-only resolution sees it.
    agent = AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor());
    endpoint =
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agentComposer.forAgent(agent))
            .persist()
            .get();

    // A committed Command payload for the fixture contract: prepareGetStatusPayloadFromInject
    // re-fetches it by id in run(); the COMMAND branch needs a present payload (empty -> no rows).
    commandPayload =
        payloadComposer.forPayload(PayloadFixture.createDefaultCommand()).persist().get();

    // InjectInput (DTO) form, ids as strings. The create/ready pipeline turns this into the real
    // step data (contract as an object) that run()'s getInjectFromDataStep deserializes.
    injectInputJson =
        String.format(
            """
            {
              "type": "inject",
              "inject_title": "crackmapexec",
              "inject_description": "",
              "inject_injector_contract": "%s",
              "inject_injector": "%s",
              "inject_content": { "obfuscator": "plain-text" },
              "inject_depends_on": [],
              "inject_depends_duration": 0,
              "inject_teams": [],
              "inject_assets": [ "%s" ],
              "inject_asset_groups": [],
              "inject_documents": [],
              "inject_all_teams": false,
              "inject_country": null,
              "inject_city": null,
              "inject_tags": [],
              "inject_enabled": true
            }
            """,
            resolvableContract.getId(), resolvableInjector.getId(), endpoint.getId());

    fixtureInject = buildFixtureInject();
    // Spy stub: createInject hands run() the fully-built fixture inject; the real
    // getAgentsAndAgentlessAssetsByInject resolution (the behaviour under test) stays live.
    doReturn(fixtureInject).when(injectService).createInject(any());
    doReturn(false).when(injectService).canApplyTargetType(any(), any());
    doReturn(new User()).when(userService).currentUser();
    doReturn(new InjectStatus()).when(executor).directExecute(any());
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
        "SIM-WIRING");
    // The tenant-active FKs are ON DELETE CASCADE (e.g. injectors_contracts.tenant_id -> tenants,
    // migration V4_88), so deleting the tenant removes the committed contract, injector, payload,
    // agent and endpoint in one shot.
    tenantHelper.deleteCommittedTenants(tenant.getId());
    setAttackPathFeature(false);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "W1 — flag ON: run() drives the ingestion and the row lands under the inject's tenant")
  void wiringFiresAndRowLandsUnderTheInjectTenant() throws Exception {
    setAttackPathFeature(true);

    injectExecutionStep.run(readyStep());

    String rowId =
        AttackPathIds.executionNode(fixtureInject.getId(), endpoint.getId(), agent.getId());
    assertThat(rawTenantOf(rowId))
        .as("run() must have driven recordAttackPathExecution -> onRun -> persist")
        .isEqualTo(tenant.getId());
    verify(executor).directExecute(any());
  }

  @Test
  @DisplayName("W2 — flag OFF: run() executes, the executor is called, onRun never runs, zero rows")
  void flagOffIsAStrictNoOp() throws Exception {
    setAttackPathFeature(false);

    injectExecutionStep.run(readyStep());

    verify(attackPathIngestion, never()).onRun(any(), any(), any());
    verify(executor).directExecute(any());
    assertThat(rawRowCount()).as("flag OFF must write no attack-path rows").isZero();
  }

  @Test
  @DisplayName("W3 — a throwing ingestion is swallowed: run() still returns and the executor runs")
  void ingestionFailureIsNonFatal() throws Exception {
    setAttackPathFeature(true);
    doThrow(new RuntimeException("boom")).when(attackPathIngestion).onRun(any(), any(), any());

    assertThat(injectExecutionStep.run(readyStep())).isPresent();
    verify(executor).directExecute(any());
    assertThat(rawRowCount())
        .as("a failed ingestion writes nothing but does not fail the run")
        .isZero();
  }

  // Handed to run() in memory (via the createInject stub) with contract + exercise attached. The
  // resolution then runs on the executor-backed contract; the agent's own endpoint is source and
  // target. Same shape as the onRun tenant test's fixture.
  private Inject buildFixtureInject() {
    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");
    injector.setType("openaev_implant");
    Exercise exercise = new Exercise();
    exercise.setId("SIM-WIRING");
    Inject inject = new Inject();
    inject.setId("inj-run-wiring");
    inject.setTitle("crackmapexec");
    inject.setExercise(exercise);
    inject.setInjector(injector);
    inject.setInjectorContract(contract());
    inject.setAssets(java.util.List.of(endpoint));
    inject.setTenant(tenant);
    return inject;
  }

  // Executor-backed contract carrying the committed Command payload. It must be committed, not
  // transient: prepareGetStatusPayloadFromInject re-fetches it by id in run(). No endpoint-typed
  // argument, so the row id is the agent's own endpoint (executionNode(injectId, endpoint, agent)).
  private InjectorContract contract() {
    InjectorContract contract = new InjectorContract();
    contract.setNeedsExecutor(true);
    contract.setPayload(commandPayload);
    contract.setExternalId("contract-run-wiring");
    return contract;
  }

  // Builds the READY step through the real create -> ready pipeline (like InjectExecutionStepTest),
  // so its data is production step data (contract as an object, simulation attached) that run()'s
  // getInjectFromDataStep expects. Hand-writing it is brittle; the pipeline is the source of truth.
  // createInject then swaps the deserialized inject for the fixture.
  private Step readyStep() throws Exception {
    Workflow workflowTemplate = WorkflowFixture.getDefaultWorkflowTemplate();
    workflowTemplate.setSimulation(ExerciseFixture.createDefaultExercise());
    InjectInput injectInput = new ObjectMapper().readValue(injectInputJson, InjectInput.class);
    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    Step stepTemplate = injectExecutionStep.create(stepInput, workflowTemplate).orElseThrow();
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    return injectExecutionStep
        .ready(stepTemplate, "{\"input\":\"do defined\"}", workflowRun)
        .orElseThrow();
  }

  private void setAttackPathFeature(boolean enabled) {
    openAEVConfig.setEnabledDevFeatures(enabled ? PreviewFeature.ATTACK_PATH.getValue() : null);
    // isFeatureEnabled is @Cacheable("global"); evict or the toggle is not observed.
    if (cacheManager.getCache("global") != null) {
      cacheManager.getCache("global").clear();
    }
  }

  private String rawTenantOf(String rowId) {
    return jdbc.query(
        "SELECT tenant_id FROM attackpath_execution WHERE attackpath_execution_id = ?",
        rs -> rs.next() ? rs.getString(1) : null,
        rowId);
  }

  private Integer rawRowCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
        Integer.class,
        "SIM-WIRING");
  }
}
