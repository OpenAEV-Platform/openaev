package io.openaev.rest;

import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.connector_instance.dto.UpdateConnectorInstanceRequestedStatus;
import io.openaev.utils.JsonTestUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code connector_instances} activated, the tenant scope set from the
 * URL path isolates the table through the real {@link
 * io.openaev.rest.connector_instance.ConnectorInstanceApi} endpoints.
 *
 * <p>Seeding goes through the composers (not a raw {@code INSERT}) because the entity is still on
 * v1 at this stage of the activation: {@code TenantBaseListener} needs {@link TenantContext} set to
 * attribute {@code tenant_id} at persist time, exactly like {@code
 * ConnectorInstanceApiTest.TenantIsolation} already does. {@link CatalogConnector} itself is
 * platform-level (not tenant-scoped) and is shared by both tenants' instances.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=connector_instances")
@WithMockUser(isAdmin = true)
@DisplayName("connector_instances read and write isolation through the real HTTP endpoint")
class ConnectorInstanceHttpIsolationTest extends IntegrationTest {

  private static final String CONNECTOR_INSTANCE_BY_ID =
      "/api/tenants/{tenantId}/connector-instances/{connectorInstanceId}";
  private static final String CONNECTOR_INSTANCE_CONFIGURATIONS =
      "/api/tenants/{tenantId}/connector-instances/{connectorInstanceId}/configurations";
  private static final String CONNECTOR_INSTANCE_LOGS_SEARCH =
      "/api/tenants/{tenantId}/connector-instances/{connectorInstanceId}/logs/search";
  private static final String CONNECTOR_INSTANCE_REQUESTED_STATUS =
      "/api/tenants/{tenantId}/connector-instances/{connectorInstanceId}/requested-status";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String instanceA;
  private String instanceB;
  private String catalogConnectorId;

  @BeforeEach
  void seedTwoTenantsWithOneInstanceEach() throws Exception {
    // updateConnectorInstanceConfigurations checks the EE license unconditionally, regardless of
    // tenant; stub it so the tenant-scoping assertions below are not masked by an unrelated 403.
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);

    tenantA = tenantHelper.createTenantWithCurrentUser("ci-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ci-http-iso-b").getId();

    // Each tenant's onboarding self-scopes this shared test transaction to the tenant just
    // created (ManagerFactory#createDependencyForTenant -> ManagerCreator#createManager, see its
    // javadoc): after seeding both tenants above, the ambient scope is left pinned to tenant B.
    // Reset it so the actual test method's own request sets it fresh to whichever tenant path it
    // targets, instead of tripping the nesting guard against this leftover onboarding scope.
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', '', true)")
        .getSingleResult();

    // CatalogConnector is platform-level (no tenant_id): one row shared by both tenants' instances.
    CatalogConnector catalogConnectorToCreate =
        createDefaultCatalogConnectorManagedByXtmComposer("ci-http-iso-connector");
    // Avoid the XTM Composer reachability check on write endpoints under test.
    catalogConnectorToCreate.setManagerSupported(false);
    CatalogConnector catalogConnector =
        catalogConnectorComposer.forCatalogConnector(catalogConnectorToCreate).persist().get();
    catalogConnectorId = catalogConnector.getId();

    instanceA = seedConnectorInstance(tenantA, catalogConnector, "key-a", "value-a");
    instanceB = seedConnectorInstance(tenantB, catalogConnector, "key-b", "value-b");
  }

  private String seedConnectorInstance(
      String tenantId, CatalogConnector catalogConnector, String key, String value)
      throws Exception {
    String previousTenantId = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenantId);
    try {
      ConnectorInstancePersisted instance =
          connectorInstanceComposer
              .forConnectorInstance(createDefaultConnectorInstance())
              .withCatalogConnector(catalogConnectorComposer.forCatalogConnector(catalogConnector))
              .withConnectorInstanceConfiguration(
                  connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                      createConnectorInstanceConfiguration(key, value)))
              .persist()
              .get();
      entityManager.flush();
      return instance.getId();
    } finally {
      TenantContext.setCurrentTenant(previousTenantId);
    }
  }

  @Test
  @DisplayName("under tenant A's path: A's connector instance is visible, B's is hidden")
  void underTenantAPathReadOwnInstance() throws Exception {
    mvc.perform(get(CONNECTOR_INSTANCE_BY_ID, tenantA, instanceA)).andExpect(status().isOk());
    mvc.perform(get(CONNECTOR_INSTANCE_BY_ID, tenantA, instanceB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant B's path: B's connector instance is visible, A's is hidden")
  void underTenantBPathReadOwnInstance() throws Exception {
    mvc.perform(get(CONNECTOR_INSTANCE_BY_ID, tenantB, instanceB)).andExpect(status().isOk());
    mvc.perform(get(CONNECTOR_INSTANCE_BY_ID, tenantB, instanceA)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: A's configurations are visible, B's are hidden")
  void underTenantAPathReadOwnConfigurations() throws Exception {
    mvc.perform(get(CONNECTOR_INSTANCE_CONFIGURATIONS, tenantA, instanceA))
        .andExpect(status().isOk());
    mvc.perform(get(CONNECTOR_INSTANCE_CONFIGURATIONS, tenantA, instanceB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: A can search its own instance's logs")
  void underTenantAPathSearchOwnLogs() throws Exception {
    String body = JsonTestUtils.asJsonString(PaginationFixture.getDefault().build());
    mvc.perform(
            post(CONNECTOR_INSTANCE_LOGS_SEARCH, tenantA, instanceA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("under tenant A's path: searching B's instance's logs is not found")
  void underTenantAPathSearchOfBInstanceLogsIsBlocked() throws Exception {
    String body = JsonTestUtils.asJsonString(PaginationFixture.getDefault().build());
    mvc.perform(
            post(CONNECTOR_INSTANCE_LOGS_SEARCH, tenantA, instanceB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own instance's requested status")
  void underTenantAPathUpdateOwnRequestedStatus() throws Exception {
    UpdateConnectorInstanceRequestedStatus input = new UpdateConnectorInstanceRequestedStatus();
    input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    mvc.perform(
            put(CONNECTOR_INSTANCE_REQUESTED_STATUS, tenantA, instanceA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "under tenant A's path: updating B's instance's requested status is not found and leaves it"
          + " untouched")
  void underTenantAPathUpdateOfBInstanceRequestedStatusIsBlocked() throws Exception {
    UpdateConnectorInstanceRequestedStatus input = new UpdateConnectorInstanceRequestedStatus();
    input.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    mvc.perform(
            put(CONNECTOR_INSTANCE_REQUESTED_STATUS, tenantA, instanceB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own instance's configurations")
  void underTenantAPathUpdateOwnConfigurations() throws Exception {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    input.setCatalogConnectorId(catalogConnectorId);
    mvc.perform(
            put(CONNECTOR_INSTANCE_CONFIGURATIONS, tenantA, instanceA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "under tenant A's path: updating B's instance's configurations is not found and leaves it"
          + " untouched")
  void underTenantAPathUpdateOfBInstanceConfigurationsIsBlocked() throws Exception {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    input.setCatalogConnectorId(catalogConnectorId);
    mvc.perform(
            put(CONNECTOR_INSTANCE_CONFIGURATIONS, tenantA, instanceB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonTestUtils.asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }
}
