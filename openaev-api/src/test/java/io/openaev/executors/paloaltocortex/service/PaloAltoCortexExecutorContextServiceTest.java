package io.openaev.executors.paloaltocortex.service;

import static io.openaev.executors.ExecutorHelper.POWERSHELL_CMD;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexAction;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommand;
import io.openaev.executors.paloaltocortex.model.PaloAltoCortexCommandList;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaloAltoCortexExecutorContextServiceTest {

  private static final String TENANT_ID = "tenant-1";

  @Mock private PaloAltoCortexExecutorConfig config;
  @Mock private PaloAltoCortexExecutorClient client;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;
  @Mock private ExecutorService executorService;
  @Spy private OpenAEVConfig openAEVConfig = new OpenAEVConfig();

  @InjectMocks private PaloAltoCortexExecutorContextService service;

  @BeforeEach
  void setUp() {
    service.scheduledExecutorService = immediateScheduler();
    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);
    when(config.getWindowsScriptUid()).thenReturn("windows-script");
    when(config.getUnixScriptUid()).thenReturn("unix-script");
    openAEVConfig.setBaseUrl("http://localhost:8080");
    when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(null);
  }

  @Test
  @DisplayName("launchExecutorSubprocess should be a no-op")
  void given_validInput_should_doNothingInLaunchExecutorSubprocess() {
    // Arrange
    Inject inject = new Inject();
    Endpoint endpoint = EndpointFixture.createEndpoint();
    Agent agent = AgentFixture.createAgent(endpoint, "ref");

    // Act & Assert
    assertDoesNotThrow(() -> service.launchExecutorSubprocess(inject, endpoint, agent, "token"));
    verifyNoInteractions(client);
  }

  @Test
  @DisplayName("launchBatchExecutorSubprocess should execute windows, linux and macOS actions")
  void given_mixedPlatformAgents_should_executeWindowsAndUnixActions() {
    // Arrange
    Inject inject = buildInjectWithInjector();
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();

    Agent windowsAgent =
        AgentFixture.createAgent(
            EndpointFixture.createEndpointWithPlatform(
                "win-endpoint", Endpoint.PLATFORM_TYPE.Windows),
            "win-ext");
    windowsAgent.setId("agent-win");
    Agent linuxAgent =
        AgentFixture.createAgent(
            EndpointFixture.createEndpointWithPlatform(
                "linux-endpoint", Endpoint.PLATFORM_TYPE.Linux),
            "linux-ext");
    linuxAgent.setId("agent-linux");
    Agent macAgent =
        AgentFixture.createAgent(
            EndpointFixture.createEndpointWithPlatform(
                "mac-endpoint", Endpoint.PLATFORM_TYPE.MacOS),
            "mac-ext");
    macAgent.setId("agent-mac");

    List<Agent> managedAgents = List.of(windowsAgent, linuxAgent, macAgent);
    when(executorService.manageWithoutPlatformAgents(anyList(), eq(injectStatus)))
        .thenReturn(managedAgents);

    // Act
    List<Agent> returned =
        service.launchBatchExecutorSubprocess(
            inject, new HashSet<>(managedAgents), injectStatus, "token");

    // Assert
    assertEquals(3, returned.size());

    ArgumentCaptor<String> externalReferenceCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> scriptIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object> commandCaptor = ArgumentCaptor.forClass(Object.class);

    verify(client, times(3))
        .executeScript(
            externalReferenceCaptor.capture(), scriptIdCaptor.capture(), commandCaptor.capture());

    assertTrue(scriptIdCaptor.getAllValues().contains("windows-script"));
    assertEquals(
        2,
        scriptIdCaptor.getAllValues().stream().filter("unix-script"::equals).count(),
        "Linux + MacOS should use unix script UID");

    assertTrue(
        commandCaptor.getAllValues().stream().anyMatch(PaloAltoCortexCommandList.class::isInstance),
        "A Windows command list should be sent");
    assertTrue(
        commandCaptor.getAllValues().stream().anyMatch(PaloAltoCortexCommand.class::isInstance),
        "A Unix command should be sent");

    PaloAltoCortexCommandList windowsCommand =
        (PaloAltoCortexCommandList)
            commandCaptor.getAllValues().stream()
                .filter(PaloAltoCortexCommandList.class::isInstance)
                .findFirst()
                .orElseThrow();
    assertFalse(windowsCommand.getCommands_list().isEmpty());
    assertTrue(windowsCommand.getCommands_list().getFirst().startsWith(POWERSHELL_CMD));
  }

  @Test
  @DisplayName(
      "launchBatchExecutorSubprocess should fallback to contract injector for legacy inject")
  void given_legacyInjectWithoutInjector_should_fallbackToContractInjector() {
    // Arrange
    Injector injector = buildInjectorWithCommands();
    InjectorContract contract = new InjectorContract();
    contract.setId("contract-id");
    contract.setTenant(new Tenant(TENANT_ID));
    contract.addInjector(injector);

    Inject inject = new Inject();
    inject.setId("inject-id");
    inject.setTenant(new Tenant(TENANT_ID));
    inject.setInjectorContract(contract);
    // injector intentionally not set to cover fallback branch

    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    Agent windowsAgent =
        AgentFixture.createAgent(
            EndpointFixture.createEndpointWithPlatform(
                "win-endpoint", Endpoint.PLATFORM_TYPE.Windows),
            "win-ext");
    windowsAgent.setId("agent-win");
    when(executorService.manageWithoutPlatformAgents(anyList(), eq(injectStatus)))
        .thenReturn(List.of(windowsAgent));

    // Act
    List<Agent> returned =
        service.launchBatchExecutorSubprocess(
            inject, new HashSet<>(List.of(windowsAgent)), injectStatus, "token");

    // Assert
    assertEquals(1, returned.size());
    verify(client, times(1)).executeScript(anyString(), eq("windows-script"), any());
  }

  @Test
  @DisplayName("launchBatchExecutorSubprocess should throw when legacy inject has no contract")
  void given_legacyInjectWithoutContract_should_throwUnsupportedOperationException() {
    // Arrange
    Inject inject = new Inject();
    inject.setId("inject-id");
    inject.setTenant(new Tenant(TENANT_ID));
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();

    Agent windowsAgent =
        AgentFixture.createAgent(
            EndpointFixture.createEndpointWithPlatform(
                "win-endpoint", Endpoint.PLATFORM_TYPE.Windows),
            "win-ext");
    when(executorService.manageWithoutPlatformAgents(anyList(), eq(injectStatus)))
        .thenReturn(List.of(windowsAgent));

    // Act + Assert
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class,
            () ->
                service.launchBatchExecutorSubprocess(
                    inject, new HashSet<>(List.of(windowsAgent)), injectStatus, "token"));
    assertEquals("Inject does not have a contract", exception.getMessage());
  }

  @Test
  @DisplayName("executeActions should process paginated windows and unix actions")
  void given_mixedActions_should_executePaginatedBranches() {
    // Arrange
    PaloAltoCortexAction windowsAction = new PaloAltoCortexAction();
    windowsAction.setAgentExternalReference("windows-agent");
    windowsAction.setScriptId("windows-script");
    PaloAltoCortexCommandList commandList = new PaloAltoCortexCommandList();
    commandList.setCommands_list(List.of("encoded-windows-command"));
    windowsAction.setCommandWindows(commandList);

    PaloAltoCortexAction unixAction = new PaloAltoCortexAction();
    unixAction.setAgentExternalReference("unix-agent");
    unixAction.setScriptId("unix-script");
    PaloAltoCortexCommand unixCommand = new PaloAltoCortexCommand();
    unixCommand.setCommand("encoded-unix-command");
    unixAction.setCommandUnix(unixCommand);

    when(config.getApiBatchExecutionActionPagination()).thenReturn(1);

    // Act
    service.executeActions(List.of(windowsAction, unixAction));

    // Assert
    verify(client).executeScript("windows-agent", "windows-script", commandList);
    verify(client).executeScript("unix-agent", "unix-script", unixCommand);
    verify(client, times(2)).executeScript(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("executeActions should not call client when action list is empty")
  void given_noActions_should_notCallClient() {
    // Act
    service.executeActions(List.of());

    // Assert
    verifyNoInteractions(client);
  }

  private Inject buildInjectWithInjector() {
    Inject inject = new Inject();
    inject.setId("inject-id");
    inject.setTenant(new Tenant(TENANT_ID));
    inject.setInjector(buildInjectorWithCommands());
    return inject;
  }

  private Injector buildInjectorWithCommands() {
    Injector injector = new Injector();
    injector.setId("injector-id");
    injector.setTenantId(TENANT_ID);

    Map<String, String> commands = new HashMap<>();
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Windows.name()
            + "."
            + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x86_64 $x=a location=b;[Environment]::CurrentDirectory #{inject} #{agent} #{tenant} #{token} #{baseUrl}");
    // Unix now reads executor-prefixed keys too, like Windows already did: the detached command
    // returns the Live Terminal session as soon as the implant is launched. The generic entries
    // below stay in the fixture because the native OpenAEV agent and Caldera still read them.
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.Linux.name()
            + "."
            + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x86_64 $x=a location=b;filename=#{inject} #{agent} #{tenant} #{token} #{baseUrl}");
    commands.put(
        PALOALTOCORTEX_EXECUTOR_NAME
            + "."
            + Endpoint.PLATFORM_TYPE.MacOS.name()
            + "."
            + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x86_64 $x=a location=b;filename=#{inject} #{agent} #{tenant} #{token} #{baseUrl}");
    commands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x86_64 $x=a location=b;filename=#{inject} #{agent} #{tenant} #{token} #{baseUrl}");
    commands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "x86_64 $x=a location=b;filename=#{inject} #{agent} #{tenant} #{token} #{baseUrl}");
    injector.setExecutorCommands(commands);

    return injector;
  }

  private ScheduledExecutorService immediateScheduler() {
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    lenient()
        .doAnswer(
            invocation -> {
              Runnable runnable = invocation.getArgument(0);
              runnable.run();
              return mock(ScheduledFuture.class);
            })
        .when(scheduler)
        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    return scheduler;
  }
}
