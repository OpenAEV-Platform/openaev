package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntries.Pair;
import io.openaev.database.repository.StepRepository;
import io.openaev.service.chaining.ChainingHashUtils;
import io.openaev.service.chaining.WorkflowStateStore;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ADR-011 conversion of the legacy JSONB workflow state into normalized entries. Each
 * test inserts legacy rows, re-runs the migration in the test transaction (rolled back afterwards)
 * and reads the result back through {@link WorkflowStateStore}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261005160000000 — legacy workflow state conversion")
class MigrateWorkflowStateToNormalizedEntriesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261005160000000__Migrate_workflow_state_to_normalized_entries migration;
  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepRepository stepRepository;

  private static final String LEGACY_JSON =
      """
      {
        "regexPathCorrelated": "^.+\\\\+.+$",
        "inputs": [
          {"key": "IPv4", "values": ["10.0.0.1", "10.0.0.2"]},
          {"key": "Port", "values": ["22"]}
        ],
        "correlated": [
          {"values": [{"key": "IPv4", "value": "10.0.0.1"}, {"key": "Port", "value": "22"}],
           "type": "PortsScan"},
          {"values": [{"key": "Port", "value": "22"}, {"key": "IPv4", "value": "10.0.0.1"}],
           "type": "PortsScan"}
        ],
        "hashExecution": ["hash-1", "hash-2"],
        "executionKeys": ["IPv4"]
      }
      """;

  private Workflow persistRun(WorkflowStatus status) {
    return workflowComposer
        .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(status))
        .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
        .persist()
        .get();
  }

  private Step persistStepTemplate(Workflow workflow) {
    return stepRepository.save(
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(StepStatus.TEMPLATE)
            .workflow(workflow)
            .limitExecution(1)
            .build());
  }

  private String insertLegacyState(Workflow run, Step stepTemplate, String json) {
    entityManager.flush();
    String id = UUID.randomUUID().toString();
    var query =
        entityManager
            .createNativeQuery(
                "INSERT INTO workflow_states (workflow_state_id, workflow_execution_id,"
                    + (stepTemplate != null ? " workflow_step_template_id," : "")
                    + " workflow_state_entries, workflow_state_created_at, workflow_state_updated_at)"
                    + " VALUES (:id, :run,"
                    + (stepTemplate != null ? " :step," : "")
                    + " CAST(:json AS jsonb), now(), now())")
            .setParameter("id", id)
            .setParameter("run", run.getId())
            .setParameter("json", json);
    if (stepTemplate != null) {
      query.setParameter("step", stepTemplate.getId());
    }
    query.executeUpdate();
    return id;
  }

  private void backdate(String stateId, String interval) {
    entityManager
        .createNativeQuery(
            "UPDATE workflow_states SET workflow_state_created_at = now() - CAST(:interval AS interval)"
                + " WHERE workflow_state_id = :id")
        .setParameter("interval", interval)
        .setParameter("id", stateId)
        .executeUpdate();
  }

  /** Duplicate global states could only exist before uq_workflow_state_global (recreated). */
  private void allowDuplicateGlobalStates() {
    entityManager.createNativeQuery("DROP INDEX uq_workflow_state_global").executeUpdate();
  }

  private void runMigration() {
    entityManager.flush();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(contextOf(connection));
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    entityManager.clear();
  }

  private static Context contextOf(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }

  private long count(String sql) {
    return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).longValue();
  }

  private boolean stateExists(String stateId) {
    return count("SELECT count(*) FROM workflow_states WHERE workflow_state_id = '" + stateId + "'")
        > 0;
  }

  @Nested
  @DisplayName("conversion")
  class Conversion {

    @Test
    @DisplayName("converts inputs, correlated tuples and hashes of a running run")
    void given_legacyDocumentOfRunningRun_should_convertInputsTuplesAndHashes() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.RUN);
      Step stepTemplate = persistStepTemplate(run);
      String stateId = insertLegacyState(run, stepTemplate, LEGACY_JSON);

      // Act
      runMigration();

      // Assert
      WorkflowStateEntries view =
          workflowStateStore.load(stateId, Set.of("IPv4", "Port"), true, true);
      assertThat(view.getInputByKey("IPv4").getValues())
          .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
      assertThat(view.getInputByKey("Port").getValues()).containsExactly("22");
      // The two legacy tuples are the same tuple (different pair order): stored once.
      assertThat(view.getCorrelated()).hasSize(1);
      assertThat(view.getCorrelated().getFirst().getType()).isEqualTo("PortsScan");
      assertThat(view.getHashExecution()).containsExactlyInAnyOrder("hash-1", "hash-2");
    }

    @Test
    @DisplayName("tuple hashes match the ones computed at runtime")
    void given_legacyTuple_should_getTheRuntimeHash() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.RUN);
      String stateId = insertLegacyState(run, null, LEGACY_JSON);

      // Act
      runMigration();

      // Assert
      String runtimeHash =
          ChainingHashUtils.hashTuple(
              List.of(new Pair("Port", "22"), new Pair("IPv4", "10.0.0.1")));
      assertThat(
              count(
                  "SELECT count(*) FROM workflow_state_entries WHERE workflow_state_id = '"
                      + stateId
                      + "' AND correlation_hash = '"
                      + runtimeHash
                      + "'"))
          .isEqualTo(2);
    }

    @Test
    @DisplayName("converts a document holding more rows than one insert batch")
    void given_documentLargerThanOneBatch_should_convertEveryRow() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.RUN);
      String values =
          IntStream.rangeClosed(1, 2500)
              .mapToObj(i -> "\"10.1." + (i / 256) + "." + (i % 256) + "\"")
              .collect(Collectors.joining(","));
      String stateId =
          insertLegacyState(
              run, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [" + values + "]}]}");

      // Act
      runMigration();

      // Assert
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("IPv4"))).hasSize(2500);
    }

    @Test
    @DisplayName("skips malformed or incomplete JSON elements without failing the migration")
    void given_incompleteDocuments_should_convertTheValidPartsOnly() {
      // Arrange — the legacy writer (plain Gson) omits null fields; documents may be partial
      Workflow run = persistRun(WorkflowStatus.RUN);
      String arrayRoot = insertLegacyState(run, persistStepTemplate(run), "[1, 2]");
      String scalarRoot = insertLegacyState(run, persistStepTemplate(run), "\"text\"");
      String emptyObject = insertLegacyState(run, persistStepTemplate(run), "{}");
      String legacyDefault =
          insertLegacyState(
              run,
              persistStepTemplate(run),
              "{\"inputs\": [], \"correlated\": [], \"hashExecution\": []}");
      String partial =
          insertLegacyState(
              run,
              persistStepTemplate(run),
              """
              {
                "inputs": [
                  {"key": "IPv4", "values": ["10.0.0.9", {"nested": true}, null]},
                  {"values": ["no-key"]},
                  "not-an-object"
                ],
                "correlated": [
                  {"values": [{"key": "IPv4", "value": "10.0.0.9"}, {"key": "Port"}],
                   "type": "PortsScan"},
                  {"values": [{"key": "Port"}]},
                  "not-an-object"
                ],
                "hashExecution": ["hash-9", {"nested": true}]
              }
              """);

      // Act + Assert — the migration must never block application startup
      assertThatCode(MigrateWorkflowStateToNormalizedEntriesMigrationTest.this::runMigration)
          .doesNotThrowAnyException();
      for (String stateId : List.of(arrayRoot, scalarRoot, emptyObject, legacyDefault)) {
        assertThat(
                count(
                    "SELECT count(*) FROM workflow_state_entries WHERE workflow_state_id = '"
                        + stateId
                        + "'"))
            .isZero();
      }
      WorkflowStateEntries view =
          workflowStateStore.load(partial, Set.of("IPv4", "Port"), true, true);
      assertThat(view.getInputs()).hasSize(1);
      assertThat(view.getInputByKey("IPv4").getValues()).containsExactly("10.0.0.9");
      assertThat(view.getHashExecution()).containsExactly("hash-9");
      // The pair without value is dropped and the rest hashed exactly as at runtime.
      assertThat(view.getCorrelated()).hasSize(1);
      assertThat(view.getCorrelated().getFirst().getValues())
          .containsExactly(new Pair("IPv4", "10.0.0.9"));
      String runtimeHash = ChainingHashUtils.hashTuple(List.of(new Pair("IPv4", "10.0.0.9")));
      assertThat(
              count(
                  "SELECT count(*) FROM workflow_state_entries WHERE workflow_state_id = '"
                      + partial
                      + "' AND correlation_hash = '"
                      + runtimeHash
                      + "'"))
          .isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("runs kept and deleted")
  class RunSelection {

    @Test
    @DisplayName("keeps and converts the state of a paused (STOP) run")
    void given_pausedRun_should_keepAndConvertItsState() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.STOP);
      String stateId = insertLegacyState(run, null, LEGACY_JSON);

      // Act
      runMigration();

      // Assert
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("Port"))).containsExactly("22");
    }

    @Test
    @DisplayName("deletes the leftover state of an ended run")
    void given_endedRun_should_deleteItsState() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.END);
      String stateId = insertLegacyState(run, null, LEGACY_JSON);

      // Act
      runMigration();

      // Assert
      assertThat(stateExists(stateId)).isFalse();
    }
  }

  @Nested
  @DisplayName("duplicate global states")
  class GlobalStateMerge {

    @Test
    @DisplayName("merges the duplicate global states of a run into the oldest one")
    void given_duplicateGlobalStates_should_mergeThemIntoTheOldest() {
      // Arrange
      Workflow run = persistRun(WorkflowStatus.RUN);
      allowDuplicateGlobalStates();
      String oldest =
          insertLegacyState(run, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"a\"]}]}");
      backdate(oldest, "1 hour");
      String duplicate =
          insertLegacyState(run, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"b\"]}]}");

      // Act
      runMigration();

      // Assert
      assertThat(workflowStateStore.findGlobalStateId(run.getId())).contains(oldest);
      assertThat(workflowStateStore.loadInputValues(oldest, Set.of("IPv4")))
          .containsExactlyInAnyOrder("a", "b");
      assertThat(stateExists(duplicate)).isFalse();
    }

    @Test
    @DisplayName("merges duplicate global states run by run, never across runs")
    void given_duplicateGlobalStatesInTwoRuns_should_mergeEachRunSeparately() {
      // Arrange — two runs (two simulations), each with duplicate global states
      Workflow runA = persistRun(WorkflowStatus.RUN);
      Workflow runB = persistRun(WorkflowStatus.RUN);
      allowDuplicateGlobalStates();
      String oldestA =
          insertLegacyState(
              runA, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"a1\"]}]}");
      backdate(oldestA, "1 hour");
      insertLegacyState(runA, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"a2\"]}]}");
      String oldestB =
          insertLegacyState(
              runB, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"b1\"]}]}");
      backdate(oldestB, "2 hours");
      insertLegacyState(runB, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"b2\"]}]}");

      // Act
      runMigration();

      // Assert
      assertThat(workflowStateStore.findGlobalStateId(runA.getId())).contains(oldestA);
      assertThat(workflowStateStore.findGlobalStateId(runB.getId())).contains(oldestB);
      assertThat(workflowStateStore.loadInputValues(oldestA, Set.of("IPv4")))
          .containsExactlyInAnyOrder("a1", "a2");
      assertThat(workflowStateStore.loadInputValues(oldestB, Set.of("IPv4")))
          .containsExactlyInAnyOrder("b1", "b2");
    }
  }

  @Nested
  @DisplayName("final schema")
  class Schema {

    @Test
    @DisplayName("unique global state, legacy indexes gone, nullable JSONB, no storage_mode")
    void given_migratedSchema_should_matchTheNormalizedModel() {
      // Act — re-run it in the test transaction, so the assertion does not depend on which
      // version of this migration the test database was created with
      runMigration();

      // Assert
      assertThat(
              count("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_workflow_state_global'"))
          .isEqualTo(1);
      assertThat(
              count(
                  "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_wf_state_global_lookup'"))
          .isZero();
      assertThat(
              count("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_wf_state_entries_gin'"))
          .isZero();
      assertThat(
              count(
                  "SELECT count(*) FROM information_schema.columns WHERE table_name = 'workflow_states'"
                      + " AND column_name = 'workflow_state_entries' AND is_nullable = 'YES'"))
          .isEqualTo(1);
      assertThat(
              count(
                  "SELECT count(*) FROM information_schema.columns WHERE table_name = 'workflows'"
                      + " AND column_name = 'storage_mode'"))
          .isZero();
    }
  }
}
