package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStateEntries;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency tests (ADR-011) of {@link WorkflowStateStore} with real, independent transactions —
 * deliberately NOT {@code @Transactional}: the data is committed and removed in {@link #tearDown}.
 *
 * <p>Several syncs append to the same global state at the same time, typically with overlapping
 * values (two injects discovering the same hosts). Each multi-row {@code INSERT ... ON CONFLICT}
 * locks the unique-index keys it inserts in row order: two transactions inserting the same keys in
 * opposite orders would deadlock, and PostgreSQL would abort one of them — losing that sync's
 * values. The store must insert rows in a deterministic order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("WorkflowStateStore under concurrency")
class WorkflowStateStoreConcurrencyTest extends IntegrationTest {

  private static final int ROUNDS = 20;
  private static final int VALUES_PER_ROUND = 500;

  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private PlatformTransactionManager transactionManager;

  private TransactionTemplate transactionTemplate;
  private Workflow workflowRun;

  @BeforeEach
  void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManager);
    workflowRun =
        transactionTemplate.execute(
            status ->
                workflowComposer
                    .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
                    .withSimulation(
                        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
                    .persist()
                    .get());
  }

  @AfterEach
  void tearDown() {
    // Deleting the simulation cascades to its workflows, their states and their entries.
    transactionTemplate.executeWithoutResult(
        status ->
            entityManager
                .createNativeQuery("DELETE FROM exercises WHERE exercise_id = :id")
                .setParameter("id", workflowRun.getSimulation().getId())
                .executeUpdate());
  }

  private static WorkflowStateEntries inputs(List<String> orderedValues) {
    WorkflowStateEntries delta = WorkflowStateEntries.empty();
    delta
        .getInputs()
        .add(new WorkflowStateEntries.Input("IPv4", new LinkedHashSet<>(orderedValues)));
    return delta;
  }

  private static WorkflowStateEntries tuples(List<String> orderedValues) {
    WorkflowStateEntries delta = WorkflowStateEntries.empty();
    for (String value : orderedValues) {
      delta
          .getCorrelated()
          .add(
              new WorkflowStateEntries.Correlated(
                  new LinkedHashSet<>(
                      List.of(
                          new WorkflowStateEntries.Pair("IPv4", value),
                          new WorkflowStateEntries.Pair("Port", "22"))),
                  "PortsScan"));
    }
    return delta;
  }

  /**
   * Runs both writers at the same moment, each in its own transaction, and rethrows any failure.
   */
  private void runConcurrently(Runnable first, Runnable second) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<?>> futures =
          List.of(
              pool.submit(
                  () -> {
                    start.await();
                    transactionTemplate.executeWithoutResult(status -> first.run());
                    return null;
                  }),
              pool.submit(
                  () -> {
                    start.await();
                    transactionTemplate.executeWithoutResult(status -> second.run());
                    return null;
                  }));
      start.countDown();
      for (Future<?> future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static List<String> roundValues(int round) {
    return IntStream.range(0, VALUES_PER_ROUND)
        .mapToObj(i -> "10." + round + "." + (i / 256) + "." + (i % 256))
        .toList();
  }

  @Nested
  @DisplayName("concurrent appends to the same state")
  class ConcurrentAppends {

    @Test
    @DisplayName("overlapping inputs appended in opposite orders never deadlock nor lose values")
    void given_overlappingInputsInOppositeOrders_should_storeEveryValue() throws Exception {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);

      // Act
      for (int round = 0; round < ROUNDS; round++) {
        List<String> ascending = roundValues(round);
        List<String> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        runConcurrently(
            () -> workflowStateStore.append(stateId, inputs(ascending)),
            () -> workflowStateStore.append(stateId, inputs(descending)));
      }

      // Assert
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("IPv4")))
          .hasSize(ROUNDS * VALUES_PER_ROUND);
    }

    @Test
    @DisplayName("overlapping tuples appended in opposite orders never deadlock nor lose tuples")
    void given_overlappingTuplesInOppositeOrders_should_storeEveryTuple() throws Exception {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);

      // Act
      for (int round = 0; round < ROUNDS; round++) {
        List<String> ascending = roundValues(round);
        List<String> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        runConcurrently(
            () -> workflowStateStore.append(stateId, tuples(ascending)),
            () -> workflowStateStore.append(stateId, tuples(descending)));
      }

      // Assert
      assertThat(workflowStateStore.load(stateId, Set.of("Port"), true, false).getCorrelated())
          .hasSize(ROUNDS * VALUES_PER_ROUND);
    }

    @Test
    @DisplayName("overlapping hashes committed in opposite orders are committed exactly once")
    void given_overlappingHashesInOppositeOrders_should_commitEachHashOnce() throws Exception {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      Map<String, Integer> commits = new ConcurrentHashMap<>();

      // Act
      for (int round = 0; round < ROUNDS; round++) {
        List<String> ascending = roundValues(round);
        List<String> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        runConcurrently(
            () ->
                workflowStateStore
                    .commitExecutionHashes(stateId, ascending)
                    .forEach(hash -> commits.merge(hash, 1, Integer::sum)),
            () ->
                workflowStateStore
                    .commitExecutionHashes(stateId, descending)
                    .forEach(hash -> commits.merge(hash, 1, Integer::sum)));
      }

      // Assert — every hash committed, and by exactly one of the two evaluations (anti-replay)
      assertThat(commits).hasSize(ROUNDS * VALUES_PER_ROUND);
      assertThat(commits.values()).containsOnly(1);
    }
  }
}
