package io.openaev.executors.paloaltocortex.service;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Executor;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexEndpoint;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.PaloAltoCortexDeviceFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PaloAltoCortexExecutorServiceTest {

  @Mock private PaloAltoCortexExecutorClient client;
  @Mock private PaloAltoCortexExecutorConfig config;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AssetGroupService assetGroupService;
  @Mock private Ee enterpriseEditionService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private ExecutorService executorService;

  @InjectMocks private PaloAltoCortexExecutorService paloAltoCortexExecutorService;

  @InjectMocks private PaloAltoCortexExecutorContextService paloAltoCortexExecutorContextService;

  private PaloAltoCortexEndpoint paloAltoCortexEndpoint;
  private Executor paloAltoCortexExecutor;

  @BeforeEach
  void setUp() {
    paloAltoCortexEndpoint = PaloAltoCortexDeviceFixture.createDefaultPaloAltoCortexEndpoint();
    paloAltoCortexExecutor = new Executor();
    paloAltoCortexExecutor.setName(PALOALTOCORTEX_EXECUTOR_NAME);
    paloAltoCortexExecutor.setType(PALOALTOCORTEX_EXECUTOR_TYPE);
  }

  @Test
  void test_run_paloaltocortex() {
    // Init datas
    when(config.getGroupName()).thenReturn("groupName");
    when(client.endpoints("groupName")).thenReturn(List.of(paloAltoCortexEndpoint));
    // Run method to test
    paloAltoCortexExecutorService.run();
    // Asserts
    ArgumentCaptor<String> executorTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService).getAgentsByExecutorType(executorTypeCaptor.capture());
    assertEquals(paloAltoCortexExecutor.getType(), executorTypeCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agents = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), agents.capture());
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agents.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService)
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(
        PALOALTOCORTEX_EXECUTOR_TYPE + "_groupName",
        assetGroupCaptor.getValue().getExternalReference());
  }

  // FIXME: Commented for prerelease tests of solution, will fix later
  //  @Test
  //  void test_launchBatchExecutorSubprocess_paloaltocortex()
  //      throws JsonProcessingException, InterruptedException {
  //    // Init datas
  //    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
  //    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
  //    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
  //    when(config.getWindowsScriptUid()).thenReturn("1234567890");
  //    Command payloadCommand =
  //        PayloadFixture.createCommand(
  //            "cmd",
  //            "whoami",
  //            List.of(),
  //            "whoami",
  //            Set.of(new Domain(null, "To classify", "#000000", Instant.now(), null)));
  //    Injector injector = InjectorFixture.createDefaultPayloadInjector();
  //    Map<String, String> executorCommands = new HashMap<>();
  //    executorCommands.put(
  //        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
  //    injector.setExecutorCommands(executorCommands);
  //    Inject inject =
  //        InjectFixture.createTechnicalInject(
  //            InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand),
  //            "Inject",
  //            EndpointFixture.createEndpoint());
  //    inject.setId("injectId");
  //    List<Agent> agents =
  //        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345"));
  //    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
  //    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);
  //    // Run method to test
  //    paloAltoCortexExecutorContextService.launchBatchExecutorSubprocess(
  //        inject, new HashSet<>(agents), injectStatus);
  //    // Executor scheduled so we have to wait before the execution
  //    Thread.sleep(1000);
  //    // Asserts
  //    ArgumentCaptor<String> agentId = ArgumentCaptor.forClass(String.class);
  //    ArgumentCaptor<String> scriptId = ArgumentCaptor.forClass(String.class);
  //    ArgumentCaptor<PaloAltoCortexCommandList> commandEncoded =
  //        ArgumentCaptor.forClass(PaloAltoCortexCommandList.class);
  //    verify(client).executeScript(agentId.capture(), scriptId.capture(),
  // commandEncoded.capture());
  //    assertEquals("12345", agentId.getValue());
  //    assertEquals("1234567890", scriptId.getValue());
  //    assertEquals(
  //        POWERSHELL_CMD
  //            +
  // "cwB3AGkAdABjAGgAIAAoACQAZQBuAHYAOgBQAFIATwBDAEUAUwBTAE8AUgBfAEEAUgBDAEgASQBUAEUAQwBUAFUAUgBFACkAIAB7ACAAIgBBAE0ARAA2ADQAIgAgAHsAJABhAHIAYwBoAGkAdABlAGMAdAB1AHIAZQAgAD0AIAAiAHgAOAA2AF8ANgA0ACIAOwAgAEIAcgBlAGEAawB9ACAAIgBBAFIATQA2ADQAIgAgAHsAJABhAHIAYwBoAGkAdABlAGMAdAB1AHIAZQAgAD0AIAAiAGEAcgBtADYANAAiADsAIABCAHIAZQBhAGsAfQAgACIAeAA4ADYAIgAgAHsAIABzAHcAaQB0AGMAaAAgACgAJABlAG4AdgA6AFAAUgBPAEMARQBTAFMATwBSAF8AQQBSAEMASABJAFQARQBXADYANAAzADIAKQAgAHsAIAAiAEEATQBEADYANAAiACAAewAkAGEAcgBjAGgAaQB0AGUAYwB0AHUAcgBlACAAPQAgACIAeAA4ADYAXwA2ADQAIgA7ACAAQgByAGUAYQBrAH0AIAAiAEEAUgBNADYANAAiACAAewAkAGEAcgBjAGgAaQB0AGUAYwB0AHUAcgBlACAAPQAgACIAYQByAG0ANgA0ACIAOwAgAEIAcgBlAGEAawB9ACAAfQAgAH0AIAB9ADsAJABhAHIAYwBoAGkAdABlAGMAdAB1AHIAZQBgAA==",
  //        commandEncoded.getValue().getCommands_list().getFirst());
  //  }
}
