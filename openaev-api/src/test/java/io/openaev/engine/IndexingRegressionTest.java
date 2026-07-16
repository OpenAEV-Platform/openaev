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
                  "SELECT inject_id FROM injects WHERE inject_scenario = :sid ORDER BY inject_id")
              .setParameter("sid", scenarioWrapper.get().getId())
              .getResultList();
      Instant base = FROM.plusSeconds(1);
      for (int i = 0; i < injectIds.size(); i++) {
        entityManager
            .createNativeQuery("UPDATE injects SET inject_updated_at = :ts WHERE inject_id = :id")
            .setParameter("ts", base.plusSeconds(i))
            .setParameter("id", injectIds.get(i).toString())
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

    @Test
    @DisplayName(
        "Cursor advances when dependency triggers parent inclusion (stuck cursor regression)")
    void cursor_advances_when_dependency_triggers_parent() {
      // If a dependency row is updated, the parent inject is included in changed_injects.
      // Without the WHERE GREATEST(...) > :from fix, parent's own GREATEST could be <= cursor.
      // This test ensures the cursor always advances even in this scenario.

      int total = 4;
      int batchSize = 2;
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer.forScenario(ScenarioFixture.createDefaultIncidentResponseScenario());
      Set<String> injectIds = new HashSet<>();
      for (int i = 0; i < total; i++) {
        InjectComposer.Composer inj = injectComposer.forInject(InjectFixture.getDefaultInject());
        scenarioWrapper.withInject(inj);
      }
      scenarioWrapper.persist();
      entityManager.flush();

      // Assign distinct timestamps AFTER :from so they are indexable and cursor advances
      List<?> ids =
          entityManager
              .createNativeQuery(
                  "SELECT inject_id FROM injects WHERE inject_scenario = :sid ORDER BY inject_id")
              .setParameter("sid", scenarioWrapper.get().getId())
              .getResultList();
      Instant base = FROM.plusSeconds(1);
      for (int i = 0; i < ids.size(); i++) {
        String id = ids.get(i).toString();
        entityManager
            .createNativeQuery("UPDATE injects SET inject_updated_at = :ts WHERE inject_id = :id")
            .setParameter("ts", base.plusSeconds(i))
            .setParameter("id", id)
            .executeUpdate();
        injectIds.add(id);
      }
      entityManager.flush();
      entityManager.clear();

      // Simulate cursor loop and verify advancement
      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      while (iterations < total + 3) {
        List<EsInject> batch = injectHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsInject es : batch) {
          collectedIds.add(es.getBase_id());
        }
        Instant newCursor = batch.getLast().getBase_updated_at();
        assertThat(newCursor).as("Cursor must advance (iteration %d)", iterations).isAfter(cursor);
        cursor = newCursor;
        iterations++;
      }

      assertThat(collectedIds).containsAll(injectIds);
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

      // Assign distinct timestamps on scenarios AND their injects so GREATEST is deterministic
      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE scenarios SET scenario_updated_at = :ts WHERE scenario_id = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = :ts WHERE inject_scenario = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
            .executeUpdate();
      }
      entityManager.flush();
      entityManager.clear();

      // Simulate cursor loop
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

      // Set distinct timestamps on exercises AND their injects so GREATEST is deterministic
      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE exercises SET exercise_updated_at = :ts WHERE exercise_id = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = :ts WHERE inject_exercise = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
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

      // Set distinct timestamps on endpoints AND their injects so GREATEST is deterministic
      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery("UPDATE assets SET asset_updated_at = :ts WHERE asset_id = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = :ts WHERE inject_id IN"
                    + " (SELECT inject_id FROM injects_assets WHERE asset_id = :id)")
            .setParameter("ts", ts)
            .setParameter("id", id)
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

      // Set distinct timestamps on expectations AND their injects so GREATEST is deterministic
      int i = 0;
      for (String id : expectedIds) {
        Instant ts = FROM.plusSeconds(++i);
        entityManager
            .createNativeQuery(
                "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                    + " WHERE inject_expectation_id = :id")
            .setParameter("ts", ts)
            .setParameter("id", id)
            .executeUpdate();
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = :ts WHERE inject_id = "
                    + "(SELECT inject_id FROM injects_expectations WHERE inject_expectation_id = :id)")
            .setParameter("ts", ts)
            .setParameter("id", id)
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
        "Cursor advances when child agent-expectation triggers parent inclusion (stuck cursor regression)")
    void cursor_advances_when_child_expectation_triggers_parent() {
      // Reproduces the stuck cursor bug: child (agent-level) expectations updated after :from
      // pull in parent (agentless) expectations whose own GREATEST(timestamps) <= :from.
      // Without the WHERE GREATEST(...) > :from fix, the cursor would never advance.

      int total = 3;
      int batchSize = 2;
      Set<String> expectedIds = new HashSet<>();

      for (int j = 0; j < total; j++) {
        EndpointComposer.Composer ep =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        ep.persist();

        Agent agent = AgentFixture.createDefaultAgentService();
        agent.setAsset(ep.get());
        entityManager.persist(agent);
        entityManager.flush();
        Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

        // Agentless (parent) expectation
        DetectionInjectExpectation parentExp =
            InjectExpectationFixture.createDefaultDetectionInjectExpectation();
        InjectExpectationComposer.Composer parentWrapper =
            injectExpectationComposer.forExpectation(parentExp).withEndpoint(ep);

        // Agent-level (child) expectation — the trigger
        DetectionInjectExpectation childExp =
            InjectExpectationFixture.createDefaultDetectionInjectExpectation();
        childExp.setAgent(persistedAgent);
        InjectExpectationComposer.Composer childWrapper =
            injectExpectationComposer.forExpectation(childExp).withEndpoint(ep);

        InjectComposer.Composer inj =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withExpectation(parentWrapper)
                .withExpectation(childWrapper);
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(inj)
            .persist();
        expectedIds.add(parentExp.getId());
      }
      entityManager.flush();

      // Push ALL parent expectations and their injects into the past
      for (String id : expectedIds) {
        pushExpectationToPast(id);
        entityManager
            .createNativeQuery(
                "UPDATE injects SET inject_updated_at = :ts WHERE inject_id = "
                    + "(SELECT inject_id FROM injects_expectations WHERE inject_expectation_id = :id)")
            .setParameter("ts", PAST)
            .setParameter("id", id)
            .executeUpdate();
      }
      // Touch child expectations so they are the only recent change
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                  + " WHERE agent_id IS NOT NULL")
          .setParameter("ts", Instant.now())
          .executeUpdate();
      entityManager.flush();
      entityManager.clear();

      // Act — simulate the cursor loop
      Set<String> collectedIds = new HashSet<>();
      Instant cursor = FROM;
      int iterations = 0;
      int maxIterations = total + 5;
      while (iterations < maxIterations) {
        List<EsInjectExpectation> batch = injectExpectationHandler.fetch(cursor, batchSize);
        if (batch.isEmpty()) break;
        for (EsInjectExpectation es : batch) {
          collectedIds.add(es.getBase_id());
        }
        Instant newCursor = batch.getLast().getBase_updated_at();
        // Key assertion: cursor MUST advance on every batch
        assertThat(newCursor)
            .as(
                "Cursor must advance (iteration %d, from=%s, new=%s)",
                iterations, cursor, newCursor)
            .isAfter(cursor);
        cursor = newCursor;
        iterations++;
      }

      // All parent expectations must be collected
      assertThat(collectedIds).containsAll(expectedIds);
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
      // Arrange — endpoint with a CVE finding from an inject in an exercise
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

      // Act
      List<EsVulnerableEndpoint> results = vulnerableEndpointHandler.fetch(FROM, 5000);

      // Assert — the endpoint+exercise combo must appear
      Endpoint endpoint = endpointWrapper.get();
      String expectedBaseId = endpoint.getId() + "_" + exercise.getId();
      assertThat(results).anyMatch(es -> es.getBase_id().equals(expectedBaseId));
    }
  }
}
