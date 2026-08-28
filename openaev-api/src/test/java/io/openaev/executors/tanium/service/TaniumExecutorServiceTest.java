package io.openaev.executors.tanium.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.DataComputerGroup;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.TaniumComputerGroup;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TaniumExecutorServiceTest {

  private static final String HOST_GROUP_TANIUM = "hostGroupTanium";

  @Mock private TaniumExecutorClient client;
  @Mock private TaniumExecutorConfig config;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AssetGroupService assetGroupService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private ExecutorService executorService;
  @Mock private OpenAEVConfig openAEVConfig;

  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private TaniumExecutorService taniumExecutorService;

  @InjectMocks private TaniumExecutorContextService taniumExecutorContextService;

  private NodeEndpoint taniumEndpoint;
  private Executor taniumExecutor;

  @BeforeEach
  void setUp() {
    taniumEndpoint = TaniumDeviceFixture.createDefaultTaniumEndpoint();
    taniumExecutor = new Executor();
    taniumExecutor.setName(TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME);
    taniumExecutor.setType(TaniumExecutorIntegration.TANIUM_EXECUTOR_TYPE);
    taniumExecutor.setTenantId(TenantContext.getCurrentTenant());
    // The service wraps run() in tenantTx.execute(...): make the mock actually invoke the
    // supplied work, otherwise doRun() never happens and the tests below have nothing to verify.
    lenient()
        .when(tenantTx.execute(any(), any(java.util.function.Supplier.class)))
        .thenAnswer(
            invocation -> {
              java.util.function.Supplier<?> work = invocation.getArgument(1);
              return work.get();
            });
  }

  @Test
  void test_run_tanium() {
    // Init datas
    DataComputerGroup dataComputerGroup = new DataComputerGroup();
    TaniumComputerGroup computerGroup = new TaniumComputerGroup();
    computerGroup.setId(HOST_GROUP_TANIUM);
    computerGroup.setName("tanium");
    dataComputerGroup.setComputerGroup(computerGroup);
    when(config.getComputerGroupId()).thenReturn(HOST_GROUP_TANIUM);
    when(client.computerGroup(HOST_GROUP_TANIUM)).thenReturn(dataComputerGroup);
    when(client.endpoints(HOST_GROUP_TANIUM)).thenReturn(List.of(taniumEndpoint));
    taniumExecutorService.setExecutor(taniumExecutor);
    // Run method to test
    taniumExecutorService.run();
    // Asserts
    ArgumentCaptor<String> executorIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService)
        .getAgentsByExecutorIdAndTenantId(
            executorIdCaptor.capture(), eq(TenantContext.getCurrentTenant()));
    assertEquals(taniumExecutor.getId(), executorIdCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agents = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), agents.capture(), any());
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agents.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService)
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(HOST_GROUP_TANIUM, assetGroupCaptor.getValue().getExternalReference());
  }

  @Test
  void test_launchBatchExecutorSubprocess_tanium()
      throws JsonProcessingException, InterruptedException {
    // Init datas
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsPackageId()).thenReturn(112200);
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    injector.setExecutorCommands(executorCommands);
    Inject inject =
        InjectFixture.createTechnicalInject(
            InjectorContractFixture.createPayloadInjectorContractWithDefaultDomain(
                injector, payloadCommand),
            "Inject",
            EndpointFixture.createEndpoint());
    inject.setId("1234567890");
    inject.setInjector(injector);
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
    // Run method to test
    taniumExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    // Executor scheduled so we have to wait before the execution
    Thread.sleep(1000);
    // Asserts
    ArgumentCaptor<String> agentId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Integer> scriptId = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<String> commandEncoded = ArgumentCaptor.forClass(String.class);
    verify(client).executeAction(agentId.capture(), scriptId.capture(), commandEncoded.capture());
    assertEquals("12345", agentId.getValue());
    assertEquals(112200, scriptId.getValue());
    assertEquals("eDg2XzY0", commandEncoded.getValue());
  }

  @Test
  @DisplayName(
      "given legacy inject without injector, should fallback to contract and execute action")
  void given_legacyInjectWithoutInjector_should_fallbackToContractAndExecuteAction()
      throws InterruptedException, com.fasterxml.jackson.core.JsonProcessingException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsPackageId()).thenReturn(112200);
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "x86_64");
    injector.setExecutorCommands(executorCommands);
    InjectorContract contract =
        InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand);
    Inject inject =
        InjectFixture.createTechnicalInject(
            contract, "Legacy Inject", EndpointFixture.createEndpoint());
    inject.setId("legacyInjectId");
    // inject.setInjector is NOT called — this simulates a legacy inject
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");

    // Act
    taniumExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    Thread.sleep(1000);

    // Assert
    verify(client).executeAction(any(), any(), any());
  }
}
