package io.openaev.rest;

import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.migration.ConfigurationMigration;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.service.connector_instances.XtmComposerEncryptionService;
import io.openaev.utils.JsonTestUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantScopeTestListener;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The connector instance create endpoint attributes the row to the write tenant of the request on
 * every route: the prefixed route, the {@code X-Tenant-Ids} route and the plain route with no
 * header. The ambient tenant is at its fallback before each request; the interceptor sets the path
 * tenant on the prefixed route only, so the other two routes run with the fallback.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Connector instance create attributes the row to the write tenant")
class ConnectorInstanceCreateAttributionTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String PREFIXED_URI = "/api/tenants/{tenantId}/connector-instances";
  private static final String PLAIN_URI = "/api/connector-instances";

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private EncryptionFactory encryptionFactory;
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private XtmComposerEncryptionService xtmComposerEncryptionService;
  @MockitoBean private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;

  private String tenantB;
  private String catalogConnectorId;

  @BeforeEach
  void seedTenantBAndCatalogConnector() throws Exception {
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
    when(xtmComposerEncryptionService.encrypt(any())).thenReturn("fake-encrypted-value");
    when(catalogConnectorPrivilegeService.getOrCreateToken(any(TxCtx.class), anyString()))
        .thenReturn("fake-token-value");

    tenantB = tenantHelper.createTenantWithCurrentUser("explicit-instance-b").getId();
    CatalogConnector catalogConnector =
        createDefaultCatalogConnectorManagedByXtmComposer("explicit-instance-connector");
    catalogConnector.setManagerSupported(false);
    catalogConnectorId =
        catalogConnectorComposer.forCatalogConnector(catalogConnector).persist().get().getId();

    // Onboarding leaves the new tenant on the test thread and in the scope of the transaction; a
    // request carries neither, so both are reset and the fallback is asserted.
    DefaultTenantScopeTestListener.setAmbientScope(entityManager, "");
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("given_prefixedRoute_should_attributeInstanceToPathTenant")
  void given_prefixedRoute_should_attributeInstanceToPathTenant() throws Exception {
    // Act
    String instanceId = createInstance(post(PREFIXED_URI, tenantB));

    // Assert
    assertThat(rowTenant(instanceId, tenantB)).isEqualTo(tenantB);
  }

  @Test
  @DisplayName("given_headerRoute_should_attributeInstanceToHeaderTenant")
  void given_headerRoute_should_attributeInstanceToHeaderTenant() throws Exception {
    // Act
    String instanceId = createInstance(post(PLAIN_URI).header("X-Tenant-Ids", tenantB));

    // Assert
    assertThat(rowTenant(instanceId, tenantB)).isEqualTo(tenantB);
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("given_plainRouteWithoutHeader_should_attributeInstanceToDefaultTenant")
  void given_plainRouteWithoutHeader_should_attributeInstanceToDefaultTenant() throws Exception {
    // Arrange
    tenantHelper.detachCurrentUserFromTenant(tenantB);

    // Act
    String instanceId = createInstance(post(PLAIN_URI));

    // Assert
    assertThat(rowTenant(instanceId, Tenant.DEFAULT_TENANT_UUID))
        .isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("given_propertiesMigrationOfTenantB_should_attributeInstanceToTenantB")
  void given_propertiesMigrationOfTenantB_should_attributeInstanceToTenantB() throws Exception {
    // Arrange
    String factoryClassName = "ConnectorInstanceCreateAttributionTest_Factory";
    CatalogConnector connector =
        catalogConnectorComposer
            .forCatalogConnector(
                CatalogConnectorFixture.createCatalogConnectorWithClassName(factoryClassName))
            .persist()
            .get();
    BaseIntegrationConfiguration configuration = new BaseIntegrationConfiguration() {};
    configuration.setEnable(true);
    ConfigurationMigration migration =
        new ConfigurationMigration(
            configuration,
            factoryClassName,
            catalogConnectorService,
            connectorInstanceService,
            encryptionFactory) {};

    // Act
    migration.migrate(tenantB);

    // Assert
    entityManager.flush();
    DefaultTenantScopeTestListener.setAmbientScope(entityManager, tenantB);
    String instanceId =
        connectorInstanceService.findAllByCatalogConnector(connector).getFirst().getId();
    assertThat(rowTenant(instanceId, tenantB)).isEqualTo(tenantB);
  }

  private String createInstance(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    input.setCatalogConnectorId(catalogConnectorId);
    String response =
        mvc.perform(
                request
                    .content(JsonTestUtils.asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.connector_instance_id");
  }

  private String rowTenant(String instanceId, String scopeTenant) {
    entityManager.flush();
    DefaultTenantScopeTestListener.setAmbientScope(entityManager, scopeTenant);
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT tenant_id FROM connector_instances WHERE connector_instance_id = ?1")
            .setParameter(1, instanceId)
            .getSingleResult();
  }
}
