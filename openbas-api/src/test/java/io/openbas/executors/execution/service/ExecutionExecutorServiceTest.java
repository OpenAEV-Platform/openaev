package io.openbas.executors.execution.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.openbas.database.model.*;
import io.openbas.database.repository.ExecutionTraceRepository;
import io.openbas.execution.ExecutionExecutorException;
import io.openbas.execution.ExecutionExecutorService;
import io.openbas.executors.ExecutorContextService;
import io.openbas.rest.exception.AgentException;
import io.openbas.rest.inject.output.AgentsAndAssetsAgentless;
import io.openbas.rest.inject.service.InjectService;
import io.openbas.utils.fixtures.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExecutionExecutorServiceTest {

  @Mock private InjectService injectService;
  @Mock private ExecutionTraceRepository executionTraceRepository;
  @Mock private ExecutorContextService executorContextService;

  @InjectMocks private ExecutionExecutorService executorService;

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
            InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand),
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
    executorService.saveAgentlessAssetsTraces(Set.of(endpoint), injectStatus);
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
    executorService.saveAgentlessAssetsTraces(Set.of(), injectStatus);
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
    executorService.saveInactiveAgentsTraces(Set.of(agent), injectStatus);
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
    executorService.saveInactiveAgentsTraces(Set.of(), injectStatus);
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
    executorService.saveWithoutExecutorAgentsTraces(Set.of(agent), injectStatus);
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
    executorService.saveWithoutExecutorAgentsTraces(Set.of(), injectStatus);
    // Asserts
    ArgumentCaptor<List<ExecutionTrace>> executionTrace = ArgumentCaptor.forClass(List.class);
    verify(executionTraceRepository, never()).saveAll(executionTrace.capture());
  }

  @Test
  void test_saveCrowdstrikeAgentsErrorTraces() {
    // Init datas
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createDefaultAgentSession();
    agent.setAsset(endpoint);
    endpoint.setAgents(List.of(agent));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    // Run method to test
    executorService.saveCrowdstrikeAgentsErrorTraces(
        new Exception("EXCEPTION !!"), Set.of(agent), injectStatus);
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
}
