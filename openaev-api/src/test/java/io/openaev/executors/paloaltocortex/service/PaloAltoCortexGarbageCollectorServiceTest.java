package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.POWERSHELL_CMD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Executor;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.service.AgentService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaloAltoCortexGarbageCollectorServiceTest {

  private static final String EXECUTOR_ID = "test-executor-id";

  @Mock private AgentService agentService;
  @Mock private PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;
  @Mock private PaloAltoCortexExecutorConfig config;
  @Mock private Executor executor;
  @Mock private TenantScopedTransaction tenantTx;

  private PaloAltoCortexGarbageCollectorService paloAltoCortexGarbageCollectorService;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.when(executor.getId()).thenReturn(EXECUTOR_ID);
    // run() wraps doRun() in tenantTx.execute(TxCtx.forTenant(executor.getTenantId()), ...):
    // stub getTenantId() (TxCtx.forTenant rejects null via List.of) and make the tenantTx mock
    // actually invoke the supplied work, otherwise doRun() never happens.
    lenient().when(executor.getTenantId()).thenReturn("test-tenant-id");
    lenient()
        .when(tenantTx.execute(any(), any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<?> work = invocation.getArgument(1);
              return work.get();
            });
    paloAltoCortexGarbageCollectorService =
        new PaloAltoCortexGarbageCollectorService(
            config, paloAltoCortexExecutorContextService, agentService, executor, tenantTx);
  }

  @Test
  void test_run_garbageCollector_withPaloAltoCortexAgents() {
    // Init datas
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setExternalReference("agent_external_reference");
    agent.setAsset(EndpointFixture.createEndpoint());
    when(agentService.getAgentsByExecutorId(EXECUTOR_ID)).thenReturn(List.of(agent));
    when(config.getWindowsScriptUid()).thenReturn("test script");
    // Run method to test
    paloAltoCortexGarbageCollectorService.run();
    // Asserts
    ArgumentCaptor<List<PaloAltoCortexAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(paloAltoCortexExecutorContextService).executeActions(actionsCaptor.capture());
    assertEquals(1, actionsCaptor.getValue().size());
    PaloAltoCortexAction action = actionsCaptor.getValue().get(0);
    assertEquals("test script", action.getScriptId());
    assertEquals(
        POWERSHELL_CMD
            + "RwBlAHQALQBDAGgAaQBsAGQASQB0AGUAbQAgAC0AUABhAHQAaAAgACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXABwAGEAeQBsAG8AYQBkAHMAIgAsACIAQwA6AFwAUAByAG8AZwByAGEAbQAgAEYAaQBsAGUAcwAgACgAeAA4ADYAKQBcAEYAaQBsAGkAZwByAGEAbgBcAE8AQQBFAFYAIABBAGcAZQBuAHQAXAByAHUAbgB0AGkAbQBlAHMAIgAgAC0ARABpAHIAZQBjAHQAbwByAHkAIAAtAFIAZQBjAHUAcgBzAGUAIAB8ACAAVwBoAGUAcgBlAC0ATwBiAGoAZQBjAHQAIAB7ACQAXwAuAEMAcgBlAGEAdABpAG8AbgBUAGkAbQBlACAALQBsAHQAIAAoAEcAZQB0AC0ARABhAHQAZQApAC4AQQBkAGQASABvAHUAcgBzACgALQAyADQAKQB9ACAAfAAgAFIAZQBtAG8AdgBlAC0ASQB0AGUAbQAgAC0AUgBlAGMAdQByAHMAZQAgAC0ARgBvAHIAYwBlAA==",
        action.getCommandWindows().getCommands_list().getFirst());
    assertEquals(agent.getExternalReference(), action.getAgentExternalReference());
  }
}
