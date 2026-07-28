package io.openaev.executors.sentinelone.service;

import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_TYPE;
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
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.model.AgentRegisterInput;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SentinelOneExecutorServiceTest {

  @Mock private SentinelOneExecutorClient client;
  @Mock private SentinelOneExecutorConfig config;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private AssetGroupService assetGroupService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private EndpointService endpointService;
  @Mock private AgentService agentService;
  @Mock private ExecutorService executorService;
  @Mock private OpenAEVConfig openAEVConfig;

  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private SentinelOneExecutorService sentinelOneExecutorService;

  @InjectMocks private SentinelOneExecutorContextService sentinelOneExecutorContextService;

  private SentinelOneAgent sentinelOneAgent;
  private Executor sentinelOneExecutor;

  @BeforeEach
  void setUp() {
    sentinelOneAgent = SentinelOneDeviceFixture.createDefaultSentinelOneAgent();
    sentinelOneExecutor = new Executor();
    sentinelOneExecutor.setName(SENTINELONE_EXECUTOR_NAME);
    sentinelOneExecutor.setType(SENTINELONE_EXECUTOR_TYPE);
    sentinelOneExecutor.setTenantId(TenantContext.getCurrentTenant());
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
  void test_run_sentinelone() {
    // Init datas
    when(client.agents()).thenReturn(Set.of(sentinelOneAgent));
    sentinelOneExecutorService.setExecutor(sentinelOneExecutor);
    // Run method to test
    sentinelOneExecutorService.run();
    // Asserts
    ArgumentCaptor<String> executorIdCaptor = ArgumentCaptor.forClass(String.class);
    verify(agentService)
        .getAgentsByExecutorIdAndTenantId(
            executorIdCaptor.capture(), eq(TenantContext.getCurrentTenant()));
    assertEquals(sentinelOneExecutor.getId(), executorIdCaptor.getValue());

    ArgumentCaptor<List<AgentRegisterInput>> inputsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<Agent>> agents = ArgumentCaptor.forClass(List.class);
    verify(endpointService).syncAgentsEndpoints(inputsCaptor.capture(), agents.capture(), any());
    assertEquals(1, inputsCaptor.getValue().size());
    assertEquals(0, agents.getValue().size());

    ArgumentCaptor<AssetGroup> assetGroupCaptor = ArgumentCaptor.forClass(AssetGroup.class);
    verify(assetGroupService, times(3))
        .createOrUpdateAssetGroupWithoutDynamicAssets(assetGroupCaptor.capture());
    assertEquals(3, assetGroupCaptor.getAllValues().size());
    assertEquals(
        sentinelOneAgent.getAccountId(),
        assetGroupCaptor.getAllValues().get(0).getExternalReference());
    assertEquals(
        sentinelOneAgent.getGroupId(),
        assetGroupCaptor.getAllValues().get(1).getExternalReference());
    assertEquals(
        sentinelOneAgent.getSiteId(),
        assetGroupCaptor.getAllValues().get(2).getExternalReference());
  }

  @Test
  void test_launchBatchExecutorSubprocess_sentinelone()
      throws JsonProcessingException, InterruptedException {
    // Init datas
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptId()).thenReturn("1234567890");
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
    inject.setId("injectId");
    List<Agent> agents =
        List.of(AgentFixture.createAgent(EndpointFixture.createEndpoint(), "12345"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
    // Run method to test
    sentinelOneExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    // Executor scheduled so we have to wait before the execution
    Thread.sleep(1000);
    // Asserts
    ArgumentCaptor<String> agentId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> scriptName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> commandEncoded = ArgumentCaptor.forClass(String.class);
    verify(client).executeScript(agentId.capture(), scriptName.capture(), commandEncoded.capture());
    assertEquals("12345", agentId.getValue());
    assertEquals("1234567890", scriptName.getValue());
    // The self-clean command is now prepended to the inject command before encoding. Decode
    // (UTF-16LE, SentinelOne Windows -encodedCommand) and assert the clean block precedes the arch
    // payload. Pins the exact clean-command coverage previously held by the deleted GC test.
    String decodedWindows =
        new String(
            Base64.getDecoder().decode(commandEncoded.getValue()), StandardCharsets.UTF_16LE);
    assertEquals(ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND + ";x86_64", decodedWindows);
  }

  @Test
  void test_launchBatchExecutorSubprocess_sentinelone_unix()
      throws JsonProcessingException, InterruptedException {
    // Init datas
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
    doNothing().when(enterpriseEditionService).throwEEExecutorService(any(), any(), any());
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getUnixScriptId()).thenReturn("unixScript");
    Command payloadCommand = PayloadFixture.createCommand("cmd", "whoami", List.of(), "whoami");
    Injector injector = InjectorFixture.createDefaultPayloadInjector();
    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64, "linuxcmd");
    injector.setExecutorCommands(executorCommands);
    Inject inject =
        InjectFixture.createTechnicalInject(
            InjectorContractFixture.createPayloadInjectorContractWithDefaultDomain(
                injector, payloadCommand),
            "Inject",
            EndpointFixture.createEndpointWithPlatform("linux-ep", Endpoint.PLATFORM_TYPE.Linux));
    inject.setId("injectId");
    List<Agent> agents =
        List.of(
            AgentFixture.createAgent(
                EndpointFixture.createEndpointWithPlatform(
                    "linux-ep", Endpoint.PLATFORM_TYPE.Linux),
                "12345"));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    when(executorService.manageWithoutPlatformAgents(agents, injectStatus)).thenReturn(agents);
    when(openAEVConfig.getBaseUrlForAgent()).thenReturn("http://localhost:8080");
    // Run method to test
    sentinelOneExecutorContextService.launchBatchExecutorSubprocess(
        inject, new HashSet<>(agents), injectStatus, "token");
    // Executor scheduled so we have to wait before the execution
    Thread.sleep(1000);
    // Asserts
    ArgumentCaptor<String> agentId = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> scriptName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> commandEncoded = ArgumentCaptor.forClass(String.class);
    verify(client).executeScript(agentId.capture(), scriptName.capture(), commandEncoded.capture());
    assertEquals("12345", agentId.getValue());
    assertEquals("unixScript", scriptName.getValue());
    // Unix command is UTF-8 encoded and prepended with the self-clean command.
    String decodedUnix =
        new String(Base64.getDecoder().decode(commandEncoded.getValue()), StandardCharsets.UTF_8);
    assertEquals(ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND + ";linuxcmd", decodedUnix);
  }
}
