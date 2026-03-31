package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.executor.ExecutorApi.EXECUTOR_URI;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.service.EndpointService;
import io.openaev.utils.AgentUtils;
import io.openaev.utils.HashUtils;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Executor Api Integration Tests")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS})
public class ExecutorApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ExecutorRepository executorRepository;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;
  @Autowired private ExecutorFixture executorFixture;

  private ConnectorInstancePersisted getExecutorInstance(String executorId, String executorName)
      throws JsonProcessingException {
    return connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance())
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(
                    executorName, ConnectorType.EXECUTOR)))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration("EXECUTOR_ID", executorId)))
        .persist()
        .get();
  }

  private Executor getExecutor(String executorName) {
    Executor executor = executorFixture.createDefaultExecutor(executorName);
    return executorRepository.save(executor);
  }

  @Nested
  @DisplayName("Retrieve executors")
  class GetExecutors {
    @Test
    @DisplayName("Should retrieve all executors")
    void shouldRetrieveAllExecutors() throws Exception {
      Executor executor = getExecutor("new-executor");
      List<Executor> existingExecutors = fromIterable(executorRepository.findAll());
      getExecutorInstance("PENDING_EXECUTOR_ID", "Pending executor");
      ConnectorInstancePersisted connectorInstanceLinkToCreatedExecutor =
          getExecutorInstance(executor.getId(), executor.getName());

      String response =
          mvc.perform(
                  get(EXECUTOR_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingExecutors.size());

      assertThatJson(response)
          .inPath("[*].executor_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              existingExecutors.stream().map(Executor::getId).toList());

      String path = "$[?(@.executor_id == '" + executor.getId() + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(connectorInstanceLinkToCreatedExecutor.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }

    @Test
    @DisplayName(
        "Given queryParams include_next to true should retrieve all executors and and pending executors")
    void givenQueryParamsIncludeNextToTrue_shouldRetrieveAllExecutorsAndPendingExecutors()
        throws Exception {
      getExecutor("tanium");
      List<Executor> existingExecutors = fromIterable(executorRepository.findAll());
      String pendingExecutorIdId = "PENDING_EXECUTOR_ID";
      ConnectorInstancePersisted pendingExecutorInstance =
          getExecutorInstance(pendingExecutorIdId, "PENDING EXECUTOR");

      String response =
          mvc.perform(
                  get(EXECUTOR_URI + "?include_next=true")
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).isArray().size().isEqualTo(existingExecutors.size() + 1);

      assertThatJson(response)
          .inPath("[*].executor_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(
              Stream.concat(
                      existingExecutors.stream().map(Executor::getId),
                      Stream.of(pendingExecutorIdId))
                  .toList());
      String path = "$[?(@.executor_id == '" + pendingExecutorIdId + "')]";

      assertThatJson(response)
          .inPath(path + ".catalog.catalog_connector_id")
          .isArray()
          .containsExactly(pendingExecutorInstance.getCatalogConnector().getId());

      assertThatJson(response).inPath(path + ".is_verified").isArray().containsExactly(true);
    }
  }

  @Nested
  @DisplayName("Related executors ids")
  class GetRelatedExecutorIds {
    @Test
    @DisplayName(
        "Given executor managed by XTM Composer, should return linked connector instance ID and catalog ID")
    void givenLinkedExecutor_shouldReturnInstanceAndCatalogId() throws Exception {
      Executor executor = getExecutor("CS-executor");
      ConnectorInstancePersisted instance =
          getExecutorInstance(executor.getId(), executor.getName());
      String response =
          mvc.perform(
                  get(EXECUTOR_URI + "/" + executor.getId() + "/related-ids")
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
        "Given executor matching a catalog type, should return matching catalog ID without connector instance ID")
    void givenExecutorWithType_shouldReturnCatalogWithMatchingSlug() throws Exception {
      Executor executor = getExecutor("cs-executor");
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer("cs-executor"))
              .persist()
              .get();

      String response =
          mvc.perform(
                  get(EXECUTOR_URI + "/" + executor.getId() + "/related-ids")
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
    @DisplayName("Given unlinked executor, should return empty catalog ID and empty instance ID")
    void givenUnlinkedExecutor_shouldReturnEmptyInstanceAndCatalogId() throws Exception {
      Executor executor = getExecutor("new-executor");
      String response =
          mvc.perform(
                  get(EXECUTOR_URI + "/" + executor.getId() + "/related-ids")
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
  @DisplayName("Agent downloads")
  public class AgentDownloadsTest {
    private String getHexDigestFromBinaryResourcePath(String path) throws Exception {
      try (InputStream inputStream = getClass().getResourceAsStream(path)) {
        return HashUtils.getSha256HexDigest(inputStream.readAllBytes());
      }
    }

    private enum Outcome {
      succeed,
      fail
    }

    private static Stream<Arguments> platformArchCombinations() {
      return Stream.of(
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SERVICE_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.arm64.name(),
              EndpointService.SESSION_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SERVICE_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              Endpoint.PLATFORM_ARCH.x86_64.name(),
              EndpointService.SESSION_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "aarch64",
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "aarch64",
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "aarch64",
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "aarch64",
              EndpointService.SERVICE,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "aarch64",
              EndpointService.SERVICE_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "aarch64",
              EndpointService.SESSION_USER,
              Outcome.fail,
              UnsupportedOperationException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "aarch64",
              EndpointService.SERVICE,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "aarch64",
              EndpointService.SERVICE_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "aarch64",
              EndpointService.SESSION_USER,
              Outcome.succeed,
              null),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "not an arch",
              EndpointService.SERVICE,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "not an arch",
              EndpointService.SERVICE_USER,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.MacOS.name(),
              "not an arch",
              EndpointService.SESSION_USER,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "not an arch",
              EndpointService.SERVICE,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "not an arch",
              EndpointService.SERVICE_USER,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Linux.name(),
              "not an arch",
              EndpointService.SESSION_USER,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "not an arch",
              EndpointService.SERVICE,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "not an arch",
              EndpointService.SERVICE_USER,
              Outcome.fail,
              IllegalArgumentException.class),
          Arguments.of(
              Endpoint.PLATFORM_TYPE.Windows.name(),
              "not an arch",
              EndpointService.SESSION_USER,
              Outcome.fail,
              IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "GET package for platform \"{0}\" arch \"{1}\" install type \"{2}\" should {3} ")
    @MethodSource("platformArchCombinations")
    public void given_platformAndArch_then_downloadOutcomeAsDesigned(
        String platform,
        String arch,
        String installType,
        Outcome outcome,
        Class<? extends Exception> exceptionType)
        throws Exception {
      switch (outcome) {
        case succeed -> {
          byte[] agentBytes =
              mvc.perform(
                      get("/api/agent/package/openaev/%s/%s/%s"
                              .formatted(platform, arch, installType))
                          .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                          .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                  .andExpect(status().is2xxSuccessful())
                  .andReturn()
                  .getResponse()
                  .getContentAsByteArray();

          String filename = switch(installType) {
            case EndpointService.SERVICE -> "openaev-agent-installer-Testing.exe";
            default -> "openaev-agent-installer-%s-Testing.exe".formatted(installType);
          };
          assertThat(HashUtils.getSha256HexDigest(agentBytes))
              .isEqualTo(
                  getHexDigestFromBinaryResourcePath(
                      "/agents/openaev-agent/%s/%s/%s"
                          .formatted(
                              platform.toLowerCase(),
                              AgentUtils.getCanonicalArchitectureString(arch.toLowerCase()),
                              filename)));
        }
        case fail ->
            assertThatThrownBy(
                    () ->
                        mvc.perform(
                            get("/api/agent/package/openaev/%s/%s/%s"
                                    .formatted(platform, arch, installType))
                                .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                                .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .hasCauseInstanceOf(exceptionType);
      }
    }
  }
}
