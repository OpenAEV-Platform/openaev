package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.injector.InjectorApi.INJECT0R_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultInjector;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultInjectorCreateInput;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import io.openaev.service.FileService;
import io.openaev.utils.AgentUtils;
import io.openaev.utils.HashUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Injector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
public class InjectorApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager em;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private FileService fileService;

  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
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
      assertThatJson(response).inPath("connector_registered").isEqualTo(true);
    }
  }

  @Nested
  @DisplayName("Register external injector")
  @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
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
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThatJson(response).inPath("connection").isNotNull();
      assertThatJson(response).inPath("connection.host").isNotNull();
      assertThatJson(response).inPath("connection.port").isNotNull();
      assertThatJson(response).inPath("listen").isString().contains("_injector_" + injectorId);

      Optional<Injector> persisted =
          injectorRepository.findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant());
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
    @DisplayName(
        "Should register external injector and default missing argumentType to text")
    void shouldRegisterExternalInjectorWithoutPayloadArgumentTypeInContractContent() throws Exception {
      String injectorId = "ext-injector-without-argument-type";
      String injectorType = "openaev_ext_no_argument_type";
      String contractId = "ext-contract-no-argument-type";

      InjectorContractInput contract = new InjectorContractInput();
      contract.setId(contractId);
      contract.setLabels(Map.of("en", "Contract without argumentType"));
      contract.setContent(
          """
          {
            "fields": [
              {
                "key": "target",
                "label": "target",
                "type": "text",
                "mandatory": true,
                "defaultValue": "srv-01"
              }
            ]
          }
          """);

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(injectorId);
      input.setName("External Injector Without Argument Type");
      input.setType(injectorType);
      input.setCategory("attack");
      input.setPayloads(false);
      input.setContracts(List.of(contract));

      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      Optional<Injector> persisted =
          injectorRepository.findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant());
      assertThat(persisted).isPresent();

      List<InjectorContract> contracts =
          injectorContractRepository.findByInjectorsContaining(persisted.get());
      assertThat(contracts).hasSize(1);
      assertThat(JsonPath.read(contracts.getFirst().getContent(), "$.fields[0].key"))
          .isEqualTo("target");
      assertThat(JsonPath.read(contracts.getFirst().getContent(), "$.fields[0].argumentType"))
          .isEqualTo("text");
    }

    @Test
    @DisplayName(
        "Should default argumentType to text when external contract sends null or empty values")
    void shouldDefaultPayloadArgumentTypeToTextWhenNullOrEmpty() throws Exception {
      String injectorId = "ext-injector-null-empty-argument-type";
      String injectorType = "openaev_ext_null_empty_argument_type";
      String contractId = "ext-contract-null-empty-argument-type";

      InjectorContractInput contract = new InjectorContractInput();
      contract.setId(contractId);
      contract.setLabels(Map.of("en", "Contract with null and empty argumentType"));
      contract.setContent(
          """
          {
            "fields": [
              {
                "key": "missing_type",
                "label": "missing_type",
                "type": "text",
                "mandatory": true,
                "defaultValue": "value-1"
              },
              {
                "key": "null_type",
                "label": "null_type",
                "type": "text",
                "mandatory": true,
                "defaultValue": "value-2",
                "argumentType": null
              },
              {
                "key": "empty_type",
                "label": "empty_type",
                "type": "text",
                "mandatory": true,
                "defaultValue": "value-3",
                "argumentType": ""
              }
            ]
          }
          """);

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(injectorId);
      input.setName("External Injector Null Empty Type");
      input.setType(injectorType);
      input.setCategory("attack");
      input.setPayloads(false);
      input.setContracts(List.of(contract));

      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      Optional<Injector> persisted =
          injectorRepository.findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant());
      assertThat(persisted).isPresent();

      List<InjectorContract> contracts =
          injectorContractRepository.findByInjectorsContaining(persisted.get());
      assertThat(contracts).hasSize(1);
      assertThat(JsonPath.read(contracts.getFirst().getContent(), "$.fields[0].argumentType"))
          .isEqualTo("text");
      assertThat(JsonPath.read(contracts.getFirst().getContent(), "$.fields[1].argumentType"))
          .isEqualTo("text");
      assertThat(JsonPath.read(contracts.getFirst().getContent(), "$.fields[2].argumentType"))
          .isEqualTo("text");
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
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
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
                  .with(csrf())
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Optional<Injector> persisted =
          injectorRepository.findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant());
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
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Optional<Injector> persisted =
          injectorRepository.findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant());
      assertThat(persisted).isPresent();
      assertThat(persisted.get().isExternal()).isTrue();
      assertThat(persisted.get().getExecutorCommands()).isNullOrEmpty();
      assertThat(persisted.get().getExecutorClearCommands()).isNullOrEmpty();
    }

    @Test
    @DisplayName(
        "Should adopt an injector-less contract (starter-pack import) on injector registration")
    void shouldAdoptInjectorlessContractOnRegistration() throws Exception {
      // -- ARRANGE --
      // Simulate a starter-pack import: the contract exists without any injector link
      InjectorContract orphanContract = InjectorContractFixture.createDefaultInjectorContract();
      orphanContract.clearInjectors();
      em.persist(orphanContract);
      injectorContractRepository.save(orphanContract);
      em.flush();

      String orphanContractId = orphanContract.getId();
      String realInjectorId = "real-injector-for-orphan";

      InjectorCreateInput input = new InjectorCreateInput();
      input.setId(realInjectorId);
      input.setName("Real Injector");
      input.setType("openaev_orphan_test");
      // The real injector registers a contract with the SAME id as the imported one
      input.setContracts(List.of(buildContractInput(orphanContractId)));

      // -- ACT --
      mvc.perform(
              multipart(INJECT0R_URI)
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Optional<Injector> realInjector =
          injectorRepository.findByIdAndTenantId(realInjectorId, TenantContext.getCurrentTenant());
      assertThat(realInjector).isPresent();
      assertThat(realInjector.get().isExternal()).isTrue();

      Optional<InjectorContract> adoptedContract =
          injectorContractRepository.findById(orphanContractId);
      assertThat(adoptedContract).isPresent();
      assertThat(adoptedContract.get().getInjectors())
          .extracting(Injector::getId)
          .contains(realInjectorId);
    }

    @Test
    @DisplayName(
        "Should assign distinct per-instance RabbitMQ queues when registering two injectors of the same type")
    void shouldAssignDistinctQueuesForTwoInjectorsOfSameType() throws Exception {
      // -- ARRANGE --
      String sharedType = "openaev_shared_type";

      InjectorCreateInput firstInput = new InjectorCreateInput();
      firstInput.setId("instance-1");
      firstInput.setName("First Instance");
      firstInput.setType(sharedType);
      firstInput.setContracts(List.of(buildContractInput("shared-contract-1")));

      InjectorCreateInput secondInput = new InjectorCreateInput();
      secondInput.setId("instance-2");
      secondInput.setName("Second Instance");
      secondInput.setType(sharedType);
      secondInput.setContracts(List.of(buildContractInput("shared-contract-1")));

      // -- ACT --
      String firstResponse =
          mvc.perform(
                  multipart(INJECT0R_URI)
                      .file(buildInputPart(firstInput))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String secondResponse =
          mvc.perform(
                  multipart(INJECT0R_URI)
                      .file(buildInputPart(secondInput))
                      .file(buildEmptyIconPart())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThatJson(firstResponse).inPath("listen").isString().contains("_injector_instance-1");
      assertThatJson(secondResponse).inPath("listen").isString().contains("_injector_instance-2");

      assertThat(firstResponse).isNotEqualTo(secondResponse);
    }
  }

  @Nested
  @DisplayName("Implant downloads")
  public class ImplantDownloadsTest {
    private static Stream<Arguments> platformArchCombinationsImplantSuccess() {
      return Stream.of(
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), Endpoint.PLATFORM_ARCH.arm64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), Endpoint.PLATFORM_ARCH.x86_64.name()),
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), "Aarch64"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), "Aarch64"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), "Aarch64"));
    }

    @ParameterizedTest(name = "GET implant for platform \"{0}\" arch \"{1}\" should succeed")
    @MethodSource("platformArchCombinationsImplantSuccess")
    public void given_platformAndArch_then_downloadExecutableSucceeds(String platform, String arch)
        throws Exception {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(agentWrapper)
          .persist();
      byte[] agentBytes =
          mvc.perform(
                  get("/api/implant/openaev/%s/%s".formatted(platform, arch))
                      .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                      .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                      .queryParam("injectId", injectWrapper.get().getId())
                      .queryParam("agentId", agentWrapper.get().getId()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      String baseFilename = "openaev-implant-Testing";
      String filename =
          switch (platform) {
            case "Windows" -> "%s.exe".formatted(baseFilename);
            default -> baseFilename;
          };
      assertThat(HashUtils.getSha256HexDigest(agentBytes))
          .isEqualTo(
              HashUtils.getSha256HexDigest(
                  "/implants/openaev-implant/%s/%s/%s"
                      .formatted(
                          platform.toLowerCase(),
                          AgentUtils.getCanonicalArchitectureString(arch.toLowerCase()),
                          filename)));
    }

    private static Stream<Arguments> platformArchCombinationsImplantFailure() {
      return Stream.of(
          Arguments.of(Endpoint.PLATFORM_TYPE.MacOS.name(), "not an arch"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Linux.name(), "not an arch"),
          Arguments.of(Endpoint.PLATFORM_TYPE.Windows.name(), "not an arch"));
    }

    @ParameterizedTest(name = "GET implant for platform \"{0}\" arch \"{1}\" should fail")
    @MethodSource("platformArchCombinationsImplantFailure")
    public void given_platformAndArch_then_downloadExecutableFails(String platform, String arch) {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointComposer
          .forEndpoint(EndpointFixture.createEndpoint())
          .withAgent(agentWrapper)
          .persist();
      assertThatThrownBy(
              () ->
                  mvc.perform(
                      get("/api/implant/openaev/%s/%s".formatted(platform, arch))
                          .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                          .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                          .queryParam("injectId", injectWrapper.get().getId())
                          .queryParam("agentId", agentWrapper.get().getId())))
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    @Test
    @DisplayName(
        "Given same injector type in two tenants, loading inject should reference correct tenant injector")
    void givenSameInjectorInTwoTenants_injectShouldReferenceCorrectInjector() throws Exception {
      // -- Arrange --
      String tenantA = TenantContext.getCurrentTenant();

      // TenantBaseListener auto-sets tenant from TenantContext
      Injector injectorA = getInjector("Email Injector");

      Inject injectA = InjectFixture.getDefaultInject();
      injectA.setInjector(injectorA);
      injectComposer.forInject(injectA).persist();

      em.flush();
      em.clear();

      // Create tenant B with same injector type
      Tenant tenantB = tenantHelper.createTenantWithCurrentUser("TenantB-Injector");
      tenantHelper.switchToTenant(tenantB.getId(), em);

      // TenantBaseListener auto-sets tenant B from TenantContext
      getInjector("Email Injector");

      // Switch back to tenant A and reload the inject
      tenantHelper.switchToTenant(tenantA, em);

      Inject reloadedInject = injectRepository.findById(injectA.getId()).orElseThrow();

      // -- Assert --
      assertThat(reloadedInject.getInjector())
          .as("Inject should have an injector (not null)")
          .isNotNull();

      assertThat(reloadedInject.getInjector().getTenantId())
          .as("Injector should belong to tenant A")
          .isEqualTo(tenantA);
    }
  }

  @Nested
  @DisplayName("Tenant Isolation API")
  @WithMockUser
  class TenantIsolationApi {

    @Test
    @DisplayName("Injector created in tenant X should NOT appear in tenant Y list")
    void given_injectorInTenantX_should_notAppearInTenantYList() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TENANT_SETTINGS, Capability.ACCESS_TENANT_SETTINGS));
      Tenant tenantY =
          tenantHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_TENANT_SETTINGS));

      InjectorCreateInput input =
          createDefaultInjectorCreateInput(
              "tenant-x-injector", "Tenant X Injector", "tenant_x_type", "tenant-x-contract");

      mvc.perform(
              multipart("/api/tenants/" + tenantX.getId() + "/injectors")
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      em.flush();
      em.clear();

      // -------- Act --------
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/injectors")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> injectorIds = JsonPath.read(response, "$[*].injector_id");
      assertThat(injectorIds).doesNotContain(input.getId());
    }

    @Test
    @DisplayName("Injector created in tenant X should appear in tenant X list")
    void given_injectorInTenantX_should_appearInTenantXList() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(Capability.MANAGE_TENANT_SETTINGS, Capability.ACCESS_TENANT_SETTINGS));

      InjectorCreateInput input =
          createDefaultInjectorCreateInput(
              "tenant-x-injector-visible",
              "Tenant X Injector Visible",
              "tenant_x_visible_type",
              "tenant-x-visible-contract");

      mvc.perform(
              multipart("/api/tenants/" + tenantX.getId() + "/injectors")
                  .file(buildInputPart(input))
                  .file(buildEmptyIconPart())
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      em.flush();
      em.clear();

      // -------- Act --------
      String response =
          mvc.perform(
                  get("/api/tenants/" + tenantX.getId() + "/injectors")
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -------- Assert --------
      List<String> injectorIds = JsonPath.read(response, "$[*].injector_id");
      assertThat(injectorIds).contains(input.getId());
    }
  }

  @Nested
  @DisplayName("Injector image")
  class InjectorImage {
    @Test
    @DisplayName("Given existing injector image should return image")
    void given_existingInjectorImage_should_returnImage() throws Exception {
      // -- Arrange --
      String injectorType = "test-injector-type";
      fileService.uploadStream(
          FileService.INJECTORS_IMAGES_BASE_PATH,
          injectorType + FileService.EXT_PNG,
          new java.io.ByteArrayInputStream(new byte[] {1, 2, 3}));

      // -- Act / Assert --
      mvc.perform(get(INJECT0R_URI + "/" + injectorType + "/image")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given missing injector image should return 404")
    void given_missingInjectorImage_should_return404() throws Exception {
      // -- Act / Assert --
      mvc.perform(get(INJECT0R_URI + "/nonexistent-type/image")).andExpect(status().isNotFound());
    }
  }
}
