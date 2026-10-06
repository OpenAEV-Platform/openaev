package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntries.Pair;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowStateEntryRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests (ADR-011) of {@link WorkflowStateStore} against the real {@code
 * workflow_state_entries} table: state creation, append-only writes with database-level
 * deduplication, targeted reads, anti-replay hash commits and cascade deletion.
 */
@Transactional
@DisplayName("WorkflowStateStore")
class WorkflowStateStoreTest extends IntegrationTest {

  @Autowired private WorkflowStateStore workflowStateStore;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowStateRepository workflowStateRepository;
  @Autowired private WorkflowStateEntryRepository workflowStateEntryRepository;
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

  @Nested
  @DisplayName("states")
  class States {

    @Test
    @DisplayName("getOrCreateGlobalStateId creates the global state once")
    void globalStateIsCreatedOnce() {
      String first = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String second = workflowStateStore.getOrCreateGlobalStateId(workflowRun);

      assertThat(second).isEqualTo(first);
      assertThat(workflowStateStore.findGlobalStateId(workflowRun.getId())).contains(first);
    }

    @Test
    @DisplayName("getOrCreateLocalStateId creates one local state per step template")
    void localStateIsCreatedOncePerStep() {
      String global = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String first = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      String second = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);

      assertThat(second).isEqualTo(first).isNotEqualTo(global);
    }

    @Test
    @DisplayName("deleteAllBySimulationId deletes the states and, by cascade, their entries")
    void deleteBySimulationCascadesToEntries() {
      String global = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      String local = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(global, delta(Map.of("IPv4", Set.of("10.0.0.1"))));
      workflowStateStore.commitExecutionHashes(local, Set.of("h1"));

      int deleted = workflowStateStore.deleteAllBySimulationId(workflowRun.getSimulation().getId());

      assertThat(deleted).isEqualTo(2);
      assertThat(workflowStateRepository.findAllById(List.of(global, local))).isEmpty();
      assertThat(workflowStateEntryRepository.findExecutionHashes(local)).isEmpty();
      assertThat(workflowStateStore.loadInputValues(global, Set.of("IPv4"))).isEmpty();
    }
  }

  @Nested
  @DisplayName("append and load")
  class AppendAndLoad {

    @Test
    @DisplayName("a view holds only the inputs of the requested keys")
    void viewIsRestrictedToRequestedKeys() {
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(Map.of("IPv4", Set.of("10.0.0.1", "10.0.0.2"), "Username", Set.of("admin"))));

      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4"), false);

      assertThat(view.getInputs()).hasSize(1);
      assertThat(view.getInputByKey("IPv4").getValues())
          .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    @DisplayName("a view holds every field of the tuples sharing a requested key, and only those")
    void viewHoldsCandidateTuplesWithAllTheirFields() {
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of(),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80")),
              tuple("Credentials", new Pair("Username", "u"), new Pair("Password", "p"))));

      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("Port"), false);

      assertThat(view.getCorrelated()).hasSize(1);
      WorkflowStateEntries.Correlated tuple = view.getCorrelated().getFirst();
      assertThat(tuple.getType()).isEqualTo("PortsScan");
      assertThat(tuple.getValues())
          .containsExactlyInAnyOrder(new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80"));
    }

    @Test
    @DisplayName("tuples sharing a field value are kept as distinct tuples")
    void tuplesSharingAValueStayDistinct() {
      String stateId = workflowStateStore.getOrCreateGlobalStateId(workflowRun);
      workflowStateStore.append(
          stateId,
          delta(
              Map.of(),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "80")),
              tuple("PortsScan", new Pair("IPv4", "10.0.0.1"), new Pair("Port", "443"))));

      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("IPv4"), false);

      assertThat(view.getCorrelated()).hasSize(2);
    }

    @Test
    @DisplayName("appending again an input or a tuple already stored adds nothing")
    void appendIsDeduplicatedByTheDatabase() {
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      WorkflowStateEntries.Correlated portScan =
          tuple("PortsScan", new Pair("Host", "10.0.0.1"), new Pair("Port", "22"));
      workflowStateStore.append(stateId, delta(Map.of("Host", Set.of("10.0.0.1")), portScan));
      workflowStateStore.append(
          stateId,
          delta(
              Map.of("Host", Set.of("10.0.0.1")),
              tuple("PortsScan", new Pair("Port", "22"), new Pair("Host", "10.0.0.1"))));

      WorkflowStateEntries view = workflowStateStore.load(stateId, Set.of("Host"), false);

      assertThat(view.getInputByKey("Host").getValues()).containsExactly("10.0.0.1");
      assertThat(view.getCorrelated()).hasSize(1);
      assertThat(
              workflowStateEntryRepository.findByWorkflowState_IdAndEntryType(
                  stateId, WorkflowStateEntry.EntryType.CORRELATED))
          .hasSize(2);
    }

    @Test
    @DisplayName("hashes are loaded only when requested")
    void hashesAreLoadedOnRequest() {
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.commitExecutionHashes(stateId, Set.of("h1"));

      assertThat(workflowStateStore.load(stateId, Set.of(), false).getHashExecution()).isEmpty();
      assertThat(workflowStateStore.load(stateId, Set.of(), true).getHashExecution())
          .containsExactly("h1");
    }
  }

  @Nested
  @DisplayName("execution hashes")
  class ExecutionHashes {

    @Test
    @DisplayName("commitExecutionHashes returns only the hashes not committed before")
    void commitReturnsOnlyNewHashes() {
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);

      assertThat(workflowStateStore.commitExecutionHashes(stateId, Set.of("h1", "h2")))
          .containsExactlyInAnyOrder("h1", "h2");
      assertThat(workflowStateStore.commitExecutionHashes(stateId, Set.of("h2", "h3")))
          .containsExactly("h3");
      assertThat(workflowStateStore.loadExecutionHashes(stateId))
          .containsExactlyInAnyOrder("h1", "h2", "h3");
    }

    @Test
    @DisplayName("clearExecutionHashes removes the hashes only, so the step can fire again")
    void clearRemovesHashesOnly() {
      String stateId = workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun);
      workflowStateStore.append(stateId, delta(Map.of("IPv4", Set.of("10.0.0.1"))));
      workflowStateStore.commitExecutionHashes(stateId, Set.of("h1"));

      workflowStateStore.clearExecutionHashes(stateId);

      assertThat(workflowStateStore.loadExecutionHashes(stateId)).isEmpty();
      assertThat(workflowStateStore.loadInputValues(stateId, Set.of("IPv4")))
          .containsExactly("10.0.0.1");
      assertThat(workflowStateStore.commitExecutionHashes(stateId, Set.of("h1")))
          .containsExactly("h1");
    }
  }
}
