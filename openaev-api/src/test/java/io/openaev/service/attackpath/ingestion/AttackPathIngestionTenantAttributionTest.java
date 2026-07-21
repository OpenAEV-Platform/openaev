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
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path Phase A: the create attributes rows to the inject's tenant")
class AttackPathIngestionTenantAttributionTest extends IntegrationTest {

  private static final String SIM = "SIM-REALPATH";
  private static final String INJECT_ID = "inj-realpath";
  private static final String ENDPOINT_ID = "ep-realpath";
  private static final String AGENT_ID = "agt-realpath";

  @Autowired private AttackPathExecutionIngestionService ingestionService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;

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
    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject(), contract()));

    String rowId = AttackPathIds.executionNode(INJECT_ID, ENDPOINT_ID, AGENT_ID);
    assertThat(rawTenantOf(rowId))
        .as("the run's row must exist and carry the inject's tenant, with no ambient scope set")
        .isEqualTo(tenant.getId());
  }

  @Test
  @DisplayName("the row is readable under its own tenant's scope and invisible under another's")
  void theRowIsVisibleOnlyUnderItsOwnTenantScope() throws Exception {
    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject(), contract()));
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
  @DisplayName("only the ingestion writer emits a native statement on attackpath_execution")
  void nativeWritersOnTheTableAreReviewed() throws Exception {
    // Moving the write from saveAll to createNativeQuery took it out of the repository, so
    // TenantActiveTableAccessArchTest's repository rule no longer watches this path: its allowlist
    // entry for this class became dead. A native writer still passes through the inspector, but
    // nothing structurally forces a new one to be reviewed. This scan restores that: any production
    // class issuing createNativeQuery against an attack path table must be on this short list, or
    // the build fails and the review happens.
    List<Path> nativeWriters;
    try (Stream<Path> tree = Files.walk(Path.of("src/main/java"))) {
      nativeWriters =
          tree.filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    String body = readSource(p);
                    return body.contains("createNativeQuery")
                        && (body.contains("attackpath_execution")
                            || body.contains("attackpath_finding"));
                  })
              .toList();
    }
    assertThat(nativeWriters)
        .as("a new native writer on the attack path tables must be reviewed, not appear unseen")
        .singleElement()
        .satisfies(
            p ->
                assertThat(p.getFileName())
                    .hasToString("AttackPathExecutionIngestionService.java"));
  }

  private static String readSource(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception e) {
      throw new IllegalStateException("cannot read " + path, e);
    }
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
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService())));
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
    inject.setAssetGroups(java.util.List.of(group)); // group only, no direct assets
    inject.setTenant(tenant);

    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject, contract()));

    assertThat(rawRowCountForInject(INJECT_ID))
        .as("an inject targeting only an asset group must still record its member endpoints")
        .isPositive();
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
            .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
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
          inject.setAssets(java.util.List.of(proxy));
          inject.setTenant(tenant);
          ingestionService.onRun(step(), inject, contract());
        });

    assertThat(rawRowCountForInject(INJECT_ID))
        .as("a proxied endpoint must be recognised and recorded, not dropped by a bare instanceof")
        .isPositive();
  }

  @Test
  @DisplayName("a re-run never overwrites what the later phases wrote on the row")
  void reRunIsCreateOnce() {
    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject(), contract()));
    String rowId = AttackPathIds.executionNode(INJECT_ID, ENDPOINT_ID, AGENT_ID);

    // Phase B (#204/#202) fills its own columns on the row this create left behind. The create must
    // never touch them again: the deterministic id means a retried or replayed run lands on exactly
    // this row, and an overwrite would silently erase someone else's work.
    jdbc.update(
        "UPDATE attackpath_execution SET attackpath_execution_command = ?"
            + " WHERE attackpath_execution_id = ?",
        "whoami /priv",
        rowId);

    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject(), contract()));

    assertThat(rawRow(rowId))
        .as("the second run must leave the Phase B column untouched")
        .containsEntry("attackpath_execution_command", "whoami /priv");
  }

  @Test
  @DisplayName("a failing create cannot roll back the run that triggered it")
  void aFailingCreateDoesNotPoisonTheRunTransaction() {
    Inject doomed = inject();
    // A tenant that does not exist: the create fails inside its own boundary, whatever the reason.
    doomed.setTenant(new Tenant("no-such-tenant"));
    String marker = "ap-poison-marker";

    // Before the conversion the create joined the run's transaction, so its failure marked that
    // transaction rollback-only and the caller's catch merely delayed the death to commit time
    // (UnexpectedRollbackException). With REQUIRES_NEW the failure stays inside its own boundary.
    runAsTheExecutorWould(
        () -> {
          // Asserted, not assumed: if the doomed create silently succeeded, the catch below would
          // never fire and this test would prove nothing at all.
          assertThatThrownBy(() -> ingestionService.onRun(step(), doomed, contract()))
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
    runAsTheExecutorWould(() -> ingestionService.onRun(step(), inject(), contract()));

    // Read back from the database rather than through the ORM: a scoped read would be fail-closed
    // here, and the point is what actually landed on disk.
    Map<String, Object> row = rawRow(AttackPathIds.executionNode(INJECT_ID, ENDPOINT_ID, AGENT_ID));
    assertThat(row)
        .containsEntry("attackpath_execution_simulation_id", SIM)
        .containsEntry("attackpath_execution_inject_id", INJECT_ID)
        .containsEntry("attackpath_execution_contract_external_id", "contract-realpath")
        .containsEntry("attackpath_execution_payload_id", "cmd-realpath")
        .containsEntry("attackpath_execution_payload_name", "crackmapexec")
        .containsEntry("attackpath_execution_injector_type", "openaev_implant")
        .containsEntry("attackpath_execution_step_template_id", "tmpl-realpath")
        // Local command on an asset: source is the agent's endpoint, target is that endpoint.
        .containsEntry("attackpath_execution_source_kind", "AGENT_ASSET")
        .containsEntry("attackpath_execution_source_asset_id", ENDPOINT_ID)
        .containsEntry("attackpath_execution_source_hostname", "corp-dc")
        .containsEntry("attackpath_execution_source_ip", "10.0.0.5")
        .containsEntry("attackpath_execution_source_platform", "Windows")
        .containsEntry("attackpath_execution_agent_id", AGENT_ID)
        .containsEntry("attackpath_execution_agent_privilege", "admin")
        .containsEntry("attackpath_execution_target_kind", "ASSET")
        .containsEntry("attackpath_execution_target_key", ENDPOINT_ID);
  }

  @Test
  @DisplayName("an inject with no simulation is out of the attack path and records nothing")
  void createRecordsNothingWithoutASimulation() {
    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setTenant(tenant);
    // no exercise set

    runAsTheExecutorWould(() -> ingestionService.onRun(new Step(), inject, new InjectorContract()));

    assertThat(rawRowCountForInject(INJECT_ID)).isZero();
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

  private Integer rawRowCountForInject(String injectId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution WHERE attackpath_execution_inject_id = ?",
        Integer.class,
        injectId);
  }

  // In-memory inject graph, as the engine hands it over. Only the EXECUTION rows are persisted.
  private Inject inject() {
    Agent agent = new Agent();
    agent.setId(AGENT_ID);
    agent.setPrivilege(Agent.PRIVILEGE.admin);

    Endpoint endpoint = new Endpoint();
    endpoint.setId(ENDPOINT_ID);
    endpoint.setHostname("corp-dc");
    endpoint.setIps(new String[] {"10.0.0.5"});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    endpoint.setAgents(List.of(agent));

    Injector injector = new Injector();
    injector.setName("OpenAEV Implant");
    injector.setType("openaev_implant");

    Exercise exercise = new Exercise();
    exercise.setId(SIM);

    Inject inject = new Inject();
    inject.setId(INJECT_ID);
    inject.setExercise(exercise);
    inject.setInjector(injector);
    inject.setAssets(List.of(endpoint));
    // Inject implements TenantBase with a non-null tenant, so the write has an attribution
    // available without any ambient state. That is the whole point.
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
