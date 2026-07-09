package io.openaev.executors.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenAEVExecutorContextServiceTest {

  private static final String TENANT_ID = "tenant-id";

  @Mock private AssetAgentJobRepository assetAgentJobRepository;
  @Mock private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Spy private OpenAEVConfig openAEVConfig = new OpenAEVConfig();

  @InjectMocks private OpenAEVExecutorContextService service;

  @Test
  @DisplayName("launchExecutorSubprocess windows should create asset job with replaced tokens")
  void given_windowsEndpoint_should_saveAssetAgentJobWithReplacedCommand() {
    // Arrange
    openAEVConfig.setBaseUrl("http://localhost:8080");
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-1");
    Endpoint endpoint =
        createEndpoint(Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_ARCH.x86_64);

    // Act
    service.launchExecutorSubprocess(inject, endpoint, agent, "token-123");

    // Assert
    ArgumentCaptor<AssetAgentJob> assetAgentJobCaptor =
        ArgumentCaptor.forClass(AssetAgentJob.class);
    verify(assetAgentJobRepository).save(assetAgentJobCaptor.capture());

    AssetAgentJob saved = assetAgentJobCaptor.getValue();
    assertNotNull(saved.getCreatedAt());
    assertSame(agent, saved.getAgent());
    assertSame(inject, saved.getInject());
    assertSame(inject.getTenant(), saved.getTenant());

    assertNotNull(saved.getCommand());
    assertFalse(saved.getCommand().contains("#{inject}"));
    assertFalse(saved.getCommand().contains("#{agent}"));
    assertFalse(saved.getCommand().contains("#{tenant}"));
    assertFalse(saved.getCommand().contains("#{token}"));
    assertFalse(saved.getCommand().contains("#{baseUrl}"));
    assertTrue(saved.getCommand().contains("inject-1"));
    assertTrue(saved.getCommand().contains("agent-1"));
    assertTrue(saved.getCommand().contains(TENANT_ID));
    assertTrue(saved.getCommand().contains("token-123"));
    assertTrue(saved.getCommand().contains("http://localhost:8080"));
  }

  @Test
  @DisplayName("launchExecutorSubprocess linux should replace tokens")
  void given_linuxEndpoint_should_saveAssetAgentJob() {
    // Arrange
    openAEVConfig.setBaseUrl("https://openaev.local");
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-linux");
    Endpoint endpoint = createEndpoint(Endpoint.PLATFORM_TYPE.Linux, Endpoint.PLATFORM_ARCH.x86_64);

    // Act
    service.launchExecutorSubprocess(inject, endpoint, agent, "linux-token");

    // Assert
    ArgumentCaptor<AssetAgentJob> assetAgentJobCaptor =
        ArgumentCaptor.forClass(AssetAgentJob.class);
    verify(assetAgentJobRepository).save(assetAgentJobCaptor.capture());
    assertTrue(assetAgentJobCaptor.getValue().getCommand().contains("linux-token"));
    assertTrue(assetAgentJobCaptor.getValue().getCommand().contains("https://openaev.local"));
  }

  @Test
  @DisplayName("launchExecutorSubprocess macOS should replace tokens")
  void given_macosEndpoint_should_saveAssetAgentJob() {
    // Arrange
    openAEVConfig.setBaseUrl("https://acme.local");
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-mac");
    Endpoint endpoint = createEndpoint(Endpoint.PLATFORM_TYPE.MacOS, Endpoint.PLATFORM_ARCH.arm64);

    // Act
    service.launchExecutorSubprocess(inject, endpoint, agent, "mac-token");

    // Assert
    ArgumentCaptor<AssetAgentJob> assetAgentJobCaptor =
        ArgumentCaptor.forClass(AssetAgentJob.class);
    verify(assetAgentJobRepository).save(assetAgentJobCaptor.capture());
    assertTrue(assetAgentJobCaptor.getValue().getCommand().contains("mac-token"));
    assertTrue(assetAgentJobCaptor.getValue().getCommand().contains("https://acme.local"));
  }

  @Test
  @DisplayName("launchExecutorSubprocess legacy inject should fallback to contract injector")
  void given_legacyInjectWithoutInjector_should_fallbackToContractInjector() {
    // Arrange
    openAEVConfig.setBaseUrl("http://localhost:8080");
    Injector fallbackInjector = createInjectorCommandMap();
    InjectorContract injectorContract = mock(InjectorContract.class);
    when(injectorContract.getFirstInjector()).thenReturn(fallbackInjector);

    Inject inject = new Inject();
    inject.setId("inject-legacy");
    inject.setTenant(new Tenant(TENANT_ID));
    inject.setInjectorContract(injectorContract);
    Agent agent = createAgent("agent-legacy");
    Endpoint endpoint =
        createEndpoint(Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_ARCH.x86_64);

    // Act
    service.launchExecutorSubprocess(inject, endpoint, agent, "legacy-token");

    // Assert
    verify(assetAgentJobRepository).save(any(AssetAgentJob.class));
  }

  @Test
  @DisplayName("launchExecutorSubprocess legacy inject without contract should throw")
  void given_legacyInjectWithoutContract_should_throwUnsupportedOperationException() {
    // Arrange
    Inject inject = new Inject();
    inject.setId("inject-no-contract");
    inject.setTenant(new Tenant(TENANT_ID));
    Agent agent = createAgent("agent-x");
    Endpoint endpoint =
        createEndpoint(Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_ARCH.x86_64);

    // Act + Assert
    UnsupportedOperationException exception =
        assertThrows(
            UnsupportedOperationException.class,
            () -> service.launchExecutorSubprocess(inject, endpoint, agent, "token"));
    assertEquals("Inject does not have a contract", exception.getMessage());
    verifyNoInteractions(assetAgentJobRepository);
  }

  @Test
  @DisplayName("launchExecutorSubprocess null platform should throw")
  void given_nullPlatform_should_throwRuntimeException() {
    // Arrange
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-null");
    Endpoint endpoint = new Endpoint();
    endpoint.setArch(Endpoint.PLATFORM_ARCH.x86_64);

    // Act + Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> service.launchExecutorSubprocess(inject, endpoint, agent, "token"));
    assertEquals("Unsupported null platform", exception.getMessage());
    verifyNoInteractions(assetAgentJobRepository);
  }

  @Test
  @DisplayName("launchExecutorSubprocess unsupported platform should throw")
  void given_unsupportedPlatform_should_throwRuntimeException() {
    // Arrange
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-unsupported");
    Endpoint endpoint =
        createEndpoint(Endpoint.PLATFORM_TYPE.Android, Endpoint.PLATFORM_ARCH.x86_64);

    // Act + Assert
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () -> service.launchExecutorSubprocess(inject, endpoint, agent, "token"));
    assertEquals("Unsupported platform: Android", exception.getMessage());
    verifyNoInteractions(assetAgentJobRepository);
  }

  @Test
  @DisplayName("launchBatchExecutorSubprocess should return empty list")
  void given_anyInput_should_returnEmptyList() {
    // Arrange
    Inject inject = createInjectWithDirectInjector();
    Agent agent = createAgent("agent-1");
    Set<Agent> agents = Set.of(agent);
    InjectStatus injectStatus = new InjectStatus();

    // Act
    List<Agent> result =
        service.launchBatchExecutorSubprocess(inject, agents, injectStatus, "token");

    // Assert
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  private Inject createInjectWithDirectInjector() {
    Inject inject = new Inject();
    inject.setId("inject-1");
    inject.setTenant(new Tenant(TENANT_ID));
    inject.setInjector(createInjectorCommandMap());
    return inject;
  }

  private Injector createInjectorCommandMap() {
    Injector injector = new Injector();
    injector.setId("injector-id");
    injector.setTenantId(TENANT_ID);

    Map<String, String> executorCommands = new HashMap<>();
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Windows.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "cmd-win-#{inject}-#{agent}-#{tenant}-#{token}-#{baseUrl}");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.Linux.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name(),
        "cmd-linux-#{inject}-#{agent}-#{tenant}-#{token}-#{baseUrl}");
    executorCommands.put(
        Endpoint.PLATFORM_TYPE.MacOS.name() + "." + Endpoint.PLATFORM_ARCH.arm64.name(),
        "cmd-mac-#{inject}-#{agent}-#{tenant}-#{token}-#{baseUrl}");
    injector.setExecutorCommands(executorCommands);

    return injector;
  }

  private Agent createAgent(String id) {
    Agent agent = new Agent();
    agent.setId(id);
    return agent;
  }

  private Endpoint createEndpoint(Endpoint.PLATFORM_TYPE platform, Endpoint.PLATFORM_ARCH arch) {
    Endpoint endpoint = new Endpoint();
    endpoint.setPlatform(platform);
    endpoint.setArch(arch);
    return endpoint;
  }
}
