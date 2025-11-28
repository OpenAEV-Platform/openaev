package io.openaev.executors.crowdstrike.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.Ee;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeDevice;
import io.openaev.executors.crowdstrike.model.CrowdStrikeHostGroup;
import io.openaev.executors.crowdstrike.model.ResourcesGroups;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.DomainComposer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CrowdstrikeExecutorServiceTest {

  public static final String HOST_GROUP_CS = "hostGroupCs";

  @Mock private CrowdStrikeExecutorClient client;
  @Mock private CrowdStrikeExecutorConfig config;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AssetGroupService assetGroupService;
  @Mock private Ee eeService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;

  @InjectMocks private CrowdStrikeExecutorService crowdStrikeExecutorService;

  @InjectMocks private CrowdStrikeExecutorContextService crowdStrikeExecutorContextService;

  private DomainComposer domainComposer;
  private CrowdStrikeDevice crowdstrikeAgent;
  private Executor crowdstrikeExecutor;

  @BeforeEach
  void setUp() {
    crowdstrikeAgent = CrowdstrikeDeviceFixture.createDefaultCrowdStrikeDevice();
    crowdstrikeExecutor = new Executor();
    crowdstrikeExecutor.setName(CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME);
    crowdstrikeExecutor.setType(CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_TYPE);
    domainComposer = new DomainComposer();
  }

  @Test
  void test_run_crowdstrike() {
    // Init datas
    ResourcesGroups resourcesGroups = new ResourcesGroups();
    CrowdStrikeHostGroup crowdstrikeHostGroup = new CrowdStrikeHostGroup();
    crowdstrikeHostGroup.setId(HOST_GROUP_CS);
    crowdstrikeHostGroup.setName("crowdstrike");
    resourcesGroups.setResources(List.of(crowdstrikeHostGroup));
    when(config.getHostGroup()).thenReturn(HOST_GROUP_CS);
    when(client.hostGroup(HOST_GROUP_CS)).thenReturn(resourcesGroups);
    when(client.devices(HOST_GROUP_CS)).thenReturn(List.of(crowdstrikeAgent));
    // Run method to test
    crowdStrikeExecutorService.run();
    // Asserts
    ArgumentCaptor<String> executorTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService).getAgentsByExecutorType(executorTypeCaptor.capture());
    assertEquals(crowdstrikeExecutor.getType(), executorTypeCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agents = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), agents.capture());
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agents.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService)
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(HOST_GROUP_CS, assetGroupCaptor.getValue().getExternalReference());
  }

  @Test
  void test_launchBatchExecutorSubprocess_crowdstrike()
      throws InterruptedException, JsonProcessingException {
    // Init datas
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(eeService).throwEEExecutorService(any(), any(), any());
    when(config.isEnable()).thenReturn(true);
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptName()).thenReturn("MyScript");
    Command payloadCommand =
        PayloadFixture.createCommand(
            "cmd",
            "whoami",
            List.of(),
            "whoami",
            Set.of(new Domain(null, "To classify", "#000000", Instant.now(), null)));
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    injector.setExecutorCommands(executorCommands);
    Inject inject =
        InjectFixture.createTechnicalInject(
            InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand),
            "Inject",
            EndpointFixture.createEndpoint());
    inject.setId("1234567890");
    // Run method to test
    crowdStrikeExecutorContextService.launchBatchExecutorSubprocess(
        inject,
        Set.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345")),
        InjectStatusFixture.createPendingInjectStatus());
    // Asserts
    ArgumentCaptor<List<String>> agentIds = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<String> scriptName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> commandEncoded = ArgumentCaptor.forClass(String.class);
    verify(client)
        .executeAction(agentIds.capture(), scriptName.capture(), commandEncoded.capture());
    assertEquals(1, agentIds.getValue().size());
    assertEquals("MyScript", scriptName.getValue());
    assertEquals(
        "cwB3AGkAdABjAGgAIAAoACQAZQBuAHYAOgBQAFIATwBDAEUAUwBTAE8AUgBfAEEAUgBDAEgASQBUAEUAQwBUAFUAUgBFACkAIAB7ACAAIgBBAE0ARAA2ADQAIgAgAHsAJABhAHIAYwBoAGkAdABlAGMAdAB1AHIAZQAgAD0AIAAiAHgAOAA2AF8ANgA0ACIAOwAgAEIAcgBlAGEAawB9ACAAIgBBAFIATQA2ADQAIgAgAHsAJABhAHIAYwBoAGkAdABlAGMAdAB1AHIAZQAgAD0AIAAiAGEAcgBtADYANAAiADsAIABCAHIAZQBhAGsAfQAgACIAeAA4ADYAIgAgAHsAIABzAHcAaQB0AGMAaAAgACgAJABlAG4AdgA6AFAAUgBPAEMARQBTAFMATwBSAF8AQQBSAEMASABJAFQARQBXADYANAAzADIAKQAgAHsAIAAiAEEATQBEADYANAAiACAAewAkAGEAcgBjAGgAaQB0AGUAYwB0AHUAcgBlACAAPQAgACIAeAA4ADYAXwA2ADQAIgA7ACAAQgByAGUAYQBrAH0AIAAiAEEAUgBNADYANAAiACAAewAkAGEAcgBjAGgAaQB0AGUAYwB0AHUAcgBlACAAPQAgACIAYQByAG0ANgA0ACIAOwAgAEIAcgBlAGEAawB9ACAAfQAgAH0AIAB9ADsAJABhAGcAZQBuAHQASQBEAD0AWwBTAHkAcwB0AGUAbQAuAEIAaQB0AEMAbwBuAHYAZQByAHQAZQByAF0AOgA6AFQAbwBTAHQAcgBpAG4AZwAoACgAKABHAGUAdAAtAEkAdABlAG0AUAByAG8AcABlAHIAdAB5ACAAJwBIAEsATABNADoAXABTAFkAUwBUAEUATQBcAEMAdQByAHIAZQBuAHQAQwBvAG4AdAByAG8AbABTAGUAdABcAFMAZQByAHYAaQBjAGUAcwBcAEMAUwBBAGcAZQBuAHQAXABTAGkAbQAnACkALgBBAEcAKQApAC4AVABvAEwAbwB3AGUAcgAoACkAIAAtAHIAZQBwAGwAYQBjAGUAIAAnAC0AJwAsACcAJwA7ACQAYQByAGMAaABpAHQAZQBjAHQAdQByAGUAYAA=",
        commandEncoded.getValue());
  }
}
