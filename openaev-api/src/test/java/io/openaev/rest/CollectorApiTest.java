package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.collector.CollectorApi.COLLECTOR_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private CollectorTypeComposer collectorTypeComposer;

  private MockMultipartFile buildInputPart(CollectorCreateInput input) {
    return new MockMultipartFile(
        "input", "input.json", MediaType.APPLICATION_JSON_VALUE, asJsonString(input).getBytes());
  }

  private MockMultipartFile buildEmptyIconPart() {
    return new MockMultipartFile("icon", "icon.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);
  }

  private SecurityPlatform getSecurityPlatform(String name) {
    return securityPlatformComposer
        .forSecurityPlatform(SecurityPlatformFixture.createDefault(name, "EDR"))
        .persist()
        .get();
  }

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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
    }
  }

  @Nested
  @DisplayName("Register collector")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_SETTINGS})
  class RegisterCollector {

    @Test
    @DisplayName(
        "Should register an external collector and persist it with external flag and period")
    void shouldRegisterExternalCollector() throws Exception {
      CollectorCreateInput input = new CollectorCreateInput();
      input.setId("ext-collector-id");
      input.setType("openaev_ext_type");
      input.setName("External Collector");
      input.setPeriod(120);

      String response =
          mvc.perform(
                  multipart(COLLECTOR_URI)
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_type").isEqualTo(input.getType());
      assertThatJson(response).inPath("collector_name").isEqualTo(input.getName());
      assertThatJson(response).inPath("collector_external").isEqualTo(true);
      assertThatJson(response).inPath("collector_period").isEqualTo(input.getPeriod());

      Optional<Collector> persisted = collectorRepository.findById(input.getId());
      assertThat(persisted).isPresent();
      assertThat(persisted.get().isExternal()).isTrue();
      assertThat(persisted.get().getPeriod()).isEqualTo(input.getPeriod());
    }

    @Test
    @DisplayName("Should register an external collector with a security platform")
    void shouldRegisterExternalCollectorWithSecurityPlatform() throws Exception {
      SecurityPlatform sp = getSecurityPlatform("CrowdStrike EDR");

      CollectorCreateInput input = new CollectorCreateInput();
      input.setId("sp-collector-id");
      input.setType("openaev_cs");
      input.setName("CS Collector");
      input.setPeriod(60);
      input.setSecurityPlatform(sp.getId());

      String response =
          mvc.perform(
                  multipart(COLLECTOR_URI)
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_security_platform.asset_id").isEqualTo(sp.getId());

      Optional<Collector> persisted = collectorRepository.findById(input.getId());
      assertThat(persisted).isPresent();
      assertThat(persisted.get().getSecurityPlatform()).isNotNull();
      assertThat(persisted.get().getSecurityPlatform().getId()).isEqualTo(sp.getId());
    }

    @Test
    @DisplayName(
        "Should update an existing collector and set security platform when re-registering")
    void shouldUpdateExistingCollectorWithSecurityPlatform() throws Exception {
      Collector existing = getCollector("existing-type");
      SecurityPlatform sp = getSecurityPlatform("Splunk SIEM");

      CollectorCreateInput input = new CollectorCreateInput();
      input.setId(existing.getId());
      input.setType(existing.getType());
      input.setName("Updated Name");
      input.setPeriod(30);
      input.setSecurityPlatform(sp.getId());

      String response =
          mvc.perform(
                  multipart(COLLECTOR_URI)
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_name").isEqualTo(input.getName());
      assertThatJson(response).inPath("collector_external").isEqualTo(true);
      assertThatJson(response).inPath("collector_security_platform.asset_id").isEqualTo(sp.getId());
    }

    @Test
    @DisplayName("Should set updatedAt when re-registering an external collector with existing id")
    void shouldSetUpdatedAtWhenReRegisteringExternalCollector() throws Exception {
      Collector existing = getCollector("reregister-type");
      Collector persisted = collectorRepository.findById(existing.getId()).orElseThrow();
      java.time.Instant originalUpdatedAt = persisted.getUpdatedAt();

      CollectorCreateInput input = new CollectorCreateInput();
      input.setId(existing.getId());
      input.setType(existing.getType());
      input.setName("Re-registered");
      input.setPeriod(0);

      mvc.perform(
              multipart(COLLECTOR_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      Collector updated = collectorRepository.findById(input.getId()).orElseThrow();
      assertThat(updated.isExternal()).isTrue();
      assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName(
        "Should register a collector without security platform when securityPlatform is null")
    void shouldRegisterCollectorWithoutSecurityPlatform() throws Exception {
      CollectorCreateInput input = new CollectorCreateInput();
      input.setId("no-sp-collector");
      input.setType("openaev_no_sp");
      input.setName("No SP Collector");
      input.setPeriod(0);

      String response =
          mvc.perform(
                  multipart(COLLECTOR_URI)
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_security_platform").isEqualTo(null);

      Optional<Collector> persisted = collectorRepository.findById(input.getId());
      assertThat(persisted).isPresent();
      assertThat(persisted.get().getSecurityPlatform()).isNull();
    }
  }
}
