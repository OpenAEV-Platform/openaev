package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.injector.InjectorApi.INJECT0R_URI;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultInjector;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.utils.AgentUtils;
import io.openaev.utils.HashUtils;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Injector Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_PLATFORM_SETTINGS})
public class InjectorApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private InjectorRepository injectorRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

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
}
