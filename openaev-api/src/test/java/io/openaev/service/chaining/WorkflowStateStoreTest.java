package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntries.Pair;
import io.openaev.database.repository.StepRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.*;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests (ADR-011) of {@link WorkflowStateStore} against the real {@code
 * workflow_state_entries} table: state creation, append-only writes with database-level
 * deduplication, key-restricted reads, anti-replay hash commits, cascade deletion, and the number
 * of SQL statements each operation costs (read from Hibernate {@link Statistics}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("WorkflowStateStore")
class WorkflowStateStoreTest extends IntegrationTest {

  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepRepository stepRepository;

  private Workflow workflowRun;
  private Step stepTemplate;

  @BeforeEach
  void setUp() {
    workflowRun =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
            .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    stepTemplate =
        stepRepository.save(
            Step.builder()
                .stepAction(StepActionClass.INJECT_EXECUTION)
                .status(StepStatus.TEMPLATE)
                .workflow(workflowRun)
                .limitExecution(1)
                .build());
  }

  private static WorkflowStateEntries.Correlated tuple(String type, Pair... pairs) {
    return new WorkflowStateEntries.Correlated(new HashSet<>(Set.of(pairs)), type);
  }

  private static WorkflowStateEntries delta(
      Map<String, Set<String>> inputs, WorkflowStateEntries.Correlated... tuples) {
    WorkflowStateEntries delta = WorkflowStateEntries.empty();
    inputs.forEach((key, values) -> delta.getInputByKey(key).getValues().addAll(values));
    delta.getCorrelated().addAll(List.of(tuples));
    return delta;
  }

  private long rowCount(String stateId, String entryType) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM workflow_state_entries"
                        + " WHERE workflow_state_id = :stateId AND entry_type = :entryType")
                .setParameter("stateId", stateId)
                .setParameter("entryType", entryType)
                .getSingleResult())
        .longValue();
  }

  private boolean stateExists(String stateId) {
    return ((Number)
                entityManager
                    .createNativeQuery(
                        "SELECT count(*) FROM workflow_states WHERE workflow_state_id = :stateId")
                    .setParameter("stateId", stateId)
                    .getSingleResult())
            .longValue()
        > 0;
  }

  private record SqlCost(long statements, long entityLoads) {}

  /** Runs {@code action} on a flushed, empty persistence context and returns what it cost. */
  private SqlCost sqlCostOf(Runnable action) {
    entityManager.flush();
    entityManager.clear();
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    boolean wasEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    try {
      stats.clear();
      action.run();
      return new SqlCost(stats.getPrepareStatementCount(), stats.getEntityLoadCount());
    } finally {
      stats.setStatisticsEnabled(wasEnabled);
    }
  }

  @Nested
  @DisplayName("states")
  class States {

    @Test
    @DisplayName("getOrCreateGlobalStateId creates the global state once")
    void given_twoCalls_should_createTheGlobalStateOnce() {
      // Act
      String first = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String second = workflowStateStore.getOrCreateGlobalStateId(workflowRun);

      // Assert
      assertThat(second).isEqualTo(first);
      assertThat(workflowStateStore.findGlobalStateId(workflowRun.getId())).contains(first);
    }

    @Test
    @DisplayName("getOrCreateLocalStateId creates one local state per step template")
    void given_twoCalls_should_createOneLocalStatePerStepTemplate() {
      // Arrange
      String global = workflowStateStore.getOrCreateGlobalStateId(workflowRun);

      // Act
      String first = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      String second = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);

      // Assert
      assertThat(second).isEqualTo(first).isNotEqualTo(global);
      assertThat(workflowStateStore.findLocalStateId(stepTemplate.getId(), workflowRun.getId()))
          .contains(first);
    }

    @Test
    @DisplayName("deleteAllBySimulationId deletes the states and, by cascade, their entries")
    void given_statesWithEntries_should_deleteThemAll() {
      // Arrange
      String global = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String local = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(global, delta(Map.of("IPv4", Set.of("10.0.0.1"))));
      workflowStateStore.commitExecutionHashes(local, Set.of("h1"));

      // Act
      int deleted = workflowStateStore.deleteAllBySimulationId(workflowRun.getSimulation().getId());

      // Assert
      assertThat(deleted).isEqualTo(2);
      assertThat(stateExists(global)).isFalse();
      assertThat(stateExists(local)).isFalse();
      assertThat(rowCount(global, "INPUT")).isZero();
      assertThat(rowCount(local, "HASH_EXECUTION")).isZero();
    }
  }

  @Nested
  @DisplayName("append and load")
  class AppendAndLoad {

    @Test
    @DisplayName("a view holds only the inputs of the requested keys")
    void given_inputsOfSeveralKeys_should_loadOnlyTheRequestedOnes() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(Map.of("IPv4", Set.of("10.0.0.1", "10.0.0.2"), "Username", Set.of("admin"))));

      // Act
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4"), true, false);

      // Assert
      assertThat(view.getInputs()).hasSize(1);
      assertThat(view.getInputByKey("IPv4").getValues())
          .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    @DisplayName("a view holds every field of the tuples sharing a requested key, and only those")
    void given_tuples_should_loadTheCandidatesWithAllTheirFields() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of(),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80")),
              tuple("Credentials", new Pair("Username", "u"), new Pair("Password", "p"))));

      // Act
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("Port"), true, false);

      // Assert
      assertThat(view.getCorrelated()).hasSize(1);
      WorkflowStateEntries.Correlated tuple = view.getCorrelated().getFirst();
      assertThat(tuple.getType()).isEqualTo("PortsScan");
      assertThat(tuple.getValues())
          .containsExactlyInAnyOrder(new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80"));
    }

    @Test
    @DisplayName("without withCorrelated, a view holds the inputs only")
    void given_noCorrelatedRequested_should_loadInputsOnly() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("IPv4", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80"))));

      // Act
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4"), false, false);

      // Assert
      assertThat(view.getInputByKey("IPv4").getValues()).containsExactly("10.0.0.1");
      assertThat(view.getCorrelated()).isEmpty();
    }

    @Test
    @DisplayName("tuples sharing a field value are kept as distinct tuples")
    void given_tuplesSharingAValue_should_keepThemDistinct() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of(),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80")),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "443"))));

      // Act
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4"), true, false);

      // Assert
      assertThat(view.getCorrelated()).hasSize(2);
    }

    @Test
    @DisplayName("appending again an input or a tuple already stored adds nothing")
    void given_entriesAppendedTwice_should_storeThemOnce() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("Host", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("Host", "10.0.0.1"), new Pair("Port", "22"))));

      // Act — same input, same tuple with its pairs in another order
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("Host", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("Port", "22"), new Pair("Host", "10.0.0.1"))));

      // Assert
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("Host"), true, false);
      assertThat(view.getInputByKey("Host").getValues()).containsExactly("10.0.0.1");
      assertThat(view.getCorrelated()).hasSize(1);
      assertThat(rowCount(stateId, "CORRELATED")).isEqualTo(2);
    }

    @Test
    @DisplayName("values with array-literal and separator characters are stored and read verbatim")
    void given_valuesWithSpecialCharacters_should_roundTripVerbatim() {
      // Arrange — values come from targeted machines' outputs: any character may show up
      Set<String> values =
          Set.of(
              "a,b",
              "\"quoted\"",
              "{curly}",
              "back\\slash",
              "NULL",
              "null",
              "x|Port=y",
              "",
              " padded ",
              "ünïcødé");
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      WorkflowStateEntries.Correlated[] tuples =
          values.stream()
              .map(
                  v ->
                      tuple("Credentials", new Pair("Username", "u-" + v), new Pair("Password", v)))
              .toArray(WorkflowStateEntries.Correlated[]::new);

      // Act
      workflowStateStore.append(stateId, delta(Map.of("Password", values), tuples));

      // Assert
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("Password")))
          .containsExactlyInAnyOrderElementsOf(values);
      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("Password"), true, false);
      assertThat(view.getCorrelated()).hasSize(values.size());
      assertThat(view.getCorrelated())
          .allSatisfy(
              t -> {
                String password =
                    t.getValues().stream()
                        .filter(p -> p.key().equals("Password"))
                        .findFirst()
                        .orElseThrow()
                        .value();
                assertThat(t.getValues()).contains(new Pair("Username", "u-" + password));
              });
    }

    @Test
    @DisplayName("hashes are loaded only when requested")
    void given_committedHashes_should_loadThemOnlyWhenRequested() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.commitExecutionHashes(stateId, Set.of("h1"));

      // Act + Assert
      assertThat(workflowStateStore.load(stateId, Set.of(), false, false).getHashExecution())
          .isEmpty();
      assertThat(workflowStateStore.load(stateId, Set.of(), false, true).getHashExecution())
          .containsExactly("h1");
    }
  }

  @Nested
  @DisplayName("execution hashes")
  class ExecutionHashes {

    @Test
    @DisplayName("commitExecutionHashes returns only the hashes not committed before")
    void given_partlyCommittedHashes_should_returnOnlyTheNewOnes() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);

      // Act
      Set<String> first = workflowStateStore.commitExecutionHashes(stateId, Set.of("h1", "h2"));
      Set<String> second = workflowStateStore.commitExecutionHashes(stateId, Set.of("h2", "h3"));

      // Assert
      assertThat(first).containsExactlyInAnyOrder("h1", "h2");
      assertThat(second).containsExactly("h3");
      assertThat(workflowStateStore.loadExecutionHashes(stateId))
          .containsExactlyInAnyOrder("h1", "h2", "h3");
    }

    @Test
    @DisplayName("clearExecutionHashes removes the hashes only, so the step can fire again")
    void given_clearedHashes_should_keepInputsAndAllowRecommit() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(stateId, delta(Map.of("IPv4", Set.of("10.0.0.1"))));
      workflowStateStore.commitExecutionHashes(stateId, Set.of("h1"));

      // Act
      workflowStateStore.clearExecutionHashes(stateId);

      // Assert
      assertThat(workflowStateStore.loadExecutionHashes(stateId)).isEmpty();
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("IPv4")))
          .containsExactly("10.0.0.1");
      assertThat(workflowStateStore.commitExecutionHashes(stateId, Set.of("h1")))
          .containsExactly("h1");
    }
  }

  @Nested
  @DisplayName("SQL cost")
  class SqlCostTests {

    @Test
    @DisplayName("an append costs one statement per entry type, whatever the state's history")
    void given_appendToEmptyOrLargeState_should_costTheSameStatements() {
      // Arrange
      String emptyState = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String largeState = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(
          largeState,
          delta(
              Map.of(
                  "IPv4",
                  new HashSet<>(
                      IntStream.range(0, 2000)
                          .mapToObj(i -> "10.9." + i / 256 + "." + i % 256)
                          .toList()))));
      WorkflowStateEntries newEntries =
          delta(
              Map.of("IPv4", Set.of("172.16.0.1")),
              tuple("PortsScan", new Pair("IPv4", "172.16.0.1"), new Pair("Port", "22")));

      // Act
      SqlCost onEmpty = sqlCostOf(() -> workflowStateStore.append(emptyState, newEntries));
      SqlCost onLarge = sqlCostOf(() -> workflowStateStore.append(largeState, newEntries));

      // Assert — no read of the existing state, one insert for inputs and one for tuples
      assertThat(onEmpty).isEqualTo(new SqlCost(2, 0));
      assertThat(onLarge).isEqualTo(onEmpty);
    }

    @Test
    @DisplayName("a mapper view costs one statement per part: inputs, tuples (two), hashes")
    void given_fullView_should_costFourStatements() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("IPv4", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "22"))));
      workflowStateStore.commitExecutionHashes(stateId, Set.of("h1"));

      // Act
      SqlCost cost = sqlCostOf(() -> workflowStateStore.load(stateId, Set.of("IPv4"), true, true));

      // Assert
      assertThat(cost).isEqualTo(new SqlCost(4, 0));
    }

    @Test
    @DisplayName("a filter view (inputs only) costs a single statement")
    void given_inputsOnlyView_should_costOneStatement() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("IPv4", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "22"))));

      // Act
      SqlCost cost =
          sqlCostOf(() -> workflowStateStore.load(stateId, Set.of("IPv4"), false, false));

      // Assert
      assertThat(cost).isEqualTo(new SqlCost(1, 0));
    }

    @Test
    @DisplayName("an anti-replay commit costs a single statement")
    void given_hashesToCommit_should_costOneStatement() {
      // Arrange
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);

      // Act
      SqlCost cost =
          sqlCostOf(() -> workflowStateStore.commitExecutionHashes(stateId, Set.of("h1", "h2")));

      // Assert
      assertThat(cost).isEqualTo(new SqlCost(1, 0));
    }
  }
}
