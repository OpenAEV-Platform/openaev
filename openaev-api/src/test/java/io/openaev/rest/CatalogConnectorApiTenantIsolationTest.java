package io.openaev.rest;

import static io.openaev.rest.catalog_connector.CatalogConnectorApi.TENANT_CATALOG_CONNECTOR_URI;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the Phase 3b two-hop gap found while activating {@code connector_instances}:
 * CatalogConnectorApi#getCatalogConnectors/#getConnector compute {@code instance_deployed_count} by
 * reading connector_instances through CatalogConnectorService ->
 * ConnectorInstanceService#connectorInstances/#findAllByCatalogConnectorId, a repository call
 * invisible to a simple ConnectorInstanceRepository-caller grep since it goes through an
 * intermediate service. Without a TxCtx on these two handlers, once connector_instances is
 * v2-active the count silently reads zero for every tenant instead of that tenant's own count.
 *
 * <p>Seeding goes through the composers (not a raw INSERT) because the entity is still on v1 at
 * this stage of the activation: {@code TenantBaseListener} needs {@link TenantContext} set to
 * attribute {@code tenant_id} at persist time. {@code CatalogConnector} itself is platform-level
 * (no tenant_id) and is shared by both tenants' instances.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=connector_instances")
@WithMockUser(isAdmin = true)
@DisplayName("catalog-connector instance_deployed_count is scoped to the caller's tenant")
class CatalogConnectorApiTenantIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private String tenantA;
  private String tenantB;
  private String catalogConnectorId;

  @BeforeEach
  void seedTwoTenantsWithOneInstanceEachOnTheSameCatalogConnector() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("cc-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("cc-iso-b").getId();

    // TenantIsolationTestHelper#createTenant now resets the leftover onboarding scope itself
    // (see its javadoc), so no manual reset is needed here.

    CatalogConnector catalogConnectorToCreate =
        createDefaultCatalogConnectorManagedByXtmComposer("cc-iso-connector");
    catalogConnectorToCreate.setManagerSupported(false);
    CatalogConnector catalogConnector =
        catalogConnectorComposer.forCatalogConnector(catalogConnectorToCreate).persist().get();
    catalogConnectorId = catalogConnector.getId();

    seedConnectorInstance(tenantA, catalogConnector, "key-a", "value-a");
    seedConnectorInstance(tenantB, catalogConnector, "key-b", "value-b");
  }

  private void seedConnectorInstance(
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
    } finally {
      TenantContext.setCurrentTenant(previousTenantId);
    }
  }

  @Test
  @DisplayName("GET /catalog-connector under tenant A's path counts only A's own instance, not B's")
  void listUnderTenantAPathCountsOnlyOwnInstance() throws Exception {
    mvc.perform(get(TENANT_CATALOG_CONNECTOR_URI, tenantA))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$[?(@.catalog_connector_id == '"
                        + catalogConnectorId
                        + "')].instance_deployed_count")
                .value(1));
  }

  @Test
  @DisplayName("GET /catalog-connector under tenant B's path counts only B's own instance, not A's")
  void listUnderTenantBPathCountsOnlyOwnInstance() throws Exception {
    mvc.perform(get(TENANT_CATALOG_CONNECTOR_URI, tenantB))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                    "$[?(@.catalog_connector_id == '"
                        + catalogConnectorId
                        + "')].instance_deployed_count")
                .value(1));
  }

  @Test
  @DisplayName(
      "GET /catalog-connector/{id} under tenant A's path counts only A's own instance, not B's")
  void getUnderTenantAPathCountsOnlyOwnInstance() throws Exception {
    mvc.perform(get(TENANT_CATALOG_CONNECTOR_URI + "/{id}", tenantA, catalogConnectorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance_deployed_count").value(1));
  }

  @Test
  @DisplayName(
      "GET /catalog-connector/{id} under tenant B's path counts only B's own instance, not A's")
  void getUnderTenantBPathCountsOnlyOwnInstance() throws Exception {
    mvc.perform(get(TENANT_CATALOG_CONNECTOR_URI + "/{id}", tenantB, catalogConnectorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instance_deployed_count").value(1));
  }
}
