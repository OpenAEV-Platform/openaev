package io.openaev.rest;

import static io.openaev.rest.catalog_connector.CatalogConnectorApi.CATALOG_CONNECTOR_URI;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createCatalogConfiguration;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.CatalogConnectorConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
@DisplayName("Catalog Connector Api Integration Tests")
public class CatalogConnectorApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private CatalogConnectorConfigurationComposer catalogConfigurationComposer;

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
        .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("New Collector"))
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
}
