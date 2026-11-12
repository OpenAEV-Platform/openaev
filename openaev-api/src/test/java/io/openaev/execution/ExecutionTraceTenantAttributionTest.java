package io.openaev.execution;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.utils.fixtures.AgentFixture.createDefaultAgentService;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectTestStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectTestStatusRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every execution trace takes its tenant from the inject it reports on, whatever tenant the request
 * or job thread carries. No other source can pass for the right answer: the thread-local is set to
 * a tenant of its own throughout (an unset thread reads as the default tenant, so the default is
 * not a distinct value), and the transaction's tenant scope names three tenants with the owner in
 * the middle, so neither the whole scope nor its first or last entry can match the owner. No scope
 * ever includes the thread-local's tenant.
 *
 * <p>Each trace is read back by plain JDBC: the column is the subject, not the entity.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Execution traces: tenant taken from the inject they report on")
class ExecutionTraceTenantAttributionTest extends IntegrationTest {

  private static final String TWO_STATUSES_MESSAGE =
      "cross-tenant write: ExecutionTrace has an inject status and an inject test status of"
          + " different tenants";

  @Autowired private TenantRepository tenantRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private InjectTestStatusRepository injectTestStatusRepository;
  @Autowired private ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;
  @Autowired private InjectStatusService injectStatusService;
  @Autowired private ExecutionExecutorService executionExecutorService;
  @Autowired private ExecutorService executorService;

  private Tenant tenantB;
  private Tenant tenantC;
  private Tenant threadTenant;
  private Tenant bystander;
  private Inject injectOfB;
  private Inject injectOfC;
  private Endpoint endpointOfB;
  private Agent agentOfB;
  private Agent agentWithoutPlatform;

  @BeforeEach
  void setUp() {
    injectComposer.reset();
    injectStatusComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    tenantB = persistTenant("trace-owner-b");
    tenantC = persistTenant("trace-owner-c");
    threadTenant = persistTenant("trace-thread");
    bystander = persistTenant("trace-bystander");

    inScopeAround(tenantB);
    TenantContext.setCurrentTenant(tenantB.getId());
    injectOfB = persistInjectWithStatus();
    endpointOfB =
        endpointComposer
            .forEndpoint(createEndpoint())
            .withAgent(agentComposer.forAgent(createDefaultAgentService()))
            .persist()
            .get();
    agentOfB = endpointOfB.getAgents().getFirst();
    Endpoint unknownPlatform = createEndpoint();
    unknownPlatform.setPlatform(Endpoint.PLATFORM_TYPE.Unknown);
    agentWithoutPlatform =
        endpointComposer
            .forEndpoint(unknownPlatform)
            .withAgent(agentComposer.forAgent(createDefaultAgentService()))
            .persist()
            .get()
            .getAgents()
            .getFirst();

    inScopeAround(tenantC);
    TenantContext.setCurrentTenant(tenantC.getId());
    injectOfC = persistInjectWithStatus();
    entityManager.flush();

    assertThat(nativeTenant("injects", "inject_id", injectOfB.getId()))
        .isEqualTo(tenantB.getId())
        .isNotEqualTo(DEFAULT_TENANT_UUID)
        .isNotEqualTo(threadTenant.getId());
    assertThat(nativeTenant("injects", "inject_id", injectOfC.getId()))
        .isEqualTo(tenantC.getId())
        .isNotEqualTo(DEFAULT_TENANT_UUID)
        .isNotEqualTo(threadTenant.getId());

    TenantContext.setCurrentTenant(threadTenant.getId());
  }

  @AfterEach
  void clearThreadLocal() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("JDBC insert")
  class JdbcInsert {

    @Test
    @DisplayName("A trace reported through the callback takes its inject's tenant")
    void given_callbackOfInjectOfB_should_storeTraceInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      InjectExecutionInput input = new InjectExecutionInput();
      input.setMessage("reported by the injector");
      input.setStatus("INFO");
      input.setAction(InjectExecutionAction.command_execution);

      // Act
      ExecutionTrace trace = injectStatusService.updateInjectStatus(injectOfB, null, input, null);

      // Assert
      assertThat(tenantsOfTraces("execution_trace_id", trace.getId()))
          .containsExactly(tenantB.getId());
    }

    @Test
    @DisplayName("The helper stores the trace under its inject's tenant with the thread cleared")
    void given_clearedThread_should_storeTraceInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.getCurrentTenant()).isEqualTo(DEFAULT_TENANT_UUID);
      ExecutionTrace trace = traceOf(injectOfB.getStatus().orElseThrow(), "direct");

      // Act
      String id = executionTraceRepositoryHelper.saveExecutionTrace(trace);

      // Assert
      assertThat(tenantsOfTraces("execution_trace_id", id)).containsExactly(tenantB.getId());
    }
  }

  @Nested
  @DisplayName("JPA save")
  class JpaSave {

    @Test
    @DisplayName("The distribution trace takes its inject's tenant")
    void given_distribution_should_storeTraceInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus status = injectOfB.getStatus().orElseThrow();

      // Act
      executionExecutorService.saveDistributionTrace(Set.of(agentOfB), status);

      // Assert
      assertThat(tenantsOfStatusTraces(status)).containsExactly(tenantB.getId());
    }

    @Test
    @DisplayName("The traces of agentless assets take their inject's tenant")
    void given_agentlessAssets_should_storeTracesInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus status = injectOfB.getStatus().orElseThrow();

      // Act
      executionExecutorService.saveAgentlessAssetsTraces(Set.of(endpointOfB), status);

      // Assert
      assertThat(tenantsOfStatusTraces(status)).containsExactly(tenantB.getId());
    }

    @Test
    @DisplayName("The traces of agents without platform take their inject's tenant")
    void given_agentWithoutPlatform_should_storeTraceInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus status = injectOfB.getStatus().orElseThrow();

      // Act
      executorService.manageWithoutPlatformAgents(
          new ArrayList<>(List.of(agentWithoutPlatform)), status);

      // Assert
      assertThat(tenantsOfStatusTraces(status)).containsExactly(tenantB.getId());
    }
  }

  @Nested
  @DisplayName("Cascade from a status")
  class Cascade {

    @Test
    @DisplayName("The traces of an execution take the tenant of the inject status they move to")
    void given_executionOfInjectOfB_should_storeItsTracesInTenantB() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus status = injectOfB.getStatus().orElseThrow();

      // Act
      injectStatusService.saveAndStreamInject(
          injectStatusService.fromExecution(executionWithTraces(), status));

      // Assert
      assertThat(tenantsOfStatusTraces(status)).hasSize(2).containsOnly(tenantB.getId());
    }

    @Test
    @DisplayName("The traces of a test execution take the tenant of the inject test status")
    void given_testExecutionOfInjectOfC_should_storeItsTracesInTenantC() {
      // Arrange
      inScopeAround(tenantC);
      InjectTestStatus testStatus = InjectTestStatus.fromExecutionTest(executionWithTraces());
      testStatus.setInject(injectOfC);

      // Act
      InjectTestStatus saved = injectTestStatusRepository.save(testStatus);

      // Assert
      assertThat(tenantsOfTraces("execution_inject_test_status_id", saved.getId()))
          .hasSize(2)
          .containsOnly(tenantC.getId());
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("A JPA trace with no parent is refused and lands in no default tenant")
    void given_traceWithoutParent_should_refuseTheJpaWrite() {
      // Arrange
      inScopeAround(tenantB);
      ExecutionTrace orphan = ExecutionTrace.getNewInfoTrace("orphan", ExecutionTraceAction.START);

      // Act + Assert
      assertThatThrownBy(() -> saveAndFlush(orphan))
          .hasStackTraceContaining("unattributed tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName("A trace carrying another tenant than its inject's is refused")
    void given_traceWithForeignTenant_should_refuseTheWrite() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus status = injectOfB.getStatus().orElseThrow();
      ExecutionTrace foreign = traceOf(status, "foreign");
      ReflectionTestUtils.setField(foreign, "tenant", tenantC);
      status.addTrace(foreign);

      // Act + Assert
      assertThatThrownBy(() -> saveAndFlush(foreign))
          .hasStackTraceContaining("cross-tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName("A JDBC insert whose inject status has no inject is refused")
    void given_statusWithoutInject_should_refuseTheJdbcWrite() {
      // Arrange
      inScopeAround(tenantB);
      InjectStatus statusWithoutInject = new InjectStatus();
      statusWithoutInject.setId(UUID.randomUUID().toString());
      statusWithoutInject.setName(ExecutionStatus.PENDING);
      ExecutionTrace trace = traceOf(statusWithoutInject, "no inject");

      // Act + Assert
      assertThatThrownBy(() -> executionTraceRepositoryHelper.saveExecutionTrace(trace))
          .hasStackTraceContaining("unattributed tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName("A JDBC insert with no parent at all is refused")
    void given_traceWithoutParent_should_refuseTheJdbcWrite() {
      // Arrange
      inScopeAround(tenantB);
      ExecutionTrace orphan = ExecutionTrace.getNewInfoTrace("orphan", ExecutionTraceAction.START);

      // Act + Assert
      assertThatThrownBy(() -> executionTraceRepositoryHelper.saveExecutionTrace(orphan))
          .hasStackTraceContaining("unattributed tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName("A parentless JPA trace that already carries a tenant is refused")
    void given_parentlessTraceWithTenant_should_refuseTheJpaWrite() {
      // Arrange
      inScopeAround(tenantB);
      ExecutionTrace orphan = ExecutionTrace.getNewInfoTrace("orphan", ExecutionTraceAction.START);
      ReflectionTestUtils.setField(orphan, "tenant", tenantC);

      // Act + Assert
      assertThatThrownBy(() -> saveAndFlush(orphan))
          .hasStackTraceContaining("unattributed tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName("A parentless JDBC trace that already carries a tenant is refused")
    void given_parentlessTraceWithTenant_should_refuseTheJdbcWrite() {
      // Arrange
      inScopeAround(tenantB);
      ExecutionTrace orphan = ExecutionTrace.getNewInfoTrace("orphan", ExecutionTraceAction.START);
      ReflectionTestUtils.setField(orphan, "tenant", tenantC);

      // Act + Assert
      assertThatThrownBy(() -> executionTraceRepositoryHelper.saveExecutionTrace(orphan))
          .hasStackTraceContaining("unattributed tenant write: ExecutionTrace");
    }

    @Test
    @DisplayName(
        "A trace reported by an inject status of B and an inject test status of C is refused")
    void given_statusesOfTwoTenants_should_refuseTheWrite() {
      // Arrange
      inScopeAround(tenantB);
      InjectTestStatus testStatusOfC = new InjectTestStatus();
      testStatusOfC.setName(ExecutionStatus.PENDING);
      testStatusOfC.setInject(injectOfC);
      testStatusOfC = injectTestStatusRepository.save(testStatusOfC);
      entityManager.flush();
      ExecutionTrace trace = traceOf(injectOfB.getStatus().orElseThrow(), "two statuses");
      trace.setInjectTestStatus(testStatusOfC);

      // Act + Assert
      assertThatThrownBy(() -> saveAndFlush(trace)).hasStackTraceContaining(TWO_STATUSES_MESSAGE);
      assertThatThrownBy(() -> executionTraceRepositoryHelper.saveExecutionTrace(trace))
          .hasStackTraceContaining(TWO_STATUSES_MESSAGE);
    }
  }

  // -- helpers --

  /**
   * Narrows the test transaction to the other fixture tenant, the owner and the bystander, in that
   * order. The owner's rows stay readable, as in a request or a job scoped to several tenants, but
   * the scope never names the owner alone, first or last, and never the thread's tenant.
   */
  private void inScopeAround(Tenant owner) {
    Tenant other = owner == tenantB ? tenantC : tenantB;
    DefaultTenantScopeTestListener.setAmbientScope(
        entityManager,
        Stream.of(other, owner, bystander).map(Tenant::getId).collect(Collectors.joining(",")));
  }

  private Tenant persistTenant(String name) {
    return tenantRepository.save(
        TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  /** Built under the owner's thread tenant, as the fixture reads it at build time. */
  private Inject persistInjectWithStatus() {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectStatus(
            injectStatusComposer.forInjectStatus(InjectStatusFixture.createPendingInjectStatus()))
        .persist()
        .get();
  }

  private void saveAndFlush(ExecutionTrace trace) {
    executionTraceRepository.save(trace);
    entityManager.flush();
  }

  private static ExecutionTrace traceOf(InjectStatus status, String message) {
    return new ExecutionTrace(
        status,
        ExecutionTraceStatus.INFO,
        List.of(),
        message,
        ExecutionTraceAction.START,
        null,
        null);
  }

  private static Execution executionWithTraces() {
    Execution execution = new Execution(false);
    execution.addTrace(
        ExecutionTrace.getNewInfoTrace("first from the execution", ExecutionTraceAction.EXECUTION));
    execution.addTrace(
        ExecutionTrace.getNewSuccessTrace(
            "second from the execution", ExecutionTraceAction.EXECUTION));
    return execution;
  }

  private List<String> tenantsOfStatusTraces(InjectStatus status) {
    return tenantsOfTraces("execution_inject_status_id", status.getId());
  }

  /** Flushes the persistence context, then reads {@code tenant_id} on the raw connection. */
  private List<String> tenantsOfTraces(String column, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              List<String> tenants = new ArrayList<>();
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM execution_traces WHERE " + column + " = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                  while (resultSet.next()) {
                    tenants.add(resultSet.getString(1));
                  }
                }
              }
              return tenants;
            });
  }

  private String nativeTenant(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                  assertThat(resultSet.next()).as("%s row %s", table, id).isTrue();
                  return resultSet.getString(1);
                }
              }
            });
  }
}
