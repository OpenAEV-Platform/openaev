package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_STATUS;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.Scenario;
import io.openaev.engine.model.snapshotobservation.AttackObservationHandler;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-regression tests for {@link AttackObservationRepository#findForIndexing} / {@link
 * AttackObservationHandler}, exercised directly (not through a full indexing round).
 *
 * <p>The bean only exists when the {@code BULK_SNAPSHOT_EXPORT} preview feature is enabled (see
 * {@link BulkSnapshotExportCondition}), hence the class-level {@link TestPropertySource}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@TestPropertySource(properties = "openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("AttackObservationHandler.findForIndexing")
class AttackObservationIndexingTest extends IntegrationTest {

  @Autowired private AttackObservationHandler attackObservationHandler;

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private TeamComposer teamComposer;

  /**
   * A point in time used as the {@code :from} parameter — 1 hour ago, truncated to the microsecond
   * precision of a PostgreSQL {@code timestamp} so that a round-trip through the column is exact.
   */
  private static final Instant FROM =
      Instant.now().truncatedTo(ChronoUnit.MICROS).minus(1, ChronoUnit.HOURS);

  /** A point in time safely before {@code FROM}. */
  private static final Instant PAST = FROM.minus(1, ChronoUnit.DAYS);

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    endpointComposer.reset();
    injectExpectationComposer.reset();
    collectorComposer.reset();
    securityPlatformComposer.reset();
    teamComposer.reset();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private AttackPattern newAttackPattern() {
    AttackPattern attackPattern = new AttackPattern();
    String uniqueId = UUID.randomUUID().toString();
    attackPattern.setName("Technique " + uniqueId);
    attackPattern.setExternalId("T" + uniqueId.substring(0, 8));
    attackPattern.setStixId("attack-pattern--" + uniqueId);
    return attackPattern;
  }

  /** Wires a DETECTION expectation (agentless, asset-level) on the given endpoint. */
  private InjectComposer.Composer buildDetectionInject(
      EndpointComposer.Composer endpointWrapper,
      AttackPatternComposer.Composer attackPatternWrapper,
      EXPECTATION_STATUS status) {
    return buildAssetInject(
        endpointWrapper, attackPatternWrapper, EXPECTATION_TYPE.DETECTION, status);
  }

  /** Wires an agentless, asset-level expectation of the given type on the given endpoint. */
  private InjectComposer.Composer buildAssetInject(
      EndpointComposer.Composer endpointWrapper,
      AttackPatternComposer.Composer attackPatternWrapper,
      EXPECTATION_TYPE type,
      EXPECTATION_STATUS status) {
    InjectorContractComposer.Composer contractWrapper =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withAttackPattern(attackPatternWrapper);
    BaseInjectExpectation expectation =
        InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status);
    InjectExpectationComposer.Composer expectationWrapper =
        injectExpectationComposer.forExpectation(expectation).withEndpoint(endpointWrapper);
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(contractWrapper)
        .withExpectation(expectationWrapper);
  }

  private Scenario persistScenario() {
    return scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .persist()
        .get();
  }

  private Exercise wireExercise(
      Scenario scenario, Instant start, InjectComposer.Composer... injects) {
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(start);
    exercise.setScenario(scenario);
    ExerciseComposer.Composer wrapper = exerciseComposer.forExercise(exercise);
    for (InjectComposer.Composer inject : injects) {
      wrapper.withInject(inject);
    }
    wrapper.persist();
    return exercise;
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private void bumpExpectationTimestamp(String expectationId, Instant ts) {
    entityManager
        .createNativeQuery(
            "UPDATE injects_expectations SET inject_expectation_updated_at = :ts"
                + " WHERE inject_expectation_id = :id")
        .setParameter("ts", ts)
        .setParameter("id", expectationId)
        .executeUpdate();
  }

  private void pushToPast(String table, String tsColumn, String idColumn, String id) {
    entityManager
        .createNativeQuery(
            "UPDATE " + table + " SET " + tsColumn + " = :ts WHERE " + idColumn + " = :id")
        .setParameter("ts", PAST)
        .setParameter("id", id)
        .executeUpdate();
  }

  // ---------------------------------------------------------------------------
  // Scope (FR12/FR14 — ACs 4, 5)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("scope filtering")
  class ScopeFiltering {

    @Test
    @DisplayName("an expectation from an atomic testing (no simulation) emits zero document")
    void given_atomicTestingExpectation_should_emitNoDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS)
          .persist();
      flushAndClear();

      List<EsAttackObservation> results = attackObservationHandler.fetch(FROM, 5000);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("an expectation from a simulation with no scenario emits zero document")
    void given_simulationWithNoScenario_should_emitNoDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      // No scenario linked on the exercise.
      Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now());
      exerciseComposer.forExercise(exercise).withInject(injectWrapper).persist();
      flushAndClear();

      List<EsAttackObservation> results = attackObservationHandler.fetch(FROM, 5000);

      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName(
        "an unverified (null score) expectation emits zero document, one after its verdict")
    void given_unverifiedExpectation_should_emitOnlyAfterVerdict() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.PENDING);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      assertThat(attackObservationHandler.fetch(FROM, 5000)).isEmpty();

      // First verdict lands.
      BaseInjectExpectation expectation = injectWrapper.get().getExpectations().get(0);
      entityManager
          .createNativeQuery(
              "UPDATE injects_expectations SET inject_expectation_score = 100"
                  + " WHERE inject_expectation_id = :id")
          .setParameter("id", expectation.getId())
          .executeUpdate();
      flushAndClear();

      assertThat(attackObservationHandler.fetch(FROM, 5000))
          .anyMatch(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()));
    }

    @Test
    @DisplayName("a MANUAL expectation emits zero document")
    void given_manualExpectation_should_emitNoDocument() {
      TeamComposer.Composer teamWrapper = teamComposer.forTeam(TeamFixture.getDefaultTeam());
      Inject inject = InjectFixture.getDefaultInject();
      BaseInjectExpectation manualExpectation =
          InjectExpectationFixture.createManualInjectExpectation(teamWrapper.get(), inject);
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer.forExpectation(manualExpectation).withTeam(teamWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(inject)
              .withTeam(teamWrapper)
              .withExpectation(expectationWrapper);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      assertThat(attackObservationHandler.fetch(FROM, 5000)).isEmpty();
    }

    @Test
    @DisplayName("an asset-level VULNERABILITY expectation emits zero document")
    void given_vulnerabilityExpectation_should_emitNoDocument() {
      // ARRANGE — same shape as a counted DETECTION, only the type differs, so this is the one
      // case that actually exercises the IN ('PREVENTION', 'DETECTION') filter.
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildAssetInject(
              endpointWrapper,
              attackPatternWrapper,
              EXPECTATION_TYPE.VULNERABILITY,
              EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      // ACT
      List<EsAttackObservation> results = attackObservationHandler.fetch(FROM, 5000);

      // ASSERT
      assertThat(results)
          .noneMatch(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()));
    }
  }

  // ---------------------------------------------------------------------------
  // Forward progress (FR15 — AC6) and keyset paging
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("forward progress and keyset paging")
  class ForwardProgress {

    @Test
    @DisplayName("a batch limit cutting before excluded noise still returns batchSize documents")
    void given_noiseRowsFilteredBeforeLimit_should_stillFillTheBatch() {
      Scenario scenario = persistScenario();
      List<InjectComposer.Composer> injects = new ArrayList<>();
      // 2 valid keys.
      for (int i = 0; i < 2; i++) {
        EndpointComposer.Composer endpoint =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        AttackPatternComposer.Composer attackPattern =
            attackPatternComposer.forAttackPattern(newAttackPattern());
        injects.add(buildDetectionInject(endpoint, attackPattern, EXPECTATION_STATUS.SUCCESS));
      }
      // 3 noise keys (still PENDING — excluded by the score filter before the LIMIT).
      for (int i = 0; i < 3; i++) {
        EndpointComposer.Composer endpoint =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        AttackPatternComposer.Composer attackPattern =
            attackPatternComposer.forAttackPattern(newAttackPattern());
        injects.add(buildDetectionInject(endpoint, attackPattern, EXPECTATION_STATUS.PENDING));
      }
      wireExercise(scenario, Instant.now(), injects.toArray(new InjectComposer.Composer[0]));
      flushAndClear();

      List<EsAttackObservation> batch = attackObservationHandler.fetch(FROM, null, 2);

      assertThat(batch).hasSize(2);
    }

    @Test
    @DisplayName("paging with the returned cursor makes forward progress without duplicates")
    void given_multipleKeys_when_pagingWithCursor_should_notDuplicateOrSkip() {
      Scenario scenario = persistScenario();
      List<InjectComposer.Composer> injects = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        EndpointComposer.Composer endpoint =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        AttackPatternComposer.Composer attackPattern =
            attackPatternComposer.forAttackPattern(newAttackPattern());
        injects.add(buildDetectionInject(endpoint, attackPattern, EXPECTATION_STATUS.SUCCESS));
      }
      wireExercise(scenario, Instant.now(), injects.toArray(new InjectComposer.Composer[0]));
      flushAndClear();

      List<EsAttackObservation> firstBatch = attackObservationHandler.fetch(FROM, null, 2);
      assertThat(firstBatch).hasSize(2);

      EsAttackObservation last = firstBatch.get(firstBatch.size() - 1);
      List<EsAttackObservation> secondBatch =
          attackObservationHandler.fetch(last.getBase_updated_at(), last.getBase_id(), 2);
      assertThat(secondBatch).hasSize(1);

      Set<String> allIds = new HashSet<>();
      firstBatch.forEach(es -> allIds.add(es.getBase_id()));
      secondBatch.forEach(es -> assertThat(allIds.add(es.getBase_id())).isTrue());
    }

    @Test
    @DisplayName(
        "resuming from a persisted (timestamp, lastId) cursor matches the full unpaged result")
    void given_persistedCursor_when_resumingFetch_should_matchTheFullUnpagedResult() {
      Scenario scenario = persistScenario();
      List<InjectComposer.Composer> injects = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        EndpointComposer.Composer endpoint =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        AttackPatternComposer.Composer attackPattern =
            attackPatternComposer.forAttackPattern(newAttackPattern());
        injects.add(buildDetectionInject(endpoint, attackPattern, EXPECTATION_STATUS.SUCCESS));
      }
      wireExercise(scenario, Instant.now(), injects.toArray(new InjectComposer.Composer[0]));
      flushAndClear();

      List<EsAttackObservation> fullBaseline = attackObservationHandler.fetch(FROM, 5000);
      assertThat(fullBaseline).hasSize(4);

      // Round 1, as the indexing loop would run it.
      List<EsAttackObservation> firstBatch = attackObservationHandler.fetch(FROM, null, 2);
      assertThat(firstBatch).hasSize(2);
      EsAttackObservation lastOfFirstBatch = firstBatch.get(firstBatch.size() - 1);

      // Resume exactly as EsIndexingUtils.computeKeysetCursor would persist it.
      List<EsAttackObservation> secondBatch =
          attackObservationHandler.fetch(
              lastOfFirstBatch.getBase_updated_at(), lastOfFirstBatch.getBase_id(), 2);
      assertThat(secondBatch).hasSize(2);

      List<String> resumedIds = new ArrayList<>();
      firstBatch.forEach(es -> resumedIds.add(es.getBase_id()));
      secondBatch.forEach(es -> resumedIds.add(es.getBase_id()));

      assertThat(resumedIds)
          .containsExactlyInAnyOrderElementsOf(
              fullBaseline.stream().map(EsAttackObservation::getBase_id).toList());
    }

    @Test
    @DisplayName(
        "the grace-window cap (fromId = null) idempotently re-fetches the whole tied group")
    void given_tiedWatermarkGroup_when_refetchingWithNullId_should_returnWholeGroupIdempotently() {
      Scenario scenario = persistScenario();
      List<InjectComposer.Composer> injects = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        EndpointComposer.Composer endpoint =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
        AttackPatternComposer.Composer attackPattern =
            attackPatternComposer.forAttackPattern(newAttackPattern());
        injects.add(buildDetectionInject(endpoint, attackPattern, EXPECTATION_STATUS.SUCCESS));
      }
      Exercise exercise =
          wireExercise(scenario, PAST, injects.toArray(new InjectComposer.Composer[0]));
      flushAndClear();

      // Force all three keys onto the exact same watermark by pushing inject/exercise below it
      // and pinning every expectation's own timestamp to it.
      Instant tiedTs = FROM.plusSeconds(1);
      pushToPast("exercises", "exercise_updated_at", "exercise_id", exercise.getId());
      for (InjectComposer.Composer inject : injects) {
        pushToPast("injects", "inject_updated_at", "inject_id", inject.get().getId());
        bumpExpectationTimestamp(inject.get().getExpectations().get(0).getId(), tiedTs);
      }
      flushAndClear();

      List<EsAttackObservation> baseline = attackObservationHandler.fetch(FROM, 5000);
      assertThat(baseline).hasSize(3);
      baseline.forEach(es -> assertThat(es.getBase_updated_at()).isEqualTo(tiedTs));

      List<EsAttackObservation> firstBatch = attackObservationHandler.fetch(FROM, null, 2);
      assertThat(firstBatch).hasSize(2);

      // The degraded cursor capToGraceWindow persists when the cap moves the timestamp: same
      // timestamp, no lastId. It must re-fetch the whole tied group, not just what is left of it.
      List<EsAttackObservation> reFetched = attackObservationHandler.fetch(tiedTs, null, 5000);
      assertThat(reFetched).hasSize(3);

      Set<String> merged = new HashSet<>();
      firstBatch.forEach(es -> merged.add(es.getBase_id()));
      reFetched.forEach(es -> merged.add(es.getBase_id()));
      assertThat(merged)
          .containsExactlyInAnyOrderElementsOf(
              baseline.stream().map(EsAttackObservation::getBase_id).toList());
    }
  }

  // ---------------------------------------------------------------------------
  // Fan-out (AC7)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("fan-out safety")
  class FanOut {

    @Test
    @DisplayName("a duplicate scenarios_exercises row does not multiply the document")
    void given_duplicateScenarioExerciseRow_should_notMultiplyDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise exercise = wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      entityManager
          .createNativeQuery(
              "INSERT INTO scenarios_exercises (exercise_id, scenario_id) VALUES (:exerciseId,"
                  + " :scenarioId) ON CONFLICT DO NOTHING")
          .setParameter("exerciseId", exercise.getId())
          .setParameter("scenarioId", scenario.getId())
          .executeUpdate();
      flushAndClear();

      List<EsAttackObservation> results = attackObservationHandler.fetch(FROM, 5000);

      assertThat(results)
          .filteredOn(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
          .hasSize(1);
    }
  }

  // ---------------------------------------------------------------------------
  // Latest replay (FR18 — AC8)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("latest verified replay")
  class LatestReplay {

    @Test
    @DisplayName("a newer PENDING replay does not blank the older verified replay")
    void given_newerPendingReplay_should_keepOlderVerifiedReplay() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      InjectComposer.Composer olderInjectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise olderExercise =
          wireExercise(scenario, Instant.now().minus(2, ChronoUnit.DAYS), olderInjectWrapper);

      InjectComposer.Composer newerInjectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.PENDING);
      wireExercise(scenario, Instant.now(), newerInjectWrapper);
      flushAndClear();

      List<EsAttackObservation> results = attackObservationHandler.fetch(FROM, 5000);

      EsAttackObservation doc =
          results.stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();
      assertThat(doc.getBase_simulation_side()).isEqualTo(olderExercise.getId());
      assertThat(doc.getAttack_observation_status()).isEqualTo(EXPECTATION_STATUS.SUCCESS.name());
    }
  }

  // ---------------------------------------------------------------------------
  // Counters (FR16/FR17 — AC9)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("counters and status")
  class Counters {

    private EsAttackObservation fetchWithAttempts(int successCount, int failedCount) {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      List<InjectComposer.Composer> injects = new ArrayList<>();
      for (int i = 0; i < successCount; i++) {
        injects.add(
            buildDetectionInject(
                endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS));
      }
      for (int i = 0; i < failedCount; i++) {
        injects.add(
            buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.FAILED));
      }
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injects.toArray(new InjectComposer.Composer[0]));
      flushAndClear();

      return attackObservationHandler.fetch(FROM, 5000).stream()
          .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
          .findFirst()
          .orElseThrow();
    }

    @Test
    @DisplayName("3/3 success yields SUCCESS with ratio 1.0")
    void given_threeOutOfThreeSuccess_should_beSuccess() {
      EsAttackObservation doc = fetchWithAttempts(3, 0);
      assertThat(doc.getAttack_observation_status()).isEqualTo(EXPECTATION_STATUS.SUCCESS.name());
      assertThat(doc.getAttack_observation_attempts_total()).isEqualTo(3L);
      assertThat(doc.getAttack_observation_attempts_success()).isEqualTo(3L);
      assertThat(doc.getAttack_observation_coverage_ratio()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("0/3 success yields FAILED with ratio 0.0")
    void given_zeroOutOfThreeSuccess_should_beFailed() {
      EsAttackObservation doc = fetchWithAttempts(0, 3);
      assertThat(doc.getAttack_observation_status()).isEqualTo(EXPECTATION_STATUS.FAILED.name());
      assertThat(doc.getAttack_observation_coverage_ratio()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("1/3 success yields PARTIAL with ratio ~0.333")
    void given_oneOutOfThreeSuccess_should_bePartial() {
      EsAttackObservation doc = fetchWithAttempts(1, 2);
      assertThat(doc.getAttack_observation_status()).isEqualTo(EXPECTATION_STATUS.PARTIAL.name());
      assertThat(doc.getAttack_observation_coverage_ratio()).isCloseTo(0.333, within(0.01));
      assertThat(doc.getAttack_observation_status())
          .isNotIn(EXPECTATION_STATUS.PENDING.name(), EXPECTATION_STATUS.UNKNOWN.name());
    }

    @Test
    @DisplayName(
        "an unverified sibling attempt is neither counted nor reflected in last_verified_at")
    void given_unverifiedSiblingAttempt_should_notCountNorMoveLastVerifiedAt() {
      // ARRANGE
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer verified =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      InjectComposer.Composer unverified =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.PENDING);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), verified, unverified);
      flushAndClear();
      // A score-less sibling is out of scope entirely (FR14), so touching it moves neither the
      // watermark nor last_verified_at.
      Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
      bumpExpectationTimestamp(unverified.get().getExpectations().get(0).getId(), future);
      flushAndClear();

      // ACT
      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      // ASSERT
      assertThat(doc.getAttack_observation_attempts_total()).isEqualTo(1L);
      assertThat(doc.getAttack_observation_last_verified_at()).isBefore(future);
      assertThat(doc.getBase_updated_at()).isBefore(future);
    }
  }

  // ---------------------------------------------------------------------------
  // Security platforms (FR19 — AC10)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("security platform attribution")
  class SecurityPlatforms {

    @Test
    @DisplayName("a collector-sourced result attributes its security platform")
    void given_collectorSourcedResult_should_attributeSecurityPlatform() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      SecurityPlatformComposer.Composer securityPlatform =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR", "EDR"))
              .persist();
      Collector collector =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector("collector-" + UUID.randomUUID()))
              .withSecurityPlatform(securityPlatform)
              .persist()
              .get();

      BaseInjectExpectation expectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS);
      expectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collector.getId())
                  .sourceType("collector")
                  .sourceName(collector.getName())
                  .result("detected")
                  .score(100.0)
                  .build()));
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer.forExpectation(expectation).withEndpoint(endpointWrapper);
      InjectorContractComposer.Composer contractWrapper =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withAttackPattern(attackPatternWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contractWrapper)
              .withExpectation(expectationWrapper);

      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_security_platforms_side()).contains(securityPlatform.get().getId());
      assertThat(doc.getAttack_observation_platforms_succeeded())
          .contains(securityPlatform.get().getId());
    }

    @Test
    @DisplayName("a below-expected score is attributed but not counted as succeeded")
    void given_belowExpectedScore_should_notBeInSucceededSet() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      SecurityPlatformComposer.Composer securityPlatform =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR miss", "EDR"))
              .persist();
      Collector collector =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector("collector-" + UUID.randomUUID()))
              .withSecurityPlatform(securityPlatform)
              .persist()
              .get();

      DetectionInjectExpectation expectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      expectation.setScore(0.0);
      expectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collector.getId())
                  .sourceType("collector")
                  .sourceName(collector.getName())
                  .result("not detected")
                  .score(0.0)
                  .build()));
      InjectExpectationComposer.Composer expectationWrapper =
          injectExpectationComposer.forExpectation(expectation).withEndpoint(endpointWrapper);
      InjectorContractComposer.Composer contractWrapper =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withAttackPattern(attackPatternWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contractWrapper)
              .withExpectation(expectationWrapper);

      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_security_platforms_side()).contains(securityPlatform.get().getId());
      assertThat(doc.getAttack_observation_platforms_succeeded())
          .doesNotContain(securityPlatform.get().getId());
    }

    @Test
    @DisplayName(
        "an agent-level child result attributes its security platform to the agentless parent")
    void given_agentChildResult_should_attributeSecurityPlatformToParent() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      endpointWrapper.persist();
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      SecurityPlatformComposer.Composer securityPlatform =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR agent", "EDR"))
              .persist();
      Collector collector =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector("collector-" + UUID.randomUUID()))
              .withSecurityPlatform(securityPlatform)
              .persist()
              .get();

      // Parent: agentless, carries no result of its own.
      BaseInjectExpectation parentExpectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS);
      InjectExpectationComposer.Composer parentWrapper =
          injectExpectationComposer.forExpectation(parentExpectation).withEndpoint(endpointWrapper);

      // Agent-level child on the SAME inject, carrying the collector-sourced result.
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpointWrapper.get());
      entityManager.persist(agent);
      entityManager.flush();
      Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

      DetectionInjectExpectation childExpectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      childExpectation.setAgent(persistedAgent);
      childExpectation.setAsset(endpointWrapper.get());
      childExpectation.setScore(100.0);
      childExpectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collector.getId())
                  .sourceType("collector")
                  .sourceName(collector.getName())
                  .result("detected")
                  .score(100.0)
                  .build()));
      InjectExpectationComposer.Composer childWrapper =
          injectExpectationComposer.forExpectation(childExpectation);

      InjectorContractComposer.Composer contractWrapper =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withAttackPattern(attackPatternWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contractWrapper)
              .withExpectation(parentWrapper)
              .withExpectation(childWrapper);

      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_security_platforms_side()).contains(securityPlatform.get().getId());
      assertThat(doc.getAttack_observation_platforms_succeeded())
          .contains(securityPlatform.get().getId());
    }

    @Test
    @DisplayName(
        "parent and agent-level child results are both attributed with an independent succeeded subset")
    void given_parentAndAgentChildResults_should_attributeBothIndependently() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      endpointWrapper.persist();
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      SecurityPlatformComposer.Composer platformA =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR A", "EDR"))
              .persist();
      Collector collectorA =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector("collector-a-" + UUID.randomUUID()))
              .withSecurityPlatform(platformA)
              .persist()
              .get();
      SecurityPlatformComposer.Composer platformB =
          securityPlatformComposer
              .forSecurityPlatform(SecurityPlatformFixture.createDefault("EDR B", "EDR"))
              .persist();
      Collector collectorB =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector("collector-b-" + UUID.randomUUID()))
              .withSecurityPlatform(platformB)
              .persist()
              .get();

      // Parent result: platform A, succeeds.
      BaseInjectExpectation parentExpectation =
          InjectExpectationFixture.createExpectationWithTypeAndStatus(
              EXPECTATION_TYPE.DETECTION, EXPECTATION_STATUS.SUCCESS);
      parentExpectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collectorA.getId())
                  .sourceType("collector")
                  .sourceName(collectorA.getName())
                  .result("detected")
                  .score(100.0)
                  .build()));
      InjectExpectationComposer.Composer parentWrapper =
          injectExpectationComposer.forExpectation(parentExpectation).withEndpoint(endpointWrapper);

      // Agent-level child result: platform B, below expected score.
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpointWrapper.get());
      entityManager.persist(agent);
      entityManager.flush();
      Agent persistedAgent = entityManager.getReference(Agent.class, agent.getId());

      DetectionInjectExpectation childExpectation =
          InjectExpectationFixture.createDefaultDetectionInjectExpectation();
      childExpectation.setAgent(persistedAgent);
      childExpectation.setAsset(endpointWrapper.get());
      childExpectation.setScore(0.0);
      childExpectation.setResults(
          List.of(
              InjectExpectationResult.builder()
                  .sourceId(collectorB.getId())
                  .sourceType("collector")
                  .sourceName(collectorB.getName())
                  .result("not detected")
                  .score(0.0)
                  .build()));
      InjectExpectationComposer.Composer childWrapper =
          injectExpectationComposer.forExpectation(childExpectation);

      InjectorContractComposer.Composer contractWrapper =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withAttackPattern(attackPatternWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(contractWrapper)
              .withExpectation(parentWrapper)
              .withExpectation(childWrapper);

      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_security_platforms_side())
          .contains(platformA.get().getId(), platformB.get().getId());
      assertThat(doc.getAttack_observation_platforms_succeeded())
          .contains(platformA.get().getId())
          .doesNotContain(platformB.get().getId());
    }
  }

  // ---------------------------------------------------------------------------
  // Determinism and identity (AC11)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("determinism and identity")
  class Determinism {

    @Test
    @DisplayName("two independent calls produce the same base_id for the same key")
    void given_sameKey_when_calledTwice_should_produceTheSameBaseId() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      String firstId =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow()
              .getBase_id();
      String secondId =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow()
              .getBase_id();

      assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    @DisplayName("replaying the scenario updates the same document, not a new one")
    void given_secondReplay_should_updateTheSameDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer firstInject =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now().minus(1, ChronoUnit.DAYS), firstInject);
      flushAndClear();

      String firstId =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow()
              .getBase_id();

      InjectComposer.Composer secondInject =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.FAILED);
      wireExercise(scenario, Instant.now(), secondInject);
      flushAndClear();

      List<EsAttackObservation> resultsAfterReplay =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .toList();

      assertThat(resultsAfterReplay).hasSize(1);
      assertThat(resultsAfterReplay.get(0).getBase_id()).isEqualTo(firstId);
    }
  }

  // ---------------------------------------------------------------------------
  // Watermark (FR9 — AC12)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("watermark composition")
  class Watermark {

    @Test
    @DisplayName("an asset heartbeat (asset_updated_at) alone does not surface the document")
    void given_assetHeartbeatOnly_should_notEmitDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise exercise = wireExercise(scenario, PAST, injectWrapper);
      flushAndClear();

      // Push every relevant timestamp into the past...
      pushToPast(
          "injects_expectations",
          "inject_expectation_updated_at",
          "inject_expectation_id",
          injectWrapper.get().getExpectations().get(0).getId());
      pushToPast("injects", "inject_updated_at", "inject_id", injectWrapper.get().getId());
      pushToPast("exercises", "exercise_updated_at", "exercise_id", exercise.getId());
      // ...then simulate an agent heartbeat on the asset only.
      entityManager
          .createNativeQuery("UPDATE assets SET asset_updated_at = now() WHERE asset_id = :id")
          .setParameter("id", endpointWrapper.get().getId())
          .executeUpdate();
      flushAndClear();

      assertThat(attackObservationHandler.fetch(FROM, 5000))
          .noneMatch(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()));
    }

    @Test
    @DisplayName("renaming the scenario alone does not surface the document")
    void given_scenarioRenameOnly_should_notEmitDocument() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise exercise = wireExercise(scenario, PAST, injectWrapper);
      flushAndClear();

      // Push every relevant timestamp into the past...
      pushToPast(
          "injects_expectations",
          "inject_expectation_updated_at",
          "inject_expectation_id",
          injectWrapper.get().getExpectations().get(0).getId());
      pushToPast("injects", "inject_updated_at", "inject_id", injectWrapper.get().getId());
      pushToPast("exercises", "exercise_updated_at", "exercise_id", exercise.getId());
      // ...then rename the scenario only: scenario_updated_at is deliberately excluded from the
      // watermark's GREATEST, otherwise every scenario edit would re-emit all its documents.
      entityManager
          .createNativeQuery(
              "UPDATE scenarios SET scenario_name = :name, scenario_updated_at = now()"
                  + " WHERE scenario_id = :id")
          .setParameter("name", "Renamed scenario")
          .setParameter("id", scenario.getId())
          .executeUpdate();
      flushAndClear();

      assertThat(attackObservationHandler.fetch(FROM, 5000))
          .noneMatch(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()));
    }

    @Test
    @DisplayName(
        "bumping an older attempt's timestamp out of order does not change the latest replay")
    void given_outOfOrderBumpOnOlderAttempt_should_stillReflectTheLatestReplay() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());

      InjectComposer.Composer olderInjectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.FAILED);
      Scenario scenario = persistScenario();
      wireExercise(scenario, Instant.now().minus(2, ChronoUnit.DAYS), olderInjectWrapper);

      InjectComposer.Composer newerInjectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Exercise newerExercise = wireExercise(scenario, Instant.now(), newerInjectWrapper);
      flushAndClear();

      // Out-of-order bump: the OLDER attempt's own timestamp moves into the future, well past
      // the newer exercise's own timestamps.
      bumpExpectationTimestamp(
          olderInjectWrapper.get().getExpectations().get(0).getId(),
          Instant.now().plus(1, ChronoUnit.HOURS));
      flushAndClear();

      // The exercise picked as "latest" is chosen by exercise_start_date/exercise_id, not by
      // inject_expectation_updated_at, so it must still be the newer one.
      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_simulation_side()).isEqualTo(newerExercise.getId());
      assertThat(doc.getAttack_observation_status()).isEqualTo(EXPECTATION_STATUS.SUCCESS.name());
    }
  }

  // ---------------------------------------------------------------------------
  // Dependencies (AC13)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("dependencies")
  class Dependencies {

    @Test
    @DisplayName("base_dependencies contains exactly the asset and the scenario, not the exercise")
    void given_document_should_dependOnAssetAndScenarioOnly() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise exercise = wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      assertThat(doc.getBase_dependencies())
          .containsExactlyInAnyOrder(endpointWrapper.get().getId(), scenario.getId())
          .doesNotContain(exercise.getId());
    }
  }

  // ---------------------------------------------------------------------------
  // ACL
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("acl")
  class Acl {

    @Test
    @DisplayName("base_restrictions carries the grantable scenario and simulation, not the asset")
    void given_document_should_restrictOnGrantableResourcesOnly() {
      // ARRANGE
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      AttackPatternComposer.Composer attackPatternWrapper =
          attackPatternComposer.forAttackPattern(newAttackPattern());
      InjectComposer.Composer injectWrapper =
          buildDetectionInject(endpointWrapper, attackPatternWrapper, EXPECTATION_STATUS.SUCCESS);
      Scenario scenario = persistScenario();
      Exercise exercise = wireExercise(scenario, Instant.now(), injectWrapper);
      flushAndClear();

      // ACT
      EsAttackObservation doc =
          attackObservationHandler.fetch(FROM, 5000).stream()
              .filter(es -> es.getBase_asset_side().equals(endpointWrapper.get().getId()))
              .findFirst()
              .orElseThrow();

      // ASSERT
      // Only Grant.grant_resource ids can ever match the ACL clause, so an asset id here would
      // make the document invisible to every non-admin.
      assertThat(doc.getBase_restrictions())
          .containsExactlyInAnyOrder(scenario.getId(), exercise.getId())
          .doesNotContain(endpointWrapper.get().getId());
    }
  }
}
