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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.service.FileService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Collector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
public class CollectorApiTest extends IntegrationTest {

  private static final String TENANT_COLLECTOR_URI = "/api/tenants/{tenantId}/collectors";

  @Autowired private MockMvc mvc;

  @Autowired private CollectorRepository collectorRepository;
  @Autowired private FileService fileService;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private CollectorTypeComposer collectorTypeComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

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
                      .with(csrf())
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
        "Given an instance-configured name, should expose it in the output without dirtying the collector entity")
    void givenInstanceConfiguredName_shouldNotDirtyCollectorEntity() throws Exception {
      Collector collector = getCollector("Registered name");
      connectorInstanceComposer
          .forConnectorInstance(createDefaultConnectorInstance())
          .withCatalogConnector(
              catalogConnectorComposer.forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer("Configured collector")))
          .withConnectorInstanceConfiguration(
              connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                  createConnectorInstanceConfiguration("COLLECTOR_ID", collector.getId())))
          .withConnectorInstanceConfiguration(
              connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                  createConnectorInstanceConfiguration("COLLECTOR_NAME", "Configured name")))
          .persist();

      String response =
          mvc.perform(
                  get(COLLECTOR_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String path = "$[?(@.collector_id == '" + collector.getId() + "')]";
      assertThatJson(response)
          .inPath(path + ".collector_name")
          .isArray()
          .containsExactly("Configured name");

      // A read path that dirties the managed entity would flush the configured name to the DB
      // here and silently overwrite the registered name (issue #7092: in prod the flush happens
      // at response commit and turns the GET into a 500 when the row is gone).
      entityManager.flush();
      entityManager.clear();
      Collector reloaded =
          fromIterable(collectorRepository.findAll()).stream()
              .filter(c -> collector.getId().equals(c.getId()))
              .findFirst()
              .orElseThrow();
      assertThat(reloaded.getName()).isEqualTo("Registered name");
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
                      .with(csrf())
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
  @DisplayName("Retrieve collector by id")
  class GetCollectorById {

    private String performSingleGet(String collectorId) throws Exception {
      return mvc.perform(
              get(tenantUri(TENANT_COLLECTOR_URI + "/" + collectorId))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    @DisplayName("Should return the collector output with its linked connector instance")
    void shouldReturnCollectorOutputWithLinkedInstance() throws Exception {
      Collector collector = getCollector("single-get-linked");
      ConnectorInstancePersisted instance =
          getCollectorInstance(collector.getId(), collector.getName());

      String response = performSingleGet(collector.getId());

      assertThatJson(response).inPath("collector_id").isEqualTo(collector.getId());
      assertThatJson(response).inPath("is_verified").isEqualTo(true);
      assertThatJson(response)
          .inPath("connector_instance.connector_instance_id")
          .isEqualTo(instance.getId());
      assertThatJson(response)
          .inPath("catalog.catalog_connector_id")
          .isEqualTo(instance.getCatalogConnector().getId());
    }

    @Test
    @DisplayName("Should return the collector output as unverified when no instance is linked")
    void shouldReturnCollectorOutputWithoutInstance() throws Exception {
      Collector collector = getCollector("single-get-unlinked");

      String response = performSingleGet(collector.getId());

      assertThatJson(response).inPath("collector_id").isEqualTo(collector.getId());
      assertThatJson(response).inPath("is_verified").isEqualTo(false);
    }

    @Test
    @DisplayName("Should return 404 for an unknown collector id")
    void shouldReturn404ForUnknownCollector() throws Exception {
      mvc.perform(
              get(tenantUri(TENANT_COLLECTOR_URI + "/unknown-collector-id"))
                  .with(csrf())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should not resolve a connector instance belonging to another tenant")
    void shouldNotResolveInstanceFromAnotherTenant() throws Exception {
      Collector collector = getCollector("single-get-cross-tenant");
      ConnectorInstancePersisted instance =
          getCollectorInstance(collector.getId(), collector.getName());
      // Resolve the tenant-scoped URI before creating the second tenant: tenantUri() picks the
      // user's first tenant, which is no longer deterministic once the user belongs to two.
      String uri = tenantUri(TENANT_COLLECTOR_URI + "/" + collector.getId());
      Tenant otherTenant = tenantHelper.createTenantWithCurrentUser("collector-single-get-b");

      // Move the instance to another tenant while keeping its COLLECTOR_ID pointing at the
      // current tenant's collector: the instance lookup is a native query bypassing the Hibernate
      // tenant filter, so it must not resolve a foreign tenant's instance.
      entityManager.flush();
      entityManager
          .createNativeQuery(
              "UPDATE connector_instances SET tenant_id = :tenant"
                  + " WHERE connector_instance_id = :id")
          .setParameter("tenant", otherTenant.getId())
          .setParameter("id", instance.getId())
          .executeUpdate();

      String response =
          mvc.perform(
                  get(uri)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(collector.getId());
      assertThatJson(response).inPath("is_verified").isEqualTo(false);
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
                  get(tenantUri(TENANT_COLLECTOR_URI + "/" + collector.getId() + "/related-ids"))
                      .with(csrf())
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
                  get(tenantUri(TENANT_COLLECTOR_URI + "/" + collector.getId() + "/related-ids"))
                      .with(csrf())
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
                  get(tenantUri(TENANT_COLLECTOR_URI + "/" + collector.getId() + "/related-ids"))
                      .with(csrf())
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
  @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
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
                  multipart(tenantUri(TENANT_COLLECTOR_URI))
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_type").isEqualTo(input.getType());
      assertThatJson(response).inPath("collector_name").isEqualTo(input.getName());
      assertThatJson(response).inPath("collector_external").isEqualTo(true);
      assertThatJson(response).inPath("collector_period").isEqualTo(input.getPeriod());

      Optional<Collector> persisted =
          collectorRepository.findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant());
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
                  multipart(tenantUri(TENANT_COLLECTOR_URI))
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_security_platform.asset_id").isEqualTo(sp.getId());

      Optional<Collector> persisted =
          collectorRepository.findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant());
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
                  multipart(tenantUri(TENANT_COLLECTOR_URI))
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
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
      Collector persisted =
          collectorRepository
              .findByIdAndTenantId(existing.getId(), TenantContext.getCurrentTenant())
              .orElseThrow();
      java.time.Instant originalUpdatedAt = persisted.getUpdatedAt();

      CollectorCreateInput input = new CollectorCreateInput();
      input.setId(existing.getId());
      input.setType(existing.getType());
      input.setName("Re-registered");
      input.setPeriod(0);

      mvc.perform(
              multipart(tenantUri(TENANT_COLLECTOR_URI))
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      Collector updated =
          collectorRepository
              .findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant())
              .orElseThrow();
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
                  multipart(tenantUri(TENANT_COLLECTOR_URI))
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).inPath("collector_id").isEqualTo(input.getId());
      assertThatJson(response).inPath("collector_security_platform").isEqualTo(null);

      Optional<Collector> persisted =
          collectorRepository.findByIdAndTenantId(input.getId(), TenantContext.getCurrentTenant());
      assertThat(persisted).isPresent();
      assertThat(persisted.get().getSecurityPlatform()).isNull();
    }
  }

  // Tenant isolation is tested in CollectorHttpIsolationTest (with @TestPropertySource activating
  // the table). @TestPropertySource cannot be applied to nested classes.

  @Nested
  @DisplayName("Collector image")
  class CollectorImage {
    @Test
    @DisplayName("Given existing collector image should return image by type")
    void given_existingCollectorImage_should_returnImageByType() throws Exception {
      // -- Arrange --
      String collectorType = "test-collector-type";
      fileService.uploadStream(
          FileService.COLLECTORS_IMAGES_BASE_PATH,
          collectorType + FileService.EXT_PNG,
          new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}));

      // -- Act / Assert --
      mvc.perform(get(COLLECTOR_URI + "/" + collectorType + "/image")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given existing collector image should return image by id")
    void given_existingCollectorImage_should_returnImageById() throws Exception {
      // -- Arrange --
      Collector collector = getCollector("test-collector-by-id");
      fileService.uploadStream(
          FileService.COLLECTORS_IMAGES_BASE_PATH,
          collector.getType() + FileService.EXT_PNG,
          new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}));

      // -- Act / Assert --
      mvc.perform(get(tenantUri(TENANT_COLLECTOR_URI + "/id/" + collector.getId() + "/image")))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given missing collector image should return 404")
    void given_missingCollectorImage_should_return404() throws Exception {
      // -- Act / Assert --
      mvc.perform(get(COLLECTOR_URI + "/nonexistent-type/image")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given unknown collector id should return 404")
    void given_unknownCollectorId_should_return404() throws Exception {
      // -- Act / Assert --
      mvc.perform(get(tenantUri(TENANT_COLLECTOR_URI + "/id/nonexistent-id/image")))
          .andExpect(status().isNotFound());
    }
  }
}
