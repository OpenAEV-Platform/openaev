package io.openaev.rest;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.executor.ExecutorApi.EXECUTOR_URI;
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
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
}
