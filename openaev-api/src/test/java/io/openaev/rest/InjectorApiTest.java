package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.injector.InjectorApi.INJECT0R_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultInjector;
import static io.openaev.utils.fixtures.InjectorFixture.createInjector;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Injector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
public class InjectorApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private EntityManager em;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private MockMultipartFile buildInputPart(InjectorCreateInput input) {
    return new MockMultipartFile(
        "input", "input.json", MediaType.APPLICATION_JSON_VALUE, asJsonString(input).getBytes());
  }

  private MockMultipartFile buildEmptyIconPart() {
    return new MockMultipartFile("icon", "icon.png", MediaType.IMAGE_PNG_VALUE, new byte[0]);
  }

  private InjectorContractInput buildContractInput(String contractId) {
    InjectorContractInput contract = new InjectorContractInput();
    contract.setId(contractId);
    contract.setLabels(Map.of("en", "Test Contract"));
    contract.setContent("{\"fields\":[]}");
    return contract;
  }

  private ConnectorInstancePersisted getInjectorInstance(String injectorId, String injectorName)
      throws JsonProcessingException {
    return connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance())
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(
                    injectorName, ConnectorType.INJECTOR)))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration("INJECTOR_ID", injectorId)))
        .persist()
        .get();
  }

  private Injector getInjector(String injectorName) {
    Injector injector = createDefaultInjector(injectorName);
    return injectorRepository.save(injector);
  }

  @Nested
  @DisplayName("Retrieve injectors")
  class GetInjectors {
    @Test
    @DisplayName("Should retrieve all injectors")
    void shouldRetrieveAllInjectors() throws Exception {
      Injector injector = getInjector("nuclei");
      List<Injector> existingInjectors = fromIterable(injectorRepository.findAll());
      getInjectorInstance("PENDING_INJECTOR_ID", "Pending injector");
      ConnectorInstancePersisted connectorInstanceLinkToCreatedInjector =
          getInjectorInstance(injector.getId(), injector.getName());

      String response =
          mvc.perform(
                  get(INJECT0R_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingInjectors.size());

      assertThatJson(response)
          .inPath("[*].injector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              existingInjectors.stream().map(Injector::getId).toList());

      String path = "$[?(@.injector_id == '" + injector.getId() + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(connectorInstanceLinkToCreatedInjector.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }

    @Test
    @DisplayName(
        "Given queryParams include_next to true should retrieve all injectors and and pending injectors")
    void givenQueryParamsIncludeNextToTrue_shouldRetrieveAllInjectorsAndPendingInjectors()
        throws Exception {
      getInjector("Mitre Attack");
      List<Injector> existingInjectors = fromIterable(injectorRepository.findAll());
      String pendingInjectorId = "PENDING_INJECTOR_ID";
      ConnectorInstancePersisted pendingInjectorInstance =
          getInjectorInstance(pendingInjectorId, "PENDING INJECTOR");

      String response =
          mvc.perform(
                  get(INJECT0R_URI + "?include_next=true")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingInjectors.size() + 1);

      assertThatJson(response)
          .inPath("[*].injector_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              Stream.concat(
                      existingInjectors.stream().map(Injector::getId), Stream.of(pendingInjectorId))
                  .toList());
      String path = "$[?(@.injector_id == '" + pendingInjectorId + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(pendingInjectorInstance.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }
  }

  @Nested
  @DisplayName("Related injectors ids")
  class GetRelatedInjectorIds {
    @Test
    @DisplayName(
        "Given injector managed by XTM Composer, should return linked connector instance ID and catalog ID")
    void givenLinkedInjector_shouldReturnInstanceAndCatalogId() throws Exception {
      Injector injector = getInjector("nmap");
      ConnectorInstancePersisted instance =
          getInjectorInstance(injector.getId(), injector.getName());
      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + injector.getId() + "/related-ids")
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
        "Given injector matching a catalog type, should return matching catalog ID without connector instance ID")
    void givenInjectorWithType_shouldReturnCatalogWithMatchingSlug() throws Exception {
      Injector injector = getInjector("nmap-injector");
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(
                  createDefaultCatalogConnectorManagedByXtmComposer("nmap-injector"))
              .persist()
              .get();

      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + injector.getId() + "/related-ids")
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
    @DisplayName("Given unlinked injector, should return empty catalog ID and empty instance ID")
    void givenUnlinkedInjector_shouldReturnEmptyInstanceAndCatalogId() throws Exception {
      Injector injector = getInjector("http-query-injector");
      String response =
          mvc.perform(
                  get(INJECT0R_URI + "/" + injector.getId() + "/related-ids")
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

  @Nested
  @DisplayName("Register external injector")
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_SETTINGS})
  class RegisterExternalInjector {

    @Test
    @DisplayName(
        "Should register a new external injector with contracts and return RabbitMQ connection info")
    void shouldRegisterNewExternalInjectorWithContracts() throws Exception {
      // -- ARRANGE --
      String injectorId = "ext-injector-id";
      String injectorType = "openaev_ext_type";
      String contractId = "ext-contract-1";

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(injectorId);
      input.setName("External Injector");
      input.setType(injectorType);
      input.setCategory("attack");
      input.setCustomContracts(false);
      input.setPayloads(false);
      input.setExecutorCommands(Map.of("linux", "bash -c '#{command}'"));
      input.setExecutorClearCommands(Map.of("linux", "bash -c '#{clear}'"));
      input.setContracts(List.of(buildContractInput(contractId)));

      // -- ACT --
      String response =
          mvc.perform(
                  multipart(INJECT0R_URI)
                      .file(buildInputPart(input))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThatJson(response).inPath("connection").isNotNull();
      assertThatJson(response).inPath("connection.host").isNotNull();
      assertThatJson(response).inPath("connection.port").isNotNull();
      assertThatJson(response).inPath("listen").isString().contains("_injector_" + injectorType);

      Optional<Injector> persisted = injectorRepository.findById(injectorId);
      assertThat(persisted).isPresent();
      assertThat(persisted.get().isExternal()).isTrue();
      assertThat(persisted.get().getName()).isEqualTo("External Injector");
      assertThat(persisted.get().getType()).isEqualTo(injectorType);
      assertThat(persisted.get().getCategory()).isEqualTo("attack");
      assertThat(persisted.get().getExecutorCommands())
          .containsEntry("linux", "bash -c '#{command}'");

      List<InjectorContract> contracts =
          injectorContractRepository.findByInjectorsContaining(persisted.get());
      assertThat(contracts).hasSize(1);
      assertThat(contracts.getFirst().getId()).isEqualTo(contractId);
    }

    @Test
    @DisplayName("Should update an existing external injector when re-registering with the same ID")
    void shouldUpdateExistingExternalInjectorOnReRegistration() throws Exception {
      // -- ARRANGE --
      String injectorId = "upsert-injector-id";
      String injectorType = "openaev_upsert_type";

      InjectorCreateInput initialInput = new InjectorCreateInput();
      initialInput.setId(injectorId);
      initialInput.setName("Initial Name");
      initialInput.setType(injectorType);
      initialInput.setCategory("initial-category");
      initialInput.setContracts(List.of(buildContractInput("upsert-contract-1")));

      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(initialInput))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      InjectorCreateInput updateInput = new InjectorCreateInput();
      updateInput.setId(injectorId);
      updateInput.setName("Updated Name");
      updateInput.setType(injectorType);
      updateInput.setCategory("updated-category");
      updateInput.setContracts(List.of(buildContractInput("upsert-contract-1")));

      // -- ACT --
      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(updateInput))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Optional<Injector> persisted = injectorRepository.findById(injectorId);
      assertThat(persisted).isPresent();
      assertThat(persisted.get().getName()).isEqualTo("Updated Name");
      assertThat(persisted.get().getCategory()).isEqualTo("updated-category");
      assertThat(persisted.get().isExternal()).isTrue();
    }

    @Test
    @DisplayName(
        "Should register an external injector without executor commands when none are provided")
    void shouldRegisterExternalInjectorWithoutExecutorCommands() throws Exception {
      // -- ARRANGE --
      String injectorId = "no-commands-injector";
      String injectorType = "openaev_no_cmds";

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(injectorId);
      input.setName("No Commands Injector");
      input.setType(injectorType);
      input.setContracts(List.of(buildContractInput("no-cmds-contract")));

      // -- ACT --
      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Optional<Injector> persisted = injectorRepository.findById(injectorId);
      assertThat(persisted).isPresent();
      assertThat(persisted.get().isExternal()).isTrue();
      assertThat(persisted.get().getExecutorCommands()).isNullOrEmpty();
      assertThat(persisted.get().getExecutorClearCommands()).isNullOrEmpty();
    }

    @Test
    @DisplayName(
        "Should delete dummy injector and reassign its contracts to the new injector on registration")
    void shouldDeleteDummyInjectorAndReassignContracts() throws Exception {
      // -- ARRANGE --
      String injectorType = "openaev_dummy_test";
      String dummyId = injectorType + "_dummy";
      String dummyType = injectorType + "_dummy";

      // Create a dummy injector (simulating what createOrGetDummyInjector does)
      Injector dummyInjector = createInjector(dummyId, "Dummy " + injectorType, dummyType);
      injectorRepository.save(dummyInjector);

      // Create an injector contract linked to the dummy injector
      InjectorContract dummyContract = InjectorContractFixture.createDefaultInjectorContract();
      dummyContract.getInjectors().clear();
      dummyContract.addInjector(dummyInjector);
      em.persist(dummyContract);
      injectorContractRepository.save(dummyContract);
      dummyInjector.getContracts().add(dummyContract);
      injectorRepository.save(dummyInjector);
      em.flush();

      String dummyContractId = dummyContract.getId();
      String realInjectorId = "real-injector-for-dummy";

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(realInjectorId);
      input.setName("Real Injector");
      input.setType(injectorType);
      input.setContracts(List.of(buildContractInput("real-contract-1")));

      // -- ACT --
      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      assertThat(injectorRepository.findById(dummyId)).isEmpty();

      Optional<Injector> realInjector = injectorRepository.findById(realInjectorId);
      assertThat(realInjector).isPresent();
      assertThat(realInjector.get().isExternal()).isTrue();

      Optional<InjectorContract> reassignedContract =
          injectorContractRepository.findById(dummyContractId);
      assertThat(reassignedContract).isPresent();
      assertThat(reassignedContract.get().getInjectors())
          .extracting(Injector::getId)
          .contains(realInjectorId)
          .doesNotContain(dummyId);
    }
  }
}
