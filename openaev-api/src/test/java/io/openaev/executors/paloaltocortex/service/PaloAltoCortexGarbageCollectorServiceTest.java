package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.POWERSHELL_CMD;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.service.AgentService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaloAltoCortexGarbageCollectorServiceTest {

  @Mock private AgentService agentService;
  @Mock private PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;
  @Mock private PaloAltoCortexExecutorConfig config;

  @InjectMocks private PaloAltoCortexGarbageCollectorService paloAltoCortexGarbageCollectorService;

  @Test
  void test_run_garbageCollector_withPaloAltoCortexAgents() {
    // Init datas
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setExternalReference("agent_external_reference");
    agent.setAsset(EndpointFixture.createEndpoint());
    when(agentService.getAgentsByExecutorType(PALOALTOCORTEX_EXECUTOR_TYPE))
        .thenReturn(List.of(agent));
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
