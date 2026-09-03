package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.openaev.IntegrationTest;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.model.asset.EsAsset;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import io.openaev.engine.query.EsEntities;
import io.openaev.scheduler.jobs.engine_sync.EngineSyncExecutionJob;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end regression tests for the {@code findForIndexing} query in {@link
 * io.openaev.database.repository.EndpointRepository}.
 *
 * <p>Unlike {@link IndexingRegressionTest} which calls handlers directly, these tests exercise the
 * full pipeline: data preparation → {@link EngineSyncExecutionJob} → ES/OpenSearch bulk indexing →
 * query via {@link EngineService}. This validates that query changes surface correctly all the way
 * to the engine layer.
 *
 * <p>Key regression covered: endpoint is detected for re-indexing when a <em>linked finding</em>
 * has {@code finding_updated_at > :from} (new UNION branch in the {@code changed_assets} CTE).
 */
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("findForIndexing end-to-end regression tests (full pipeline)")
class IndexingRegressionIntegrationTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private IndexingStatusRepository indexingStatusRepository;

  @Autowired private EndpointComposer endpointComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  /**
   * One hour ago - used as the {@code lastIndexing} cursor for incremental tests. Truncated to the
   * microsecond precision of a PostgreSQL {@code timestamp} so a round-trip through the column is
   * exact.
   */
  private static final Instant FROM =
      Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);

  /** A point in time safely before {@code FROM} - used to push timestamps into the past. */
  private static final Instant PAST = FROM.minus(1, ChronoUnit.DAYS);

  /** Admin user for {@link EngineService#entities} queries. */
  private static final String ADMIN_USER_ID = "test-admin-" + UUID.randomUUID();

  /** Admin user for {@link EngineService#entities} queries. */
  private static final RawUserAuth ADMIN_USER =
      new RawUserAuth() {
        @Override
        public String getUser_id() {
          return ADMIN_USER_ID;
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
  void resetIndexAndComposers() throws IOException {
    endpointComposer.reset();
    exerciseComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    scenarioComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    // Avoid stale incremental cursors from previous tests/suites impacting indexing behavior.
    entityManager.createNativeQuery("DELETE FROM indexing_status").executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private EsEntities queryEndpoints() {
    return queryModel("asset");
  }

  private EsEntities queryModel(String model) {
    ListConfiguration config = engineService.createListConfiguration(model, Map.of());
    // ALL_TIME avoids the DEFAULT branch that requires a dashboard timeRange parameter
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 5000));
    return engineService.entities(ADMIN_USER, runtime);
  }

  /**
   * Flushes pending JPA changes, clears the 1st-level cache, runs the job, and waits for ES async
   * indexing to complete.
   */
  private void executeJobAndWait() {
    entityManager.flush();
    entityManager.clear();
    engineService.bulkProcessing(engineContext.getModels().stream());
  }

  private void awaitEndpointIndexedAssertion(Runnable assertion) {
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(assertion::run);
  }

  /** Pins the endpoint {@code asset_updated_at} to {@code PAST}. */
  private void pushEndpointToPast(String assetId) {
    entityManager
        .createNativeQuery("UPDATE assets SET asset_updated_at = :ts WHERE asset_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", assetId)
        .executeUpdate();
  }

  /** Pins the inject {@code inject_updated_at} to {@code PAST}. */
  private void pushInjectToPast(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = :ts WHERE inject_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", injectId)
        .executeUpdate();
  }

  /** Pins the exercise {@code exercise_updated_at} to {@code PAST}. */
  private void pushExerciseToPast(String exerciseId) {
    entityManager
        .createNativeQuery("UPDATE exercises SET exercise_updated_at = :ts WHERE exercise_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", exerciseId)
        .executeUpdate();
  }

  /** Pins the scenario {@code scenario_updated_at} to {@code PAST}. */
  private void pushScenarioToPast(String scenarioId) {
    entityManager
        .createNativeQuery("UPDATE scenarios SET scenario_updated_at = :ts WHERE scenario_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", scenarioId)
        .executeUpdate();
  }

  /** Pins the finding {@code finding_updated_at} to {@code PAST}. */
  private void pushFindingToPast(String findingId) {
    entityManager
        .createNativeQuery("UPDATE findings SET finding_updated_at = :ts WHERE finding_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", findingId)
        .executeUpdate();
  }

  /** Pins the expectation {@code inject_expectation_updated_at} to {@code PAST}. */
  private void pushExpectationToPast(String expectationId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                + " WHERE inject_expectation_id = :id")
        .setParameter("ts", PAST)
        .setParameter("id", expectationId)
        .executeUpdate();
  }

  /** Pins the dependency row {@code dependency_updated_at} to {@code PAST}. */
  private void pushInjectDependencyToPast(String parentInjectId, String childInjectId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_dependencies SET dependency_updated_at = :ts"
                + " WHERE inject_parent_id = :parentId AND inject_children_id = :childId")
        .setParameter("ts", PAST)
        .setParameter("parentId", parentInjectId)
        .setParameter("childId", childInjectId)
        .executeUpdate();
  }

  private void touchInjectNow(String injectId) {
    entityManager
        .createNativeQuery("UPDATE injects SET inject_updated_at = now() WHERE inject_id = :id")
        .setParameter("id", injectId)
        .executeUpdate();
  }

  private void touchScenarioNow(String scenarioId) {
    entityManager
        .createNativeQuery(
            "UPDATE scenarios SET scenario_updated_at = now() WHERE scenario_id = :id")
        .setParameter("id", scenarioId)
        .executeUpdate();
  }

  private void touchExpectationNow(String expectationId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_updated_at = now()"
                + " WHERE inject_expectation_id = :id")
        .setParameter("id", expectationId)
        .executeUpdate();
  }

  private void touchExerciseNow(String exerciseId) {
    entityManager
        .createNativeQuery(
            "UPDATE exercises SET exercise_updated_at = now() WHERE exercise_id = :id")
        .setParameter("id", exerciseId)
        .executeUpdate();
  }

  private void touchInjectDependencyNow(String parentInjectId, String childInjectId) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_dependencies SET dependency_updated_at = now()"
                + " WHERE inject_parent_id = :parentId AND inject_children_id = :childId")
        .setParameter("parentId", parentInjectId)
        .setParameter("childId", childInjectId)
        .executeUpdate();
  }

  /**
   * Sets the {@code IndexingStatus} cursor for the "asset" model to {@code FROM} so that only
   * records updated after 1 hour ago are considered for re-indexing.
   */
  private void setEndpointIndexingStatusToFrom() {
    setIndexingStatusToFrom("asset");
  }

  private void setIndexingStatusToFrom(String type) {
    IndexingStatus status = new IndexingStatus();
    status.setType(type);
    status.setLastIndexing(FROM);
    entityManager.merge(status);
    entityManager.flush();
  }

  // ---------------------------------------------------------------------------
  // Endpoint - findings aggregation and finding-triggered indexing
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("AssetHandler.findForIndexing - full pipeline")
  class EndpointFindForIndexing {

    @Test
    @DisplayName(
        "Endpoint with a linked finding is indexed and exposes the finding ID in base_findings_side")
    void given_endpointWithLinkedFinding_should_beIndexedWithFindingIdInEngine() throws Exception {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper)
              .persist();
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryEndpoints();
            assertThat(result.getTotal()).isEqualTo(1);

            EsAsset esEndpoint =
                (EsAsset)
                    result.getEsDatas().stream()
                        .filter(e -> e.getBase_id().equals(endpointWrapper.get().getId()))
                        .findFirst()
                        .orElseThrow(
                            () ->
                                new AssertionError("Endpoint not found in engine after indexing"));

            assertThat(esEndpoint.getBase_findings_side())
                .as("base_findings_side must contain the finding ID")
                .contains(findingWrapper.get().getId());
          });
    }

    @Test
    @DisplayName(
        "Endpoint whose only recent change is a finding update is picked up by the job"
            + " (new changed_assets UNION branch)")
    void given_endpointWhoseFindingIsRecentlyUpdated_should_bePickedUpByJobEvenIfEndpointIsOld()
        throws Exception {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper)
              .persist();
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();

      // Push all timestamps into the past - nothing should appear as "recent"
      pushEndpointToPast(endpointWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      pushFindingToPast(findingWrapper.get().getId());
      pushScenarioToPast(scenarioWrapper.get().getId());

      // Set the indexing cursor to FROM so that only records updated after 1 h ago are fetched
      setEndpointIndexingStatusToFrom();

      // Touch the finding_updated_at to NOW - this is the trigger for the new UNION branch:
      //   SELECT fa.asset_id FROM findings_assets fa
      //   JOIN findings f ON fa.finding_id = f.finding_id
      //   JOIN assets a ON fa.asset_id = a.asset_id AND a.asset_type = 'Endpoint'
      //   WHERE f.finding_updated_at > :from
      entityManager
          .createNativeQuery(
              "UPDATE findings SET finding_updated_at = now() WHERE finding_id = :id")
          .setParameter("id", findingWrapper.get().getId())
          .executeUpdate();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryEndpoints();
            assertThat(result.getTotal())
                .as("endpoint must be re-indexed because its linked finding was recently updated")
                .isGreaterThanOrEqualTo(1);
            assertThat(result.getEsDatas())
                .as("the re-indexed document must correspond to the expected endpoint")
                .anyMatch(e -> e.getBase_id().equals(endpointWrapper.get().getId()));
          });
    }

    @Test
    @DisplayName(
        "Endpoint with no recent changes is not re-indexed when the indexing cursor is recent")
    void given_endpointWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent()
        throws Exception {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper)
              .persist();
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();

      // Push ALL timestamps to PAST - every UNION branch in changed_assets returns nothing
      pushEndpointToPast(endpointWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      pushFindingToPast(findingWrapper.get().getId());
      pushScenarioToPast(scenarioWrapper.get().getId());

      // Set indexing cursor to FROM - all data is older than 1 hour ago, so nothing is "new"
      setEndpointIndexingStatusToFrom();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryEndpoints().getTotal())
          .as("engine must be empty - no entity was updated after the indexing cursor")
          .isZero();
    }
  }

  @Nested
  @DisplayName("SimulationHandler.findForIndexing - full pipeline")
  class SimulationFindForIndexing {

    @Test
    @DisplayName("Exercise is indexed in simulation model")
    void given_exerciseWithInject_should_beIndexedInSimulationModel() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("simulation");
            assertThat(result.getEsDatas())
                .anyMatch(e -> e.getBase_id().equals(exerciseWrapper.get().getId()));
          });
    }

    @Test
    @DisplayName("Exercise is reindexed when linked inject is updated after cursor")
    void given_exerciseWithOldTimestamps_when_linkedInjectUpdated_should_beReindexed() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushExerciseToPast(exerciseWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("simulation");
      touchInjectNow(injectWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("simulation");
            assertThat(result.getEsDatas())
                .anyMatch(e -> e.getBase_id().equals(exerciseWrapper.get().getId()));
          });
    }

    @Test
    @DisplayName("Exercise is not reindexed when no linked row changed after cursor")
    void given_exerciseWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushExerciseToPast(exerciseWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("simulation");

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryModel("simulation").getTotal()).isZero();
    }
  }

  @Nested
  @DisplayName("InjectHandler.findForIndexing - full pipeline")
  class InjectFindForIndexing {

    @Test
    @DisplayName("Inject is indexed and linked to its scenario")
    void given_injectInScenario_should_beIndexedWithScenarioReference() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      setIndexingStatusToFrom("inject");
      touchInjectNow(injectWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("inject");
            assertThat(result.getTotal()).isGreaterThan(0);
            assertThat(result.getEsDatas())
                .anySatisfy(
                    e ->
                        assertThat(((EsInject) e).getInject_title())
                            .as("inject_title must be present in the indexed documents")
                            .isNotBlank());
          });
    }

    @Test
    @DisplayName("Inject is reindexed when dependency row is updated after cursor")
    void given_injectWithOldTimestamps_when_dependencyUpdated_should_beReindexed() {
      // -- ARRANGE --
      InjectComposer.Composer parentInjectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      InjectComposer.Composer childInjectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withDependsOn(parentInjectWrapper.get());
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(parentInjectWrapper)
          .withInject(childInjectWrapper)
          .persist();

      entityManager.flush();
      pushInjectToPast(parentInjectWrapper.get().getId());
      pushInjectToPast(childInjectWrapper.get().getId());
      pushInjectDependencyToPast(
          parentInjectWrapper.get().getId(), childInjectWrapper.get().getId());
      setIndexingStatusToFrom("inject");
      touchInjectDependencyNow(parentInjectWrapper.get().getId(), childInjectWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () ->
              assertThat(queryModel("inject").getEsDatas())
                  .anyMatch(e -> e.getBase_id().equals(parentInjectWrapper.get().getId())));
    }

    @Test
    @DisplayName("Inject is not reindexed when no recent row changed after cursor")
    void given_injectWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent() {
      // -- ARRANGE --
      InjectComposer.Composer parentInjectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      InjectComposer.Composer childInjectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withDependsOn(parentInjectWrapper.get());
      scenarioComposer
          .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
          .withInject(parentInjectWrapper)
          .withInject(childInjectWrapper)
          .persist();

      entityManager.flush();
      pushInjectToPast(parentInjectWrapper.get().getId());
      pushInjectToPast(childInjectWrapper.get().getId());
      pushInjectDependencyToPast(
          parentInjectWrapper.get().getId(), childInjectWrapper.get().getId());
      setIndexingStatusToFrom("inject");

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryModel("inject").getTotal()).isZero();
    }
  }

  @Nested
  @DisplayName("InjectExpectationHandler.findForIndexing - full pipeline")
  class InjectExpectationFindForIndexing {

    @Test
    @DisplayName("Inject expectation is indexed and linked to its inject")
    void given_expectationLinkedToInject_should_beIndexedWithInjectReference() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointWrapper);
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
      setIndexingStatusToFrom("expectation-inject");
      touchExpectationNow(expectationWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("expectation-inject");
            assertThat(result.getTotal()).isGreaterThan(0);
            assertThat(result.getEsDatas())
                .anySatisfy(
                    e ->
                        assertThat(((EsInjectExpectation) e).getBase_inject_side())
                            .as("base_inject_side doit etre present sur au moins un document")
                            .isNotBlank());
          });
    }

    @Test
    @DisplayName("Inject expectation is reindexed when linked inject is updated after cursor")
    void given_expectationWithOldTimestamps_when_linkedInjectUpdated_should_beReindexed() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointWrapper);
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
      pushExpectationToPast(expectationWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("expectation-inject");
      touchInjectNow(injectWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () ->
              assertThat(queryModel("expectation-inject").getEsDatas())
                  .anyMatch(e -> e.getBase_id().equals(expectationWrapper.get().getId())));
    }

    @Test
    @DisplayName("Inject expectation is not reindexed when no recent row changed after cursor")
    void given_expectationWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer
              .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
              .withEndpoint(endpointWrapper);
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
      pushExpectationToPast(expectationWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("expectation-inject");

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryModel("expectation-inject").getTotal()).isZero();
    }
  }

  @Nested
  @DisplayName("ScenarioHandler.findForIndexing - full pipeline")
  class ScenarioFindForIndexing {

    @Test
    @DisplayName("Scenario is indexed in scenario model")
    void given_scenarioWithInject_should_beIndexedInScenarioModel() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      setIndexingStatusToFrom("scenario");
      touchScenarioNow(scenarioWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("scenario");
            assertThat(result.getTotal()).isGreaterThan(0);
            assertThat(result.getEsDatas())
                .anySatisfy(
                    e ->
                        assertThat(((EsScenario) e).getName())
                            .as("name doit etre present dans les documents scenario")
                            .isNotBlank());
          });
    }

    @Test
    @DisplayName("Scenario is reindexed when linked inject is updated after cursor")
    void given_scenarioWithOldTimestamps_when_linkedInjectUpdated_should_beReindexed() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushScenarioToPast(scenarioWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("scenario");
      touchInjectNow(injectWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () ->
              assertThat(queryModel("scenario").getEsDatas())
                  .anyMatch(e -> e.getBase_id().equals(scenarioWrapper.get().getId())));
    }

    @Test
    @DisplayName("Scenario is not reindexed when no recent row changed after cursor")
    void given_scenarioWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent() {
      // -- ARRANGE --
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushScenarioToPast(scenarioWrapper.get().getId());
      pushInjectToPast(injectWrapper.get().getId());
      setIndexingStatusToFrom("scenario");

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryModel("scenario").getTotal()).isZero();
    }
  }

  @Nested
  @DisplayName("VulnerableEndpointHandler.findForIndexing - full pipeline")
  class VulnerableEndpointFindForIndexing {

    @Test
    @DisplayName("Vulnerable endpoint is indexed with linked finding IDs")
    void given_vulnerableEndpointWithCveFinding_should_beIndexedWithFindingIds() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      setIndexingStatusToFrom("vulnerable-endpoint");
      touchExerciseNow(exerciseWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            EsEntities result = queryModel("vulnerable-endpoint");
            assertThat(result.getTotal()).isGreaterThan(0);
            assertThat(result.getEsDatas())
                .anySatisfy(
                    e ->
                        assertThat(((EsVulnerableEndpoint) e).getBase_findings_side())
                            .as("base_findings_side doit contenir au moins un finding")
                            .isNotEmpty());
          });
    }

    @Test
    @DisplayName("Vulnerable endpoint is reindexed when linked exercise is updated after cursor")
    void given_vulnerableEndpointWithOldAsset_when_linkedExerciseUpdated_should_beReindexed() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushEndpointToPast(endpointWrapper.get().getId());
      pushExerciseToPast(exerciseWrapper.get().getId());
      setIndexingStatusToFrom("vulnerable-endpoint");
      touchExerciseNow(exerciseWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(
          () -> {
            String expectedBaseId =
                endpointWrapper.get().getId() + "_" + exerciseWrapper.get().getId();
            assertThat(queryModel("vulnerable-endpoint").getEsDatas())
                .anyMatch(e -> e.getBase_id().equals(expectedBaseId));
          });
    }

    @Test
    @DisplayName("Vulnerable endpoint is not reindexed when no recent row changed after cursor")
    void given_vulnerableEndpointWithNoRecentChanges_should_notBeReindexedWhenFromIsRecent() {
      // -- ARRANGE --
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);
      ExerciseComposer.Composer exerciseWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInject(injectWrapper)
              .persist();

      entityManager.flush();
      pushEndpointToPast(endpointWrapper.get().getId());
      pushExerciseToPast(exerciseWrapper.get().getId());
      setIndexingStatusToFrom("vulnerable-endpoint");

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(queryModel("vulnerable-endpoint").getTotal()).isZero();
    }
  }

  @Nested
  @DisplayName("Keyset paging - additive slice, no behaviour change (Story 1.1/1.2)")
  class KeysetPagingRegression {

    @Test
    @DisplayName("Existing handler persists a null indexing_status_last_id")
    void given_existingHandler_should_persistANullLastId() {
      // -- ARRANGE --
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(() -> assertThat(queryModel("asset").getTotal()).isEqualTo(1));
      assertThat(indexingStatusRepository.findByType("asset"))
          .as("none of the 13 existing handlers has opted into keyset paging yet")
          .hasValueSatisfying(status -> assertThat(status.getLastId()).isNull());
    }

    @Test
    @DisplayName("Existing handler keeps its historical cursor-advancement behaviour")
    void given_existingHandler_should_keepUnchangedCursorBehaviour() {
      // -- ARRANGE --
      // Pinned to PAST (outside the grace window) so the persisted cursor equals the row's
      // updated_at exactly, instead of being capped to now-graceWindow and clock-dependent.
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      entityManager.flush();
      pushEndpointToPast(endpointWrapper.get().getId());

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      awaitEndpointIndexedAssertion(() -> assertThat(queryModel("asset").getTotal()).isEqualTo(1));
      assertThat(indexingStatusRepository.findByType("asset"))
          .as("a timestamp-only handler must still land on the last row's updated_at, with no id")
          .hasValueSatisfying(
              status -> {
                assertThat(status.getLastIndexing()).isEqualTo(PAST);
                assertThat(status.getLastId()).isNull();
              });
    }

    @Test
    @DisplayName("Empty batch writes no indexing_status row")
    void given_emptyBatch_should_writeNoIndexingStatusRow() {
      // -- ARRANGE --
      // No endpoint fixture is created: @BeforeEach resets the endpoint composer, so the asset
      // handler's fetch is empty and the loop takes the untouched "up to date" branch (plan §2).
      assertThat(queryModel("asset").getTotal())
          .as("precondition: no seeded asset, otherwise the batch would not be empty")
          .isZero();

      // -- ACT --
      executeJobAndWait();

      // -- ASSERT --
      assertThat(indexingStatusRepository.findByType("asset"))
          .as("the empty-batch branch must write no row (locked as-is until the Epic 2 PR)")
          .isEmpty();
    }
  }
}
