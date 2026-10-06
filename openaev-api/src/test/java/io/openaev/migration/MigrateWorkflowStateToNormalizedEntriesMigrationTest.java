package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the ADR-011 conversion of the legacy JSONB workflow state into normalized entries. Each
 * test inserts legacy rows, re-runs the migration in the test transaction (rolled back afterwards)
 * and reads the result back through {@link WorkflowStateStore}.
 */
@Transactional
@WithMockUser(isAdmin = true)
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

  @Test
  @DisplayName("converts inputs, correlated tuples and hashes of a running run")
  void convertsLegacyDocumentOfRunningRun() {
    Workflow run = persistRun(WorkflowStatus.RUN);
    Step stepTemplate = persistStepTemplate(run);
    String stateId = insertLegacyState(run, stepTemplate, LEGACY_JSON);

    runMigration();

    WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4", "Port"), true);
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
  void convertedTupleHashMatchesRuntimeHash() {
    Workflow run = persistRun(WorkflowStatus.RUN);
    String stateId = insertLegacyState(run, null, LEGACY_JSON);

    runMigration();

    String runtimeHash =
        ChainingHashUtils.hashTuple(List.of(new Pair("Port", "22"), new Pair("IPv4", "10.0.0.1")));
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
  @DisplayName("keeps and converts the state of a paused (STOP) run")
  void convertsPausedRun() {
    Workflow run = persistRun(WorkflowStatus.STOP);
    String stateId = insertLegacyState(run, null, LEGACY_JSON);

    runMigration();

    assertThat(workflowStateStore.loadInputValues(stateId, Set.of("Port"))).containsExactly("22");
  }

  @Test
  @DisplayName("deletes the leftover state of an ended run")
  void deletesStateOfEndedRun() {
    Workflow run = persistRun(WorkflowStatus.END);
    String stateId = insertLegacyState(run, null, LEGACY_JSON);

    runMigration();

    assertThat(
            count(
                "SELECT count(*) FROM workflow_states WHERE workflow_state_id = '" + stateId + "'"))
        .isZero();
  }

  @Test
  @DisplayName("merges duplicate global states of a run into the oldest one")
  void mergesDuplicateGlobalStates() {
    Workflow run = persistRun(WorkflowStatus.RUN);
    // Duplicates could only exist before uq_workflow_state_global: drop it for the setup (the
    // migration recreates it; everything is rolled back with the test transaction).
    entityManager.createNativeQuery("DROP INDEX uq_workflow_state_global").executeUpdate();
    String oldest =
        insertLegacyState(run, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"a\"]}]}");
    entityManager
        .createNativeQuery(
            "UPDATE workflow_states SET workflow_state_created_at = now() - interval '1 hour'"
                + " WHERE workflow_state_id = :id")
        .setParameter("id", oldest)
        .executeUpdate();
    String duplicate =
        insertLegacyState(run, null, "{\"inputs\": [{\"key\": \"IPv4\", \"values\": [\"b\"]}]}");

    runMigration();

    assertThat(workflowStateStore.findGlobalStateId(run.getId())).contains(oldest);
    assertThat(workflowStateStore.loadInputValues(oldest, Set.of("IPv4")))
        .containsExactlyInAnyOrder("a", "b");
    assertThat(
            count(
                "SELECT count(*) FROM workflow_states WHERE workflow_state_id = '"
                    + duplicate
                    + "'"))
        .isZero();
  }

  @Test
  @DisplayName("finalizes the schema: unique global state, nullable JSONB, no storage_mode")
  void finalizesSchema() {
    assertThat(
            count("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_workflow_state_global'"))
        .isEqualTo(1);
    assertThat(
            count("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_wf_state_global_lookup'"))
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
