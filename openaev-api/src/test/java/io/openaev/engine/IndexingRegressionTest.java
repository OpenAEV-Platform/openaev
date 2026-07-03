package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.engine.model.endpoint.EndpointHandler;
import io.openaev.engine.model.endpoint.EsEndpoint;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.injectexpectation.InjectExpectationHandler;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.scenario.ScenarioHandler;
import io.openaev.engine.model.simulation.EsSimulation;
import io.openaev.engine.model.simulation.SimulationHandler;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-regression tests for the four {@code findForIndexing} queries (Endpoint, Exercise, Scenario,
 * InjectExpectation).
 *
 * <p>Each test verifies that an entity whose only change is on a <em>linked</em> row (e.g. an
 * inject updated after {@code :from}) still appears in the indexing results. This catches silent
 * failures in the changed-set CTE logic that would otherwise only surface weeks later in
 * production.
 *
 * @see io.openaev.engine.InjectHandlerTest
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("findForIndexing non-regression tests")
class IndexingRegressionTest extends IntegrationTest {

  @Autowired private EndpointHandler endpointHandler;
  @Autowired private SimulationHandler simulationHandler;
  @Autowired private ScenarioHandler scenarioHandler;
  @Autowired private InjectExpectationHandler injectExpectationHandler;

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  /** A point in time used as the {@code :from} parameter — 1 hour ago. */
  private static final Instant FROM = Instant.now().minus(1, ChronoUnit.HOURS);

  /** A point in time safely before {@code FROM}. */
  private static final Instant PAST = FROM.minus(1, ChronoUnit.DAYS);

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    endpointComposer.reset();
    injectExpectationComposer.reset();
    agentComposer.reset();
    collectorComposer.reset();
    securityPlatformComposer.reset();
  }

  // ---------------------------------------------------------------------------
  // Helpers — push a row's updated_at into the past via native SQL so that the
  // only "recent" change comes from a linked row.
  // ---------------------------------------------------------------------------

  private void pushScenarioToPast(String scenarioId) {
    entityManager
        .createNativeQuery("UPDATE scenarios SET scenario_updated_at = :ts WHERE scenario_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", scenarioId)
        .executeUpdate();
  }

  private void pushExerciseToPast(String exerciseId) {
    entityManager
        .createNativeQuery("UPDATE exercises SET exercise_updated_at = :ts WHERE exercise_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", exerciseId)
        .executeUpdate();
  }

  private void pushEndpointToPast(String assetId) {
    entityManager
        .createNativeQuery("UPDATE assets SET asset_updated_at = :ts WHERE asset_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", assetId)
        .executeUpdate();
  }

  private void pushInjectToPast(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = :ts WHERE inject_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", injectId)
        .executeUpdate();
  }

  private void pushExpectationToPast(String expectationId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                + " WHERE inject_expectation_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", expectationId)
        .executeUpdate();
  }

  private void touchInject(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = :ts WHERE inject_id = :id")
        .setParameter("ts", Instant.now())
        .setParameter("id", injectId)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------
  // Scenario
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("ScenarioHandler.findForIndexing")
  class ScenarioIndexing {

    @Test
    @DisplayName("Scenario appears when only its linked inject was updated after :from")
    void scenario_reindexed_when_linked_inject_updated() {
      // Arrange — create a scenario with an inject
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();

      // Push the scenario's own updated_at into the past
      pushScenarioToPast(scenario.getId());
      // The inject's updated_at stays at now() — recent
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsScenario> results = scenarioHandler.fetch(FROM, 5000);

      // Assert — scenario must appear because its inject is recent
      assertThat(results).anyMatch(es -> es.getBase_id().equals(scenario.getId()));
    }
  }

  // ---------------------------------------------------------------------------
  // Exercise (Simulation)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("SimulationHandler.findForIndexing")
  class SimulationIndexing {

    @Test
    @DisplayName("Exercise appears when only its linked inject was updated after :from")
    void exercise_reindexed_when_linked_inject_updated() {
      // Arrange — create an exercise with an inject
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Exercise exercise =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();

      // Push the exercise's own updated_at into the past
      pushExerciseToPast(exercise.getId());
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsSimulation> results = simulationHandler.fetch(FROM, 5000);

      // Assert — exercise must appear because its inject is recent
      assertThat(results).anyMatch(es -> es.getBase_id().equals(exercise.getId()));
    }
  }

  // ---------------------------------------------------------------------------
  // Endpoint
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("EndpointHandler.findForIndexing")
  class EndpointIndexing {

    @Test
    @DisplayName("Endpoint appears when only its linked inject was updated after :from")
    void endpoint_reindexed_when_linked_inject_updated() {
      // Arrange — create an endpoint linked to an inject via a scenario
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(endpointWrapper);
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();

      Endpoint endpoint = endpointWrapper.get();
      Inject inject = injectWrapper.get();

      // Push the endpoint's own updated_at into the past
      pushEndpointToPast(endpoint.getId());
      // Also push the inject so the only "recent" change must come from touching it
      pushInjectToPast(inject.getId());
      // Now touch the inject so it becomes recent again
      touchInject(inject.getId());
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsEndpoint> results = endpointHandler.fetch(FROM, 5000);

      // Assert — endpoint must appear because its linked inject is recent
      assertThat(results).anyMatch(es -> es.getBase_id().equals(endpoint.getId()));
    }
  }

  // ---------------------------------------------------------------------------
  // InjectExpectation
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("InjectExpectationHandler.findForIndexing")
  class InjectExpectationIndexing {

    @Test
    @DisplayName("Expectation appears when only its linked inject was updated after :from")
    void expectation_reindexed_when_linked_inject_updated() {
      // Arrange — create an inject with an expectation inside a scenario
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      BaseInjectExpectation expectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer.forExpectation(expectation).withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExpectation(expectationWrapper);
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();

      Inject inject = injectWrapper.get();

      // Push the expectation's own updated_at into the past
      pushExpectationToPast(expectation.getId());
      // Also push the inject then touch it so the inject is the only recent change
      pushInjectToPast(inject.getId());
      touchInject(inject.getId());
      entityManager.flush();
      entityManager.clear();

      // Act — the handler filters out agent-level expectations, so we pass null/large limit
      List<EsInjectExpectation> results = injectExpectationHandler.fetch(FROM, 5000);

      // Assert — expectation must appear because its linked inject is recent
      assertThat(results).anyMatch(es -> es.getBase_id().equals(expectation.getId()));
    }

    @Test
    @DisplayName(
        "Agentless expectation keeps security platforms side from agent-level expectation results")
    void
        given_agentless_expectation_should_index_security_platforms_from_agent_expectation_results() {
      // Arrange
      SecurityPlatformComposer.Composer securityPlatform =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR test", "EDR"))
              .persist();
      Collector collector =
          collectorComposer
              .forCollector(CollectorFixture.createDefaultCollector("collector-edr"))
              .withSecurityPlatform(securityPlatform)
              .persist()
              .get();

      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      endpointWrapper.persist();

      BaseInjectExpectation agentlessExpectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      InjectExpectationComposer.Composer agentlessExpectationWrapper =
          injectExpectationComposer
              .forExpectation(agentlessExpectation)
              .withEndpoint(endpointWrapper);

      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpointWrapper.get());
      entityManager.persist(agent);
      entityManager.flush();
      Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

      DetectionInjectExpectation agentExpectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      agentExpectation.setAgent(persistedAgent);
      agentExpectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collector.getId())
                  .sourceType("collector")
                  .sourceName(collector.getName())
                  .result("detected")
                  .score(100.0)
                  .build()));
      InjectExpectationComposer.Composer agentExpectationWrapper =
          injectExpectationComposer.forExpectation(agentExpectation).withEndpoint(endpointWrapper);

      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExpectation(agentlessExpectationWrapper)
              .withExpectation(agentExpectationWrapper);
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();
      entityManager.clear();

      // Act
      List<EsInjectExpectation> results = injectExpectationHandler.fetch(FROM, 5000);

      // Assert
      assertThat(results)
          .filteredOn(es -> es.getBase_id().equals(agentlessExpectation.getId()))
          .singleElement()
          .satisfies(
              es ->
                  assertThat(es.getBase_security_platforms_side())
                      .contains(securityPlatform.get().getId()));
    }
  }
}
