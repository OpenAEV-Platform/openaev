package io.openaev.executors.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.execution.ExecutionExecutorException;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.AgentException;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.fixtures.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ExecutionExecutorServiceTest {

  @Mock private InjectService injectService;
  @Mock private ExecutionTraceRepository executionTraceRepository;
  @Mock private ExecutorContextService executorContextService;
  @Mock private ManagerFactory managerFactory;
  @Mock private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  @Mock private ConnectorInstanceRepository connectorInstanceRepository;

  @InjectMocks private ExecutionExecutorService executorService;

  @BeforeEach
  void setUp() {
    ConnectorInstanceService connectorInstanceService =
        new ConnectorInstanceService(
            null,
            null,
            connectorInstanceRepository,
            connectorInstanceConfigurationRepository,
            null,
            null,
            null,
            null,
            null,
            null);
    ReflectionTestUtils.setField(
        executorService, "connectorInstanceService", connectorInstanceService);
    ReflectionTestUtils.setField(executorService, "executorUtils", new ExecutorUtils());
  }

  @Test
  void test_launchExecutorContext_noAssetException() throws Exception {

    // Init datas
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    injector.setExecutorCommands(executorCommands);
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("0123456789");
    Inject inject =
        InjectFixture.createTechnicalInject(
            InjectorContractFixture.createPayloadInjectorContractWithDefaultDomain(
                injector, payloadCommand),
            "Inject",
            endpoint);
    inject.setStatus(InjectStatusFixture.createPendingInjectStatus());
    when(injectService.getAgentsAndAgentlessAssetsByInject(inject))
        .thenReturn(
            new AgentsAndAssetsAgentless(new HashSet<>(), new HashSet<>(List.of(endpoint))));
    // Run method to test
    assertThrows(
        ExecutionExecutorException.class,
        () -> {
          executorService.launchExecutorContext(inject);
        });
  }

  @Test
  void test_saveAgentlessAssetsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setId("0123456789");
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveAgentlessAssetsTraces(new HashSet<>(Set.of(endpoint)), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(
        ExecutionTraceStatus.ASSET_AGENTLESS, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Asset " + endpoint.getName() + " has no agent, unable to launch the inject",
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveAgentlessAssetsTraces_withoutAgents() {
    // Init datas
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveAgentlessAssetsTraces(new HashSet<>(), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveInactiveAgentsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    agent.setLastSeen(Instant.now().minus(5, ChronoUnit.DAYS));
    endpoint.setAgents(List.of(agent));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveInactiveAgentsTraces(new HashSet<>(Set.of(agent)), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(
        ExecutionTraceStatus.AGENT_INACTIVE, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Agent "
            + agent.getExecutedByUser()
            + " is inactive for the asset "
            + agent.getAsset().getName(),
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveInactiveAgentsTraces_withoutAgents() {
    // Init datas
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveInactiveAgentsTraces(new HashSet<>(), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveWithoutExecutorAgentsTraces_withAgents() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    agent.setExecutor(null);
    endpoint.setAgents(List.of(agent));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveWithoutExecutorAgentsTraces(new HashSet<>(Set.of(agent)), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals(
        "Cannot find the executor for the agent "
            + agent.getExecutedByUser()
            + " from the asset "
            + agent.getAsset().getName(),
        executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void test_saveWithoutExecutorAgentsTraces_withoutAgents() {
    // Init datas
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveWithoutExecutorAgentsTraces(new HashSet<>(), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveCrowdstrikeSentineloneAgentsErrorTraces() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    endpoint.setAgents(List.of(agent));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveAgentsErrorTraces(
        new Exception("EXCEPTION !!"), new HashSet<>(Set.of(agent)), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository).saveAll(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getFirst().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getFirst().getAction());
    assertEquals("EXCEPTION !!", executionTrace.getValue().getFirst().getMessage());
  }

  @Test
  void saveAgentErrorTrace() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    endpoint.setAgents(List.of(agent));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveAgentErrorTrace(new AgentException("EXCEPTION !!", agent), injectStatus);
    // Asserts
    ArgumentCaptor<ExecutionTrace> executionTrace = ArgumentCaptor.forClass(ExecutionTrace.class);
    verify(executionTraceRepository).save(executionTrace.capture());
    assertEquals(ExecutionTraceStatus.ERROR, executionTrace.getValue().getStatus());
    assertEquals(ExecutionTraceAction.COMPLETE, executionTrace.getValue().getAction());
    assertEquals("EXCEPTION !!", executionTrace.getValue().getMessage());
  }

  @Nested
  @DisplayName("launchExecutorContext with active agents")
  class LaunchExecutorContextWithAgentsTests {

    private Inject createInjectWithActiveAgent(Executor executor) throws JsonProcessingException {
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId("endpoint-" + UUID.randomUUID());
      Agent agent = AgentFixture.createDefaultAgentSession(executor);
      agent.setAsset(endpoint);
      agent.setLastSeen(Instant.now());
      endpoint.setAgents(List.of(agent));

      Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
      Injector injector = InjectorFixture.createDefaultPayloadInjector();
      Map<String, String> executorCommands = new HashMap<>();
      executorCommands.put(
          Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
      injector.setExecutorCommands(executorCommands);
      Inject inject =
          InjectFixture.createTechnicalInject(
              InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand),
              "Inject",
              endpoint);
      inject.setStatus(InjectStatusFixture.createPendingInjectStatus());

      when(injectService.getAgentsAndAgentlessAssetsByInject(inject))
          .thenReturn(new AgentsAndAssetsAgentless(new HashSet<>(Set.of(agent)), new HashSet<>()));
      return inject;
    }

    private void stubFindByExecutorId(String executorId, ConnectorInstancePersisted instance) {
      ConnectorInstanceConfigurationRepository.ConnectorIdsFomDatabase ids =
          mock(ConnectorInstanceConfigurationRepository.ConnectorIdsFomDatabase.class);
      when(ids.getConnectorInstanceId()).thenReturn(instance.getId());
      when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValue(
              "EXECUTOR_ID", executorId))
          .thenReturn(ids);
      when(connectorInstanceRepository.findById(instance.getId()))
          .thenReturn(Optional.of(instance));
    }

    @Test
    @DisplayName(
        "Given active agent with executor and connector instance, should route via requestForInstance")
    void given_activeAgentWithExecutor_should_routeViaRequestForInstance() throws Exception {
      // Arrange
      Executor executor = new Executor();
      executor.setId("executor-1");
      executor.setName("CrowdStrike");
      executor.setType("openaev_crowdstrike");
      executor.setExternal(true);

      Inject inject = createInjectWithActiveAgent(executor);

      Manager manager = mock(Manager.class);
      when(managerFactory.getManager()).thenReturn(manager);

      ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
      connectorInstance.setId("instance-1");
      stubFindByExecutorId(executor.getId(), connectorInstance);

      ExecutorContextService mockContextService = mock(ExecutorContextService.class);
      when(manager.requestForInstance(eq(connectorInstance), eq(ExecutorContextService.class)))
          .thenReturn(mockContextService);
      when(mockContextService.launchBatchExecutorSubprocess(any(), any(), any()))
          .thenReturn(List.of());

      // Act
      executorService.launchExecutorContext(inject);

      // Assert
      verify(connectorInstanceConfigurationRepository)
          .findInstanceAndCatalogIdsByKeyValue("EXECUTOR_ID", executor.getId());
      verify(manager).requestForInstance(eq(connectorInstance), eq(ExecutorContextService.class));
      verify(mockContextService).launchBatchExecutorSubprocess(eq(inject), any(), any());
      verify(mockContextService).launchExecutorSubprocess(eq(inject), any(Endpoint.class), any());
    }

    @Test
    @DisplayName(
        "Given executor context service throws exception, should save error traces for all agents")
    void given_executorContextServiceThrows_should_saveErrorTraces() throws Exception {
      // Arrange
      Executor executor = new Executor();
      executor.setId("executor-fail");
      executor.setName("FailingExecutor");
      executor.setType("openaev_failing");
      executor.setExternal(true);

      Inject inject = createInjectWithActiveAgent(executor);

      Manager manager = mock(Manager.class);
      when(managerFactory.getManager()).thenReturn(manager);

      ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
      connectorInstance.setId("instance-fail");
      stubFindByExecutorId(executor.getId(), connectorInstance);

      ExecutorContextService mockContextService = mock(ExecutorContextService.class);
      when(manager.requestForInstance(eq(connectorInstance), eq(ExecutorContextService.class)))
          .thenReturn(mockContextService);
      when(mockContextService.launchBatchExecutorSubprocess(any(), any(), any()))
          .thenThrow(new RuntimeException("CrowdStrike API timeout"));

      // Act & Assert
      assertThatThrownBy(() -> executorService.launchExecutorContext(inject))
          .isInstanceOf(ExecutionExecutorException.class)
          .hasMessageContaining("No asset executed");

      // Assert
      ArgumentCaptor<List<ExecutionTrace>> captor = ArgumentCaptor.forClass(List.class);
      verify(executionTraceRepository).saveAll(captor.capture());
      List<ExecutionTrace> errorTraces = captor.getValue();
      assertThat(errorTraces).hasSize(1);
      assertThat(errorTraces.getFirst().getStatus()).isEqualTo(ExecutionTraceStatus.ERROR);
      assertThat(errorTraces.getFirst().getMessage()).isEqualTo("CrowdStrike API timeout");
    }

    @Test
    @DisplayName(
        "Given agents on two different executors, should dispatch to each executor separately")
    void given_agentsOnTwoExecutors_should_dispatchToEachSeparately() throws Exception {
      // Arrange
      Executor executor1 = new Executor();
      executor1.setId("executor-cs-1");
      executor1.setName("CrowdStrike 1");
      executor1.setType("openaev_crowdstrike");
      executor1.setExternal(true);

      Executor executor2 = new Executor();
      executor2.setId("executor-cs-2");
      executor2.setName("CrowdStrike 2");
      executor2.setType("openaev_crowdstrike");
      executor2.setExternal(true);

      Endpoint endpoint1 = EndpointFixture.createEndpoint();
      endpoint1.setId("endpoint-1");
      Agent agent1 = AgentFixture.createDefaultAgentSession(executor1);
      agent1.setAsset(endpoint1);
      agent1.setLastSeen(Instant.now());

      Endpoint endpoint2 = EndpointFixture.createEndpoint();
      endpoint2.setId("endpoint-2");
      Agent agent2 = AgentFixture.createDefaultAgentSession(executor2);
      agent2.setAsset(endpoint2);
      agent2.setLastSeen(Instant.now());

      Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
      Injector injector = InjectorFixture.createDefaultPayloadInjector();
      Inject inject =
          InjectFixture.createTechnicalInject(
              InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand),
              "Inject",
              endpoint1);
      inject.setStatus(InjectStatusFixture.createPendingInjectStatus());

      when(injectService.getAgentsAndAgentlessAssetsByInject(inject))
          .thenReturn(
              new AgentsAndAssetsAgentless(new HashSet<>(Set.of(agent1, agent2)), new HashSet<>()));

      Manager manager = mock(Manager.class);
      when(managerFactory.getManager()).thenReturn(manager);

      ConnectorInstancePersisted instance1 = new ConnectorInstancePersisted();
      instance1.setId("instance-cs-1");
      ConnectorInstancePersisted instance2 = new ConnectorInstancePersisted();
      instance2.setId("instance-cs-2");

      stubFindByExecutorId("executor-cs-1", instance1);
      stubFindByExecutorId("executor-cs-2", instance2);

      ExecutorContextService mockCtx1 = mock(ExecutorContextService.class);
      ExecutorContextService mockCtx2 = mock(ExecutorContextService.class);

      when(manager.requestForInstance(eq(instance1), eq(ExecutorContextService.class)))
          .thenReturn(mockCtx1);
      when(manager.requestForInstance(eq(instance2), eq(ExecutorContextService.class)))
          .thenReturn(mockCtx2);
      when(mockCtx1.launchBatchExecutorSubprocess(any(), any(), any())).thenReturn(List.of());
      when(mockCtx2.launchBatchExecutorSubprocess(any(), any(), any())).thenReturn(List.of());

      // Act
      executorService.launchExecutorContext(inject);

      // Assert
      verify(manager).requestForInstance(eq(instance1), eq(ExecutorContextService.class));
      verify(manager).requestForInstance(eq(instance2), eq(ExecutorContextService.class));
      verify(mockCtx1).launchBatchExecutorSubprocess(eq(inject), any(), any());
      verify(mockCtx2).launchBatchExecutorSubprocess(eq(inject), any(), any());
      verify(mockCtx1).launchExecutorSubprocess(eq(inject), any(Endpoint.class), eq(agent1));
      verify(mockCtx2).launchExecutorSubprocess(eq(inject), any(Endpoint.class), eq(agent2));
    }
  }
}
