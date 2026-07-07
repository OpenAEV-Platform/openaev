package io.openaev.executors.mde.service;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.mde.model.MdeDeviceGroup;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MdeExecutorServiceTest {

  private static final String DEVICE_GROUP_ID = "42";
  private static final String TENANT_ID = "test-tenant-id";
  private static final String EXECUTOR_ID = "test-mde-executor-id";

  @Mock private MdeExecutorClient client;
  @Mock private MdeExecutorConfig config;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AssetGroupService assetGroupService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private ExecutorService executorService;

  @InjectMocks private MdeExecutorService mdeExecutorService;

  @InjectMocks private MdeExecutorContextService mdeExecutorContextService;

  private MdeDevice mdeDevice;
  private Executor mdeExecutor;

  @BeforeEach
  void setUp() {
    mdeDevice = MdeDeviceFixture.createDefaultMdeDevice();
    mdeExecutor = new Executor();
    mdeExecutor.setId(EXECUTOR_ID);
    mdeExecutor.setName(MDE_EXECUTOR_NAME);
    mdeExecutor.setType(MDE_EXECUTOR_TYPE);
    mdeExecutor.setTenant(new Tenant(TENANT_ID));
  }

  @Test
  @DisplayName("given MDE device group with devices, should sync endpoints and create asset group")
  void test_run_mde() {
    // Arrange
    MdeDeviceGroup group = MdeDeviceFixture.createDefaultMdeDeviceGroup();
    when(config.getDeviceGroup()).thenReturn(DEVICE_GROUP_ID);
    when(client.deviceGroups()).thenReturn(List.of(group));
    when(client.devices(DEVICE_GROUP_ID)).thenReturn(List.of(mdeDevice));
    mdeExecutorService.setExecutor(mdeExecutor);

    // Act
    mdeExecutorService.run();

    // Assert
    ArgumentCaptor<String> executorIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService)
        .getAgentsByExecutorIdAndTenantId(executorIdCaptor.capture(), eq(TENANT_ID));
    assertEquals(EXECUTOR_ID, executorIdCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agentsCaptor = ArgumentCaptor.forClass(List.class);
    verify(endpointService)
        .syncAgentsEndpoints(inputsCaptor.capture(), agentsCaptor.capture(), eq(TENANT_ID));
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agentsCaptor.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService)
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(DEVICE_GROUP_ID, assetGroupCaptor.getValue().getExternalReference());
    assertEquals("Test Device Group", assetGroupCaptor.getValue().getName());
  }

  @Test
  @DisplayName(
      "given Windows agent with MDE executor, should call executeAction with base64-encoded command")
  void test_launchBatchExecutorSubprocess_mde_windows()
      throws JsonProcessingException, InterruptedException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptName()).thenReturn("openaev-subprocessor.ps1");
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        MDE_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + Endpoint.PLATFORM_ARCH.x86_64,
        "x86_64");
    injector.setExecutorCommands(executorCommands);
    Inject inject =
        InjectFixture.createTechnicalInject(
            InjectorContractFixture.createPayloadInjectorContractWithDefaultDomain(
                injector, payloadCommand),
            "Inject",
            EndpointFixture.createEndpoint());
    inject.setId("mde-inject-id");
    inject.setInjector(injector);
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "mde-agent-1"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);

    // Act
    mdeExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    // Scheduled execution: wait for the scheduled task to run
    Thread.sleep(1000);

    // Assert
    verify(client).executeAction(any(), eq("openaev-subprocessor.ps1"), any());
  }

  @Test
  @DisplayName(
      "given legacy inject without injector, should fallback to contract and execute action")
  void given_legacyInjectWithoutInjector_should_fallbackToContractAndExecuteAction()
      throws InterruptedException, JsonProcessingException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptName()).thenReturn("openaev-subprocessor.ps1");
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        MDE_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + Endpoint.PLATFORM_ARCH.x86_64,
        "x86_64");
    injector.setExecutorCommands(executorCommands);
    InjectorContract contract =
        InjectorContractFixture.createPayloadInjectorContract(injector, payloadCommand);
    Inject inject =
        InjectFixture.createTechnicalInject(
            contract, "Legacy MDE Inject", EndpointFixture.createEndpoint());
    inject.setId("mde-legacy-inject-id");
    // inject.setInjector NOT called — simulates legacy inject
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "mde-agent-2"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);

    // Act
    mdeExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    Thread.sleep(1000);

    // Assert
    verify(client).executeAction(any(), any(), any());
  }
}
