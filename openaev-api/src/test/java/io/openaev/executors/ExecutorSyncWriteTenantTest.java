package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
import io.openaev.executors.crowdstrike.model.CrowdStrikeDevice;
import io.openaev.executors.crowdstrike.model.CrowdStrikeHostGroup;
import io.openaev.executors.crowdstrike.model.ResourcesGroups;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.model.SentinelOneAgent;
import io.openaev.executors.sentinelone.service.SentinelOneExecutorService;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.DataComputerGroup;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.executors.tanium.model.TaniumComputerGroup;
import io.openaev.executors.tanium.service.TaniumExecutorService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CrowdstrikeDeviceFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.SentinelOneDeviceFixture;
import io.openaev.utils.fixtures.TaniumDeviceFixture;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * An EDR executor of a non-default tenant writes the endpoints, agents, asset groups and source
 * tags it syncs in its own tenant, with no ambient tenant on the scheduler thread. Each creator
 * names the executor's tenant, so the default-tenant fallback of {@link TenantContext} plays no
 * part.
 *
 * <p>NOT {@code @Transactional}: each run opens its own transaction through {@link
 * TenantScopedTransaction}, as on the scheduler thread, and the rows are read back by raw JDBC,
 * keyed by the executor that wrote them, so a row written in another tenant is seen too.
 */
@WithoutTenantScope
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Executor integrations write the rows they sync in the executor's tenant")
class ExecutorSyncWriteTenantTest extends ProductionTablesIntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private ExecutorFixture executorFixture;
  @Autowired private ExecutorRepository executorRepository;
  @Autowired private EndpointService endpointService;
  @Autowired private AgentService agentService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private JdbcTemplate jdbc;

  private String tenantB;

  @BeforeAll
  void createTenant() throws Exception {
    userService.createAdminSession();
    try {
      tenantB = tenantHelper.createTenant("executor-sync-b").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
  }

  @AfterAll
  void deleteTenant() {
    tenantHelper.deleteCommittedTenants(tenantB);
    TenantContext.clearCurrentTenant();
  }

  @BeforeEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private Executor saveInTenantB(Executor executor) {
    executor.setTenantId(tenantB);
    return tenantTx.execute(TxCtx.forTenant(tenantB), () -> executorRepository.save(executor));
  }

  private void assertNoAmbientTenant() {
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  private List<String> agentTenants(Executor executor) {
    return jdbc.queryForList(
        "SELECT DISTINCT tenant_id FROM agents WHERE agent_executor = ?",
        String.class,
        executor.getId());
  }

  private List<String> endpointTenants(Executor executor) {
    return jdbc.queryForList(
        "SELECT DISTINCT a.tenant_id FROM assets a"
            + " JOIN agents ag ON ag.agent_asset = a.asset_id WHERE ag.agent_executor = ?",
        String.class,
        executor.getId());
  }

  private List<String> assetGroupTenants(Executor executor) {
    return jdbc.queryForList(
        "SELECT DISTINCT g.tenant_id FROM asset_groups g"
            + " JOIN asset_groups_assets ga ON ga.asset_group_id = g.asset_group_id"
            + " JOIN agents ag ON ag.agent_asset = ga.asset_id WHERE ag.agent_executor = ?",
        String.class,
        executor.getId());
  }

  private List<String> sourceTagTenants(Executor executor) {
    return jdbc.queryForList(
        "SELECT DISTINCT t.tenant_id FROM tags t"
            + " JOIN assets_tags at ON at.tag_id = t.tag_id"
            + " JOIN agents ag ON ag.agent_asset = at.asset_id WHERE ag.agent_executor = ?",
        String.class,
        executor.getId());
  }

  private void assertEveryRowInTenantB(Executor executor) {
    assertThat(agentTenants(executor)).containsExactly(tenantB);
    assertThat(endpointTenants(executor)).containsExactly(tenantB);
    assertThat(assetGroupTenants(executor)).containsExactly(tenantB);
    assertThat(sourceTagTenants(executor)).containsExactly(tenantB);
  }

  // The sync reuses an endpoint of the tenant found by MAC address: each test syncs a device
  // with its own MAC, so no test reads an endpoint that another test created.
  private static String uniqueMac() {
    String hex = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    StringBuilder mac = new StringBuilder();
    for (int i = 0; i < hex.length(); i += 2) {
      if (i > 0) {
        mac.append(':');
      }
      mac.append(hex, i, i + 2);
    }
    return mac.toString();
  }

  private CrowdStrikeExecutorService crowdStrikeSync(Executor executor) {
    String hostGroupId = "host-group-" + UUID.randomUUID();
    CrowdStrikeHostGroup hostGroup = new CrowdStrikeHostGroup();
    hostGroup.setId(hostGroupId);
    hostGroup.setName("crowdstrike");
    ResourcesGroups resourcesGroups = new ResourcesGroups();
    resourcesGroups.setResources(List.of(hostGroup));
    CrowdStrikeExecutorClient client = mock(CrowdStrikeExecutorClient.class);
    when(client.hostGroup(hostGroupId)).thenReturn(resourcesGroups);
    CrowdStrikeDevice device = CrowdstrikeDeviceFixture.createDefaultCrowdStrikeDevice();
    device.setMac_address(uniqueMac());
    when(client.devices(hostGroupId)).thenReturn(List.of(device));
    CrowdStrikeExecutorConfig config = mock(CrowdStrikeExecutorConfig.class);
    when(config.getHostGroup()).thenReturn(hostGroupId);
    return new CrowdStrikeExecutorService(
        executor, client, config, endpointService, agentService, assetGroupService, tenantTx);
  }

  @Nested
  @DisplayName("CrowdStrike")
  class CrowdStrike {

    @Test
    @DisplayName("given_executorInTenantB_should_writeEveryRowInTenantB")
    void given_executorInTenantB_should_writeEveryRowInTenantB() {
      // Arrange
      Executor executor = saveInTenantB(executorFixture.createCrowdstrikeExecutor());
      CrowdStrikeExecutorService service = crowdStrikeSync(executor);
      assertNoAmbientTenant();

      // Act
      service.run();

      // Assert
      assertEveryRowInTenantB(executor);
      assertNoAmbientTenant();
    }

    @Test
    @DisplayName("given_agentsSyncedInTenantB_should_cleanThemFromTheGarbageCollector")
    void given_agentsSyncedInTenantB_should_cleanThemFromTheGarbageCollector() {
      // Arrange
      Executor executor = saveInTenantB(executorFixture.createCrowdstrikeExecutor());
      crowdStrikeSync(executor).run();
      CrowdStrikeExecutorContextService contextService =
          mock(CrowdStrikeExecutorContextService.class);
      CrowdStrikeGarbageCollectorService garbageCollector =
          new CrowdStrikeGarbageCollectorService(
              mock(CrowdStrikeExecutorConfig.class),
              contextService,
              agentService,
              executor,
              tenantTx);
      assertNoAmbientTenant();

      // Act
      garbageCollector.run();

      // Assert
      ArgumentCaptor<List<CrowdStrikeAction>> actions = ArgumentCaptor.forClass(List.class);
      verify(contextService).executeActions(actions.capture());
      assertThat(actions.getValue())
          .extracting(CrowdStrikeAction::getAgentExternalReference)
          .containsExactly(
              CrowdstrikeDeviceFixture.createDefaultCrowdStrikeDevice().getDevice_id());
      assertNoAmbientTenant();
    }
  }

  @Nested
  @DisplayName("SentinelOne")
  class SentinelOne {

    @Test
    @DisplayName("given_executorInTenantB_should_writeEveryRowInTenantB")
    void given_executorInTenantB_should_writeEveryRowInTenantB() {
      // Arrange
      Executor executor = saveInTenantB(executorFixture.createSentineloneExecutor());
      SentinelOneExecutorClient client = mock(SentinelOneExecutorClient.class);
      SentinelOneAgent agent = SentinelOneDeviceFixture.createDefaultSentinelOneAgent();
      agent.getNetworkInterfaces().getFirst().setPhysical(uniqueMac());
      when(client.agents()).thenReturn(Set.of(agent));
      SentinelOneExecutorService service =
          new SentinelOneExecutorService(
              executor, client, endpointService, agentService, assetGroupService, tenantTx);
      assertNoAmbientTenant();

      // Act
      service.run();

      // Assert
      assertEveryRowInTenantB(executor);
      assertNoAmbientTenant();
    }
  }

  @Nested
  @DisplayName("Tanium")
  class Tanium {

    @Test
    @DisplayName("given_executorInTenantB_should_writeEveryRowInTenantB")
    void given_executorInTenantB_should_writeEveryRowInTenantB() {
      // Arrange
      Executor executor = saveInTenantB(executorFixture.createTaniumExecutor());
      String computerGroupId = "computer-group-" + UUID.randomUUID();
      TaniumComputerGroup computerGroup = new TaniumComputerGroup();
      computerGroup.setId(computerGroupId);
      computerGroup.setName("tanium");
      DataComputerGroup dataComputerGroup = new DataComputerGroup();
      dataComputerGroup.setComputerGroup(computerGroup);
      TaniumExecutorClient client = mock(TaniumExecutorClient.class);
      when(client.computerGroup(computerGroupId)).thenReturn(dataComputerGroup);
      NodeEndpoint endpoint = TaniumDeviceFixture.createDefaultTaniumEndpoint();
      endpoint.getNode().setMacAddresses(new String[] {uniqueMac()});
      when(client.endpoints(computerGroupId)).thenReturn(List.of(endpoint));
      TaniumExecutorConfig config = mock(TaniumExecutorConfig.class);
      when(config.getComputerGroupId()).thenReturn(computerGroupId);
      TaniumExecutorService service =
          new TaniumExecutorService(
              executor, client, config, endpointService, agentService, assetGroupService, tenantTx);
      assertNoAmbientTenant();

      // Act
      service.run();

      // Assert
      assertEveryRowInTenantB(executor);
      assertNoAmbientTenant();
    }
  }
}
