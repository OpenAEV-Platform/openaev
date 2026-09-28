package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.query.EsEntities;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("EngineService integration tests - actual ES/OpenSearch deletion")
class EngineServiceIntegrationTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EndpointComposer endpointComposer;

  /** An admin RawUserAuth used to call engineService.entities() directly. */
  private static final RawUserAuth ADMIN_USER =
      new RawUserAuth() {
        @Override
        public String getUser_id() {
          return "test-admin-" + UUID.randomUUID();
        }

        @Override
        public boolean getUser_admin() {
          return true;
        }

        @Override
        public Set<RawGrant> getUser_grants() {
          return Set.of();
        }
      };

  @BeforeEach
  void resetIndex() throws IOException {
    endpointComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private EsEntities queryEndpoints() {
    ListConfiguration config = engineService.createListConfiguration("asset", Map.of());
    // ALL_TIME avoids the DEFAULT branch that requires a dashboard timeRange parameter
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 100));
    return engineService.entities(ADMIN_USER, runtime);
  }

  private void indexAndWait() throws InterruptedException {
    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);
  }

  private void deleteAndWait(List<String> ids) throws InterruptedException {
    engineService.bulkDelete(ids);
    // ES processes deletion asynchronously — give it time
    Thread.sleep(1_000);
  }

  @Nested
  @DisplayName("bulkDelete")
  class BulkDelete {

    @Test
    @DisplayName("Indexed endpoint should remove document from engine after bulk delete")
    void given_indexedEndpoint_should_removeDocumentFromEngineAfterBulkDelete()
        throws InterruptedException {
      // Arrange: persist one endpoint and index it
      String endpointId =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint("ep-delete-single"))
              .persist()
              .get()
              .getId();

      indexAndWait();

      assertThat(queryEndpoints().getTotal())
          .as("endpoint should be present in engine before deletion")
          .isEqualTo(1);

      // Act
      deleteAndWait(List.of(endpointId));

      // Assert
      assertThat(queryEndpoints().getTotal())
          .as("endpoint should have been removed from the engine index after bulkDelete")
          .isZero();
    }

    @Test
    @DisplayName(
        "Multiple indexed endpoints should remove all documents from engine after bulk delete")
    void given_multipleIndexedEndpoints_should_removeAllDocumentsFromEngineAfterBulkDelete()
        throws InterruptedException {
      // Arrange — persist two endpoints and index them
      String endpointAId =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint("ep-delete-a"))
              .persist()
              .get()
              .getId();
      String endpointBId =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint("ep-delete-b"))
              .persist()
              .get()
              .getId();

      indexAndWait();

      assertThat(queryEndpoints().getTotal())
          .as("both endpoints should be present in engine before deletion")
          .isEqualTo(2);

      // Act — delete both in a single bulk call (covers the ids.getFirst() regression)
      deleteAndWait(List.of(endpointAId, endpointBId));

      // Assert
      assertThat(queryEndpoints().getTotal())
          .as(
              "all endpoints should have been removed from the engine index after"
                  + " bulkDelete with multiple IDs")
          .isZero();
    }

    @Test
    @DisplayName("Bulk delete only one element should leave other intact")
    void given_bulkDeleteOnlyOneId_should_leaveOtherDocumentIntact() throws InterruptedException {
      // Arrange
      String endpointToDeleteId =
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint("ep-to-delete"))
              .persist()
              .get()
              .getId();
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint("ep-to-keep")).persist();

      indexAndWait();

      assertThat(queryEndpoints().getTotal())
          .as("both endpoints should be present before partial deletion")
          .isEqualTo(2);

      // Act — delete only one
      deleteAndWait(List.of(endpointToDeleteId));

      // Assert
      assertThat(queryEndpoints().getTotal())
          .as("only the targeted endpoint should have been deleted; the other must remain")
          .isEqualTo(1);
    }
  }
}
