package io.openaev.rest;

import static io.openaev.rest.catalog_connector.CatalogConnectorApi.CATALOG_CONNECTOR_URI;
import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TOKEN;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createCatalogConfiguration;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.service.FileService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ConnectorInstanceFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.CatalogConnectorConfigurationComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
@DisplayName("Catalog Connector Api Integration Tests")
public class CatalogConnectorApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private CatalogConnectorConfigurationComposer catalogConfigurationComposer;
  @Autowired private FileService fileService;
  @Autowired private EntityManager entityManager;
  @Autowired private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;
  @Autowired private ConnectorInstanceConfigurationRepository configurationRepository;
  @Autowired private ConnectorInstanceConfigurationComposer configurationComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @Test
  @DisplayName(
      "Given catalog connector id should retrieve all catalog connector configurations associated")
  void givenCatalogConnectorId_should_retrieveAllConfiguration() throws Exception {
    CatalogConnectorConfiguration confDef =
        createCatalogConfiguration(
            "key-string",
            CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
            true,
            null,
            null,
            null);
    CatalogConnectorConfiguration confDef1 =
        createCatalogConfiguration(
            "key-string-01",
            CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
            true,
            null,
            null,
            null);
    CatalogConnectorConfiguration confDef2 =
        createCatalogConfiguration(
            "key-string-02",
            CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.STRING,
            true,
            null,
            null,
            null);
    CatalogConnector catalogConnector =
        catalogConnectorComposer
            .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("New Collector"))
            .withCatalogConnectorConfiguration(
                catalogConfigurationComposer.forCatalogConnectorConfiguration(confDef))
            .withCatalogConnectorConfiguration(
                catalogConfigurationComposer.forCatalogConnectorConfiguration(confDef1))
            .persist()
            .get();
    catalogConnectorComposer
        .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("New Collector 2"))
        .withCatalogConnectorConfiguration(
            catalogConfigurationComposer.forCatalogConnectorConfiguration(confDef2))
        .persist()
        .get();
    String response =
        mvc.perform(
                get(CATALOG_CONNECTOR_URI + "/" + catalogConnector.getId() + "/configurations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThatJson(response).isArray().size().isEqualTo(2);
    assertThatJson(response)
        .inPath("[*].connector_configuration_key")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("key-string", "key-string-01"));
  }

  @Test
  @DisplayName("Should retrieve all catalog connector")
  void should_retrieveAllCatalogConnector() throws Exception {
    // Arrange
    catalogConnectorComposer
        .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("Collector1"))
        .persist();
    catalogConnectorComposer
        .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("Collector2"))
        .withConnectorInstance(
            connectorInstanceComposer.forConnectorInstance(
                ConnectorInstanceFixture.createMigratedInstance()))
        .persist();

    // Act
    String response =
        mvc.perform(
                get(CATALOG_CONNECTOR_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertThatJson(response).isArray().size().isEqualTo(2);
    assertThatJson(response)
        .inPath("[*].catalog_connector_title")
        .isArray()
        .containsExactlyInAnyOrderElementsOf(List.of("Collector1", "Collector2"));
  }

  @Test
  @DisplayName(
      "Given catalog logo uploaded to platform storage should be retrievable without tenant context")
  void given_catalogLogoUploadedToPlatformStorage_should_beRetrievable() throws Exception {
    // Arrange
    String fileName = "platform-logo.png";
    fileService.uploadCatalogLogo(
        FileService.CONNECTORS_LOGO_PATH, fileName, new ByteArrayInputStream(new byte[] {1, 2, 3}));

    // Act / Assert
    mvc.perform(get("/api/images/catalog/connectors/logos/" + fileName)).andExpect(status().isOk());
  }

  @Nested
  @DisplayName("Rotate the catalog connector token")
  class RotateCatalogConnectorToken {

    private static final String ROTATE_URI = CATALOG_CONNECTOR_URI + "/token/rotate";

    /** Persists a connector instance in the current tenant, holding {@code token}. */
    private ConnectorInstanceConfiguration givenInstanceHoldingToken(String token)
        throws Exception {
      ConnectorInstanceConfiguration tokenConfiguration =
          createConnectorInstanceConfiguration(OPENAEV_KEY_TOKEN, token);
      connectorInstanceComposer
          .forConnectorInstance(createDefaultConnectorInstance())
          .withCatalogConnector(
              catalogConnectorComposer.forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer("Rotated collector")))
          .withConnectorInstanceConfiguration(
              configurationComposer.forConnectorInstanceConfiguration(tokenConfiguration))
          .persist();
      return tokenConfiguration;
    }

    private String storedToken(ConnectorInstanceConfiguration configuration) {
      return configurationRepository
          .findById(configuration.getId())
          .orElseThrow()
          .getValue()
          .asText();
    }

    @Test
    @DisplayName("Issues a new token and pushes it to the tenant's connector instances")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES},
        autoJoinDefaultTenant = true)
    void given_manageUsersCapability_should_rotateTokenAndUpdateInstances() throws Exception {
      // Arrange
      String previousToken =
          catalogConnectorPrivilegeService.getOrCreateToken(Tenant.DEFAULT_TENANT_UUID);
      ConnectorInstanceConfiguration tokenConfiguration = givenInstanceHoldingToken(previousToken);
      entityManager.flush();
      entityManager.clear();

      // Act
      mvc.perform(post(ROTATE_URI).with(csrf())).andExpect(status().isNoContent());

      // Assert
      entityManager.flush();
      entityManager.clear();
      String rotatedToken =
          catalogConnectorPrivilegeService.getOrCreateToken(Tenant.DEFAULT_TENANT_UUID);
      assertThat(rotatedToken).isNotEqualTo(previousToken);
      assertThat(storedToken(tokenConfiguration)).isEqualTo(rotatedToken);
    }

    @Test
    @DisplayName("Leaves the connector instances of another tenant untouched")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TENANT_USERS_GROUPS_AND_ROLES},
        autoJoinDefaultTenant = true)
    void given_instanceInAnotherTenant_should_keepItsToken() throws Exception {
      // Arrange
      catalogConnectorPrivilegeService.getOrCreateToken(Tenant.DEFAULT_TENANT_UUID);
      Tenant otherTenant = tenantIsolationHelper.createTenant("Other tenant");
      tenantIsolationHelper.switchToTenant(otherTenant.getId(), entityManager);
      ConnectorInstanceConfiguration otherTenantConfiguration =
          givenInstanceHoldingToken("other-tenant-token");
      tenantIsolationHelper.switchToTenant(Tenant.DEFAULT_TENANT_UUID, entityManager);

      // Act
      mvc.perform(post(ROTATE_URI).with(csrf())).andExpect(status().isNoContent());

      // Assert
      entityManager.flush();
      entityManager.clear();
      assertThat(storedToken(otherTenantConfiguration)).isEqualTo("other-tenant-token");
    }

    @Test
    @DisplayName("Rejects a user who can deploy connectors but not manage users")
    @WithMockUser(
        withCapabilities = {Capability.MANAGE_TENANT_SETTINGS},
        autoJoinDefaultTenant = true)
    void given_onlyManageTenantSettings_should_beForbidden() throws Exception {
      // Act & Assert
      mvc.perform(post(ROTATE_URI).with(csrf())).andExpect(status().isForbidden());
    }
  }
}
