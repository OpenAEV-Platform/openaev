package io.openaev.executors.mde.service;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
  @Mock private OpenAEVConfig openAEVConfig;

  @Mock private TenantScopedTransaction tenantTx;

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
    mdeExecutor.setTenantId(TENANT_ID);
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
  @DisplayName("given MDE device group with devices, should sync endpoints and create asset group")
  void test_run_mde() {
    // Arrange
    when(config.getDeviceGroup()).thenReturn(DEVICE_GROUP_ID);
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
      "given Windows agent with MDE executor, should call executeAction with base64-encoded"
          + " command")
  void test_launchBatchExecutorSubprocess_mde_windows() throws JsonProcessingException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptName()).thenReturn("openaev-subprocessor.ps1");
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
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

    // Assert — the Live Response dispatch is scheduled asynchronously
    verify(client, timeout(2000)).executeAction(any(), eq("openaev-subprocessor.ps1"), any());
  }

  @Test
  @DisplayName(
      "given legacy inject without injector, should fallback to contract and execute action")
  void given_legacyInjectWithoutInjector_should_fallbackToContractAndExecuteAction()
      throws JsonProcessingException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptName()).thenReturn("openaev-subprocessor.ps1");
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
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

    // Assert — the Live Response dispatch is scheduled asynchronously
    verify(client, timeout(2000)).executeAction(any(), any(), any());
  }

  // ---------------------------------------------------------------------------
  // Regression: null-guard on batch pagination (MdeExecutorContextService#executeActions)
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "given null batch pagination config, should still dispatch executeAction using the default")
  void given_nullBatchPaginationConfig_should_stillDispatchExecuteAction()
      throws JsonProcessingException {
    // Arrange
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    // Real deployments deserialize an absent @IntegrationConfigKey as null, overriding the field
    // default — this previously NPE'd in the batching math and aborted the whole dispatch.
    when(config.getApiBatchExecutionActionPagination()).thenReturn(null);
    when(config.getWindowsScriptName()).thenReturn("openaev-subprocessor.ps1");
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
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
    inject.setId("mde-null-pagination-inject-id");
    inject.setInjector(injector);
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "mde-agent-null-page"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);

    // Act
    mdeExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");

    // Assert — dispatch must still happen (falling back to the default pagination)
    verify(client, timeout(2000)).executeAction(any(), eq("openaev-subprocessor.ps1"), any());
  }

  // ---------------------------------------------------------------------------
  // Advanced Hunting active-status resolution (MdeExecutorService#resolveLastSeen via run())
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "given Advanced Hunting available and device present, should use fresh activity instant")
  void given_advancedHuntingAvailableAndDevicePresent_should_useFreshActivityInstant() {
    // Arrange — stale inventory lastSeen and Inactive health would both mark it inactive; the
    // fresh Advanced Hunting activity must win.
    Instant freshActivity = Instant.now().minus(2, ChronoUnit.MINUTES);
    MdeDevice device =
        MdeDeviceFixture.createMdeDevice("Inactive", Instant.now().minus(2, ChronoUnit.DAYS));
    Map<String, Instant> recentActivity = new HashMap<>();
    recentActivity.put(device.getId(), freshActivity);
    when(client.getRecentDeviceActivity(anyInt())).thenReturn(recentActivity);
    when(client.devicesAll()).thenReturn(List.of(device));
    mdeExecutorService.setExecutor(mdeExecutor);

    // Act
    mdeExecutorService.run();

    // Assert
    AgentRegisterInput input = captureSyncedInputs().get(0);
    assertEquals(freshActivity, input.getLastSeen());
    assertTrue(input.isActive());
  }

  @Test
  @DisplayName(
      "given Advanced Hunting available but device absent, should fall back to inventory lastSeen")
  void given_advancedHuntingAvailableButDeviceAbsent_should_fallBackToInventoryLastSeen() {
    // Arrange — Active health would falsely mark it active if the inventory fallback were skipped.
    Instant staleInventory = Instant.now().minus(2, ChronoUnit.DAYS);
    MdeDevice device = MdeDeviceFixture.createMdeDevice("Active", staleInventory);
    when(client.getRecentDeviceActivity(anyInt())).thenReturn(new HashMap<>());
    when(client.devicesAll()).thenReturn(List.of(device));
    mdeExecutorService.setExecutor(mdeExecutor);

    // Act
    mdeExecutorService.run();

    // Assert
    AgentRegisterInput input = captureSyncedInputs().get(0);
    assertEquals(
        Instant.parse(MdeDeviceFixture.formatLastSeen(staleInventory)), input.getLastSeen());
    assertFalse(input.isActive());
  }

  @Test
  @DisplayName(
      "given Advanced Hunting unavailable and healthStatus Active, should mark agent active")
  void given_advancedHuntingUnavailableAndHealthActive_should_markAgentActive() {
    // Arrange
    MdeDevice device =
        MdeDeviceFixture.createMdeDevice("Active", Instant.now().minus(2, ChronoUnit.DAYS));
    when(client.getRecentDeviceActivity(anyInt())).thenReturn(null);
    when(client.devicesAll()).thenReturn(List.of(device));
    mdeExecutorService.setExecutor(mdeExecutor);
    Instant beforeRun = Instant.now();

    // Act
    mdeExecutorService.run();

    // Assert — Active health approximates reachability with "now" (within the 1h threshold).
    AgentRegisterInput input = captureSyncedInputs().get(0);
    assertTrue(input.isActive());
    assertFalse(input.getLastSeen().isBefore(beforeRun));
  }

  @Test
  @DisplayName(
      "given Advanced Hunting unavailable and healthStatus not Active, should use inventory"
          + " lastSeen")
  void given_advancedHuntingUnavailableAndHealthInactive_should_useInventoryLastSeen() {
    // Arrange
    Instant staleInventory = Instant.now().minus(2, ChronoUnit.DAYS);
    MdeDevice device = MdeDeviceFixture.createMdeDevice("Inactive", staleInventory);
    when(client.getRecentDeviceActivity(anyInt())).thenReturn(null);
    when(client.devicesAll()).thenReturn(List.of(device));
    mdeExecutorService.setExecutor(mdeExecutor);

    // Act
    mdeExecutorService.run();

    // Assert
    AgentRegisterInput input = captureSyncedInputs().get(0);
    assertEquals(
        Instant.parse(MdeDeviceFixture.formatLastSeen(staleInventory)), input.getLastSeen());
    assertFalse(input.isActive());
  }

  @SuppressWarnings("unchecked")
  private List<AgentRegisterInput> captureSyncedInputs() {
    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), any(), eq(TENANT_ID));
    return inputsCaptor.getValue();
  }
}
