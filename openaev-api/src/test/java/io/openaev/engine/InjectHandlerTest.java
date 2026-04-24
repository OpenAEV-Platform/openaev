package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.inject.InjectHandler;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("InjectHandler indexing tests")
class InjectHandlerTest extends IntegrationTest {

  @Autowired private InjectHandler injectHandler;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  @BeforeEach
  void setUp() {
    injectComposer.reset();
    scenarioComposer.reset();
  }

  @Nested
  @DisplayName("Fetch injects for indexing")
  class FetchInjectsForIndexing {

    @Test
    @DisplayName("Given an inject in a scenario should return correctly mapped EsInject")
    void given_injectInScenario_should_returnCorrectlyMappedEsInject() {
      // Arrange
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      Inject inject = injectWrapper.get();

      // Act
      List<EsInject> results = injectHandler.fetch(null, 5000);

      // Assert
      assertThat(results).isNotEmpty();
      EsInject esInject =
          results.stream()
              .filter(es -> es.getBase_id().equals(inject.getId()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Inject not found in indexing results"));

      assertThat(esInject.getBase_id()).isEqualTo(inject.getId());
      assertThat(esInject.getBase_representative()).isEqualTo(inject.getTitle());
      assertThat(esInject.getInject_title()).isEqualTo(inject.getTitle());
      assertThat(esInject.getInject_status()).isEqualTo(ExecutionStatus.DRAFT.name());
      assertThat(esInject.getBase_scenario_side()).isEqualTo(scenario.getId());
    }

    @Test
    @DisplayName("Given no injects after timestamp should return empty list")
    void given_noInjectsAfterTimestamp_should_returnEmptyList() {
      // Arrange
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .persist();
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsInject> results = injectHandler.fetch(Instant.now().plusSeconds(3600), 5000);

      // Assert
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Given batch size limit should respect limit")
    void given_batchSizeLimit_should_respectLimit() {
      // Arrange
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .persist();
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsInject> results = injectHandler.fetch(null, 2);

      // Assert
      assertThat(results).hasSize(2);
    }
  }
}
