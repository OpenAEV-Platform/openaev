package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.collector.CollectorApi.COLLECTOR_URI;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Collector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
public class CollectorApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private CollectorRepository collectorRepository;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private ConnectorInstancePersisted getCollectorInstance(String collectorId, String collectorName)
      throws JsonProcessingException {
    return connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance())
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(collectorName)))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration("COLLECTOR_ID", collectorId)))
        .persist()
        .get();
  }

  private Collector getCollector(String collectorName) {
    return collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(collectorName))
        .persist()
        .get();
  }

  @Nested
  @DisplayName("Retrieve collectors")
  class GetCollectors {
    @Test
    @DisplayName("Should retrieve all collectors")
    void shouldRetrieveAllCollectors() throws Exception {
      Collector collector = getCollector("CS");
      List<Collector> existingCollectors = fromIterable(collectorRepository.findAll());
      getCollectorInstance("PENDING_COLLECTOR_ID", "Pending collector");
      ConnectorInstancePersisted connectorInstanceLinkToCreatedCollector =
          getCollectorInstance(collector.getId(), collector.getName());

      String response =
          mvc.perform(
                  get(COLLECTOR_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingCollectors.size());

      assertThatJson(response)
          .inPath("[*].collector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              existingCollectors.stream().map(Collector::getId).toList());

      String path = "$[?(@.collector_id == '" + collector.getId() + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(connectorInstanceLinkToCreatedCollector.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }

    @Test
    @DisplayName(
        "Given queryParams include_next to true should retrieve all collectors and and pending collectors")
    void givenQueryParamsIncludeNextToTrue_shouldRetrieveAllCollectorsAndPendingCollectors()
        throws Exception {
      getCollector("Mitre Attack");
      List<Collector> existingCollectors = fromIterable(collectorRepository.findAll());
      String pendingCollectorId = "PENDING_COLLECTOR_ID";
      ConnectorInstancePersisted pendingCollectorInstance =
          getCollectorInstance(pendingCollectorId, "PENDING COLLECTOR");

      String response =
          mvc.perform(
                  get(COLLECTOR_URI + "?include_next=true")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingCollectors.size() + 1);

      assertThatJson(response)
          .inPath("[*].collector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              Stream.concat(
                      existingCollectors.stream().map(Collector::getId),
                      Stream.of(pendingCollectorId))
                  .toList());
      String path = "$[?(@.collector_id == '" + pendingCollectorId + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(pendingCollectorInstance.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }
  }

  @Nested
  @DisplayName("Related collectors ids")
  class GetRelatedCollectorIds {
    @Test
    @DisplayName(
        "Given collector managed by XTM Composer, should return linked connector instance ID and catalog ID")
    void givenLinkedCollector_shouldReturnInstanceAndCatalogId() throws Exception {
      Collector collector = getCollector("CS-collector");
      ConnectorInstancePersisted instance =
          getCollectorInstance(collector.getId(), collector.getName());
      String response =
          mvc.perform(
                  get(COLLECTOR_URI + "/" + collector.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(instance.getId());
      assertThatJson(response)
          .inPath("catalog_connector_id")
          .isEqualTo(instance.getCatalogConnector().getId());
    }

    @Test
    @DisplayName(
        "Given collector matching a catalog type, should return matching catalog ID without connector instance ID")
    void givenCollectorWithType_shouldReturnCatalogWithMatchingSlug() throws Exception {
      Collector collector = getCollector("cs-collector");
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer("cs-collector"))
              .persist()
              .get();

      String response =
          mvc.perform(
                  get(COLLECTOR_URI + "/" + collector.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(null);
      assertThatJson(response).inPath("catalog_connector_id").isEqualTo(catalogConnector.getId());
    }

    @Test
    @DisplayName("Given unlinked collector, should return empty catalog ID and empty instance ID")
    void givenUnlinkedCollector_shouldReturnEmptyInstanceAndCatalogId() throws Exception {
      Collector collector = getCollector("Atomic Red Team");
      String response =
          mvc.perform(
                  get(COLLECTOR_URI + "/" + collector.getId() + "/related-ids")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).inPath("connector_instance_id").isEqualTo(null);
      assertThatJson(response).inPath("catalog_connector_id").isEqualTo(null);
    }
  }
}
