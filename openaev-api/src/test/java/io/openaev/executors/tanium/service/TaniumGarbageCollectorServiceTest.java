package io.openaev.executors.tanium.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.TaniumAction;
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
public class TaniumGarbageCollectorServiceTest {

  private static final String EXECUTOR_ID = "test-executor-id";

  @Mock private AgentService agentService;
  @Mock private TaniumExecutorContextService taniumExecutorContextService;
  @Mock private TaniumExecutorConfig config;

  private TaniumGarbageCollectorService taniumGarbageCollectorService;

  @BeforeEach
  void setUp() {
    taniumGarbageCollectorService =
        new TaniumGarbageCollectorService(
            config, taniumExecutorContextService, agentService, EXECUTOR_ID);
  }

  @Test
  void test_run_garbageCollector_withCrowdstrikeAgents() {
    // Init datas
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(EndpointFixture.createEndpoint());
    when(agentService.getAgentsByExecutorId(EXECUTOR_ID)).thenReturn(List.of(agent));
    when(config.getWindowsPackageId()).thenReturn(12345);
    // Run method to test
    taniumGarbageCollectorService.run();
    // Asserts
    ArgumentCaptor<List<TaniumAction>> actionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(taniumExecutorContextService).executeActions(actionsCaptor.capture());
    assertEquals(1, actionsCaptor.getValue().size());
    TaniumAction taniumAction = actionsCaptor.getValue().get(0);
    assertEquals(12345, taniumAction.getScriptId());
    assertEquals(
        "R2V0LUNoaWxkSXRlbSAtUGF0aCAiQzpcUHJvZ3JhbSBGaWxlcyAoeDg2KVxGaWxpZ3JhblxPQUVWIEFnZW50XHBheWxvYWRzIiwiQzpcUHJvZ3JhbSBGaWxlcyAoeDg2KVxGaWxpZ3JhblxPQUVWIEFnZW50XHJ1bnRpbWVzIiAtRGlyZWN0b3J5IC1SZWN1cnNlIHwgV2hlcmUtT2JqZWN0IHskXy5DcmVhdGlvblRpbWUgLWx0IChHZXQtRGF0ZSkuQWRkSG91cnMoLTI0KX0gfCBSZW1vdmUtSXRlbSAtUmVjdXJzZSAtRm9yY2U=",
        taniumAction.getCommandEncoded());
    assertEquals(agent.getExternalReference(), taniumAction.getAgentExternalReference());
  }
}
