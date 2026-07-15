package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.engine.model.endpoint.EndpointHandler;
import io.openaev.engine.model.endpoint.EsEndpoint;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.inject.InjectHandler;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.injectexpectation.InjectExpectationHandler;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.scenario.ScenarioHandler;
import io.openaev.engine.model.simulation.EsSimulation;
import io.openaev.engine.model.simulation.SimulationHandler;
import io.openaev.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import io.openaev.engine.model.vulnerableendpoint.VulnerableEndpointHandler;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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

  @Autowired private InjectHandler injectHandler;
  @Autowired private EndpointHandler endpointHandler;
  @Autowired private SimulationHandler simulationHandler;
  @Autowired private ScenarioHandler scenarioHandler;
  @Autowired private InjectExpectationHandler injectExpectationHandler;
  @Autowired private VulnerableEndpointHandler vulnerableEndpointHandler;

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private FindingComposer findingComposer;

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
    findingComposer.reset();
  }

  // ---------------------------------------------------------------------------
  // Helpers — push a row's updated_at into the past via native SQL so that the
  // only "recent" change comes from a linked row.
  // ---------------------------------------------------------------------------

  private void pushScenarioToPast(String scenarioId) {
    entityManager
        .createNativeQuery("UPDATE scenarios SET scenario_updated_at = ?1 WHERE scenario_id = ?2")
        .setParameter(1, PAST)
        .setParameter(2, scenarioId)
        .executeUpdate();
  }

  private void pushExerciseToPast(String exerciseId) {
    entityManager
        .createNativeQuery("UPDATE exercises SET exercise_updated_at = ?1 WHERE exercise_id = ?2")
        .setParameter(1, PAST)
        .setParameter(2, exerciseId)
        .executeUpdate();
  }

  private void pushEndpointToPast(String assetId) {
    entityManager
        .createNativeQuery("UPDATE assets SET asset_updated_at = ?1 WHERE asset_id = ?2")
        .setParameter(1, PAST)
        .setParameter(2, assetId)
        .executeUpdate();
  }

  private void pushInjectToPast(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = ?1 WHERE inject_id = ?2")
        .setParameter(1, PAST)
        .setParameter(2, injectId)
        .executeUpdate();
  }

  private void pushExpectationToPast(String expectationId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_updated_at = ?1"
                + " WHERE inject_expectation_id = ?2")
        .setParameter(1, PAST)
        .setParameter(2, expectationId)
        .executeUpdate();
  }

  private void touchInject(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = ?1 WHERE inject_id = ?2")
        .setParameter(1, Instant.now())
        .setParameter(2, injectId)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------
  // Inject
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("InjectHandler.findForIndexing")
  class InjectIndexing {

    @Test
    @DisplayName("Inject appears when its own updated_at is after :from")
    void inject_reindexed_when_updated() {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();

      Inject inject = injectWrapper.get();
      pushInjectToPast(inject.getId());
      touchInject(inject.getId());
      entityManager.flush();
      entityManager.clear();

      List<EsInject> results = injectHandler.fetch(FROM, 5000);

      assertThat(results).anyMatch(es -> es.getBase_id().equals(inject.getId()));
    }

    @Test
    @DisplayName("Inject correlated subqueries return correct data")
    void inject_correlated_subqueries_return_correct_data() {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();
      entityManager.clear();

      Inject inject = injectWrapper.get();
      List<EsInject> results = injectHandler.fetch(null, 5000);

      EsInject esInject =
          results.stream()
              .filter(es -> es.getBase_id().equals(inject.getId()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("Inject not found in indexing results"));

      assertThat(esInject.getInject_title()).isEqualTo(inject.getTitle());
      assertThat(esInject.getBase_id()).isNotNull();
    }

    @Test
    @DisplayName("Inject batch size limit is respected by ranked CTE")
    void inject_batch_limit_is_respected() {
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
          .persist();
      entityManager.flush();
      entityManager.clear();

      List<EsInject> results = injectHandler.fetch(null, 2);

      assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Multi-batch cursor loop indexes all injects without skips or duplicates")
    void multi_batch_cursor_no_skips_or_duplicates() {
      // Arrange — create 7 injects with distinct timestamps so ordering is deterministic
      int totalInjects = 7;
      int batchSize = 3;
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultIncidentResponseScenario());
      Set<String> expectedIds = new HashSet<>();
      for (int i = 0; i < totalInjects; i++) {
        InjectComposer.Composer inj = injectComposer.forInject(InjectFixture.getDefaultInject());
        scenarioWrapper.withInject(inj);
      }
      scenarioWrapper.persist();
      entityManager.flush();

      // Assign distinct updated_at timestamps so each batch has a clear ordering
      List<?> injectIds =
          entityManager
              .createNativeQuery(
                  "SELECT inject_id FROM injects WHERE inject_scenario = ?1 ORDER BY inject_id")
              .setParameter(1, scenarioWrapper.get().getId())
              .getResultList();
      Instant base = FROM.plusSeconds(1);
      for (int i = 0; i < injectIds.size(); i++) {
        entityManager
            .createNativeQuery("UPDATE injects SET inject_updated_at = ?1 WHERE inject_id = ?2")
            .setParameter(1, base.plusSeconds(i))
            .setParameter(2, injectIds.get(i).toString())
            .executeUpdate();
        expectedIds.add(injectIds.get(i).toString());
      }
      entityManager.flush();
      entityManager.clear();

      // Act — simulate the indexing cursor loop
      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      int maxIterations = (totalInjects / batchSize) + 2; // safety bound

      while (iterations < maxIterations) {
        List<EsInject> batch = injectHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsInject es : batch) {
          assertThat(collectedIds.add(es.getBase_id()))
              .as("Duplicate detected: %s at iteration %d", es.getBase_id(), iterations)
              .isTrue();
        }
        cursor = batch.getLast().getBase_updated_at();
        iterations++;
      }

      // Assert — every inject was indexed exactly once
      assertThat(collectedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
    }
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
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();

      pushScenarioToPast(scenario.getId());
      entityManager.flush();
      entityManager.clear();

      List<EsScenario> results = scenarioHandler.fetch(FROM, 5000);

      assertThat(results).anyMatch(es -> es.getBase_id().equals(scenario.getId()));
    }

    @Test
    @DisplayName("Multi-batch cursor loop indexes all scenarios without skips or duplicates")
    void multi_batch_cursor_no_skips_or_duplicates() {
      int total = 5;
      int batchSize = 2;
      Set<String> expectedIds = new HashSet<>();

      for (int i = 0; i < total; i++) {
        Scenario s =
            scenarioComposer
                .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
                .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
                .persist()
                .get();
        expectedIds.add(s.getId());
      }
      entityManager.flush();

      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE scenarios SET scenario_updated_at = ?1 WHERE scenario_id = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = ?1 WHERE inject_scenario = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();

      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      while (iterations < total + 2) {
        List<EsScenario> batch = scenarioHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsScenario es : batch) {
          assertThat(collectedIds.add(es.getBase_id()))
              .as("Duplicate: %s", es.getBase_id())
              .isTrue();
        }
        cursor = batch.getLast().getBase_updated_at();
        iterations++;
      }

      assertThat(collectedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
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
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      Exercise exercise =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();

      pushExerciseToPast(exercise.getId());
      entityManager.flush();
      entityManager.clear();

      List<EsSimulation> results = simulationHandler.fetch(FROM, 5000);

      assertThat(results).anyMatch(es -> es.getBase_id().equals(exercise.getId()));
    }

    @Test
    @DisplayName("Multi-batch cursor loop indexes all exercises without skips or duplicates")
    void multi_batch_cursor_no_skips_or_duplicates() {
      int total = 5;
      int batchSize = 2;
      Set<String> expectedIds = new HashSet<>();

      for (int i = 0; i < total; i++) {
        Exercise ex =
            exerciseComposer
                .forExercise(ExerciseFixture.createDefaultExercise())
                .withInject(injectComposer.forInject(InjectFixture.getDefaultInject()))
                .persist()
                .get();
        expectedIds.add(ex.getId());
      }
      entityManager.flush();

      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE exercises SET exercise_updated_at = ?1 WHERE exercise_id = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = ?1 WHERE inject_exercise = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();

      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      while (iterations < total + 2) {
        List<EsSimulation> batch = simulationHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsSimulation es : batch) {
          assertThat(collectedIds.add(es.getBase_id()))
              .as("Duplicate: %s", es.getBase_id())
              .isTrue();
        }
        cursor = batch.getLast().getBase_updated_at();
        iterations++;
      }

      assertThat(collectedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
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

      pushEndpointToPast(endpoint.getId());
      pushInjectToPast(inject.getId());
      touchInject(inject.getId());
      entityManager.flush();
      entityManager.clear();

      List<EsEndpoint> results = endpointHandler.fetch(FROM, 5000);

      assertThat(results).anyMatch(es -> es.getBase_id().equals(endpoint.getId()));
    }

    @Test
    @DisplayName("Multi-batch cursor loop indexes all endpoints without skips or duplicates")
    void multi_batch_cursor_no_skips_or_duplicates() {
      int total = 5;
      int batchSize = 2;
      Set<String> expectedIds = new HashSet<>();

      for (int i = 0; i < total; i++) {
        EndpointComposer.Composer ep =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        InjectComposer.Composer inj =
            injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(ep);
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(inj)
            .persist();
        expectedIds.add(ep.get().getId());
      }
      entityManager.flush();

      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery("UPDATE assets SET asset_updated_at = ?1 WHERE asset_id = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = ?1 WHERE inject_id IN"
                    + " (SELECT inject_id FROM injects_assets WHERE asset_id = ?2)")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();

      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      while (iterations < total + 2) {
        List<EsEndpoint> batch = endpointHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsEndpoint es : batch) {
          assertThat(collectedIds.add(es.getBase_id()))
              .as("Duplicate: %s", es.getBase_id())
              .isTrue();
        }
        cursor = batch.getLast().getBase_updated_at();
        iterations++;
      }

      assertThat(collectedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
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

      pushExpectationToPast(expectation.getId());
      pushInjectToPast(inject.getId());
      touchInject(inject.getId());
      entityManager.flush();
      entityManager.clear();

      List<EsInjectExpectation> results = injectExpectationHandler.fetch(FROM, 5000);

      assertThat(results).anyMatch(es -> es.getBase_id().equals(expectation.getId()));
    }

    @Test
    @DisplayName(
        "Agentless expectation keeps security platforms side from agent-level expectation results")
    void
        given_agentless_expectation_should_index_security_platforms_from_agent_expectation_results() {
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

      List<EsInjectExpectation> results = injectExpectationHandler.fetch(FROM, 5000);

      assertThat(results)
          .filteredOn(es -> es.getBase_id().equals(agentlessExpectation.getId()))
          .singleElement()
          .satisfies(
              es ->
                  assertThat(es.getBase_security_platforms_side())
                      .contains(securityPlatform.get().getId()));
    }

    @Test
    @DisplayName("Agent-level expectations are excluded by the CTE filter")
    void agent_expectations_are_excluded() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointWrapper.withAgent(agentWrapper);
      DetectionInjectExpectation expectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer.forExpectation(expectation).withAgent(agentWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withExpectation(expectationWrapper);
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(injectWrapper)
          .persist();
      entityManager.flush();
      entityManager.clear();

      List<EsInjectExpectation> results = injectExpectationHandler.fetch(null, 5000);

      assertThat(results).noneMatch(es -> es.getBase_id().equals(expectation.getId()));
    }

    @Test
    @DisplayName("Multi-batch cursor loop indexes all expectations without skips or duplicates")
    void multi_batch_cursor_no_skips_or_duplicates() {
      int total = 5;
      int batchSize = 2;
      Set<String> expectedIds = new HashSet<>();

      for (int i = 0; i < total; i++) {
        EndpointComposer.Composer ep =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        DetectionInjectExpectation exp =
            InjectExpectationFixture.createDefaultDetectionInjectExpectation();
        InjectExpectationComposer.Composer expWrapper =
            injectExpectationComposer.forExpectation(exp).withEndpoint(ep);
        InjectComposer.Composer inj =
            injectComposer.forInject(InjectFixture.getDefaultInject()).withExpectation(expWrapper);
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(inj)
            .persist();
        expectedIds.add(exp.getId());
      }
      entityManager.flush();

      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE injects_expectations SET inject_expectation_updated_at = ?1"
                    + " WHERE inject_expectation_id = ?2")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = ?1 WHERE inject_id = "
                    + "(SELECT inject_id FROM injects_expectations WHERE inject_expectation_id = ?2)")
            .setParameter(1, ts)
            .setParameter(2, id)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();

      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      while (iterations < total + 2) {
        List<EsInjectExpectation> batch = injectExpectationHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsInjectExpectation es : batch) {
          assertThat(collectedIds.add(es.getBase_id()))
              .as("Duplicate: %s", es.getBase_id())
              .isTrue();
        }
        cursor = batch.getLast().getBase_updated_at();
        iterations++;
      }

      assertThat(collectedIds).containsExactlyInAnyOrderElementsOf(expectedIds);
    }

    @Test
    @DisplayName(
        "Compound cursor: all expectations sharing the same timestamp are returned across"
            + " batches")
    void compound_cursor_returns_all_expectations_at_same_timestamp() {
      // Arrange — 3 expectations in the same inject, all forced to identical updated_at
      // (simulates bulkComputeTechnicalExpectations saving many rows in a single saveAll())
      Instant sharedTimestamp = FROM.plus(30, ChronoUnit.MINUTES);

      // Use composer wrappers: .get().getId() is the established pattern that guarantees
      // the JPA-assigned ID after persist+flush, regardless of @GeneratedValue strategy.
      InjectExpectationComposer.Composer exp1Composer =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()));
      InjectExpectationComposer.Composer exp2Composer =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()));
      InjectExpectationComposer.Composer exp3Composer =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()));

      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withExpectation(exp1Composer)
                  .withExpectation(exp2Composer)
                  .withExpectation(exp3Composer))
          .persist();
      entityManager.flush();

      String id1 = exp1Composer.get().getId();
      String id2 = exp2Composer.get().getId();
      String id3 = exp3Composer.get().getId();

      // Force all 3 expectations AND their inject to sharedTimestamp so that
      // GREATEST(raw_expectation, inject_updated_at, contract_updated_at) = sharedTimestamp.
      // Without aligning the inject, GREATEST = NOW() (inject was just created), and the
      // compound cursor fetch(NOW(), lastId, …) finds nothing at that exact timestamp → exp3 skip.
      for (String expId : List.of(id1, id2, id3)) {
        entityManager
            .createNativeQuery(
                "UPDATE injects_expectations SET inject_expectation_updated_at = ?1"
                    + " WHERE inject_expectation_id = ?2")
            .setParameter(1, sharedTimestamp)
            .setParameter(2, expId)
            .executeUpdate();
      }
      entityManager
          .createNativeQuery(
              "UPDATE injects SET inject_updated_at = ?1 WHERE inject_id IN"
                  + " (SELECT inject_id FROM injects_expectations WHERE inject_expectation_id = ?2)")
          .setParameter(1, sharedTimestamp)
          .setParameter(2, id1)
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

      // Act — batch1: cursor starts 1 ms before sharedTimestamp so only the 3 seeded
      // expectations are in scope (pre-existing data is at older timestamps).
      List<EsInjectExpectation> batch1 =
          injectExpectationHandler.fetch(sharedTimestamp.minusMillis(1), 2);
      assertThat(batch1)
          .as("first batch must return exactly 2 of the 3 same-timestamp expectations")
          .hasSize(2);

      // Act — batch2: compound cursor advances past the last item of batch1.
      String lastId = batch1.getLast().getBase_id();
      Instant lastUpdatedAt = batch1.getLast().getBase_updated_at();
      List<EsInjectExpectation> batch2 = injectExpectationHandler.fetch(lastUpdatedAt, lastId, 2);

      // Assert — all 3 IDs appear across both batches with no duplicates.
      // Filter to targetIds: batch2 may include other expectations newer than sharedTimestamp.
      Set<String> targetIds = Set.of(id1, id2, id3);
      List<String> relevantIds =
          Stream.concat(batch1.stream(), batch2.stream())
              .map(EsInjectExpectation::getBase_id)
              .filter(targetIds::contains)
              .toList();
      assertThat(relevantIds).containsExactlyInAnyOrder(id1, id2, id3);
    }
  }

  // ---------------------------------------------------------------------------
  // VulnerableEndpoint
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("VulnerableEndpointHandler.findForIndexing")
  class VulnerableEndpointIndexing {

    @Test
    @DisplayName("VulnerableEndpoint appears when its exercise was updated after :from")
    void vulnerable_endpoint_reindexed_when_exercise_updated() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(findingWrapper);
      Exercise exercise =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      List<EsVulnerableEndpoint> results = vulnerableEndpointHandler.fetch(FROM, 5000);

      Endpoint endpoint = endpointWrapper.get();
      String expectedBaseId = endpoint.getId() + "_" + exercise.getId();
      assertThat(results).anyMatch(es -> es.getBase_id().equals(expectedBaseId));
    }
  }
}
