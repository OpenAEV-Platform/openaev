package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntry.EntryType;
import io.openaev.database.raw.RawWorkflowStateEntry;
import io.openaev.database.raw.RawWorkflowStateInput;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository tests (ADR-011) for {@link WorkflowStateEntryRepository}: the per-entry-type
 * deduplication enforced by the partial unique indexes of {@code workflow_state_entries}, the
 * key-restricted reads, and the insert-or-ignore writes.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("WorkflowStateEntryRepository")
class WorkflowStateEntryRepositoryTest extends IntegrationTest {

  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowStateRepository workflowStateRepository;
  @Autowired private WorkflowStateEntryRepository workflowStateEntryRepository;

  private static final String CORRELATION_HASH = "0123456789abcdef0123456789abcdef";
  private static final String OTHER_CORRELATION_HASH = "fedcba9876543210fedcba9876543210";

  private WorkflowState workflowState;

  @BeforeEach
  void setUp() {
    workflowState = newState();
  }

  private WorkflowState newState() {
    Workflow workflow =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
            .withSimulation(exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
            .persist()
            .get();
    WorkflowState state = new WorkflowState();
    state.setWorkflowExecution(workflow);
    return workflowStateRepository.save(state);
  }

  private WorkflowStateEntry entry(EntryType type, String key, String value) {
    return entry(workflowState, type, key, value, null);
  }

  private WorkflowStateEntry entry(
      WorkflowState state, EntryType type, String key, String value, String hash) {
    WorkflowStateEntry e = new WorkflowStateEntry();
    e.setWorkflowState(state);
    e.setEntryType(type);
    e.setEntryKey(key);
    e.setEntryValue(value);
    if (hash != null) {
      e.setCorrelationHash(hash);
      e.setCorrelationType("PortsScan");
    }
    return workflowStateEntryRepository.saveAndFlush(e);
  }

  private static List<String> values(List<? extends RawWorkflowStateInput> rows) {
    return rows.stream().map(RawWorkflowStateInput::getEntryValue).toList();
  }

  @Nested
  @DisplayName("per-type deduplication (partial unique indexes)")
  class Deduplication {

    @Test
    @DisplayName("two correlated tuples sharing a field value are both fully stored")
    void given_tuplesSharingAValue_should_storeBothTuples() {
      // Arrange — {IPv4=10.0.0.1, Port=80} and {IPv4=10.0.0.1, Port=443}
      entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
      entry(workflowState, EntryType.CORRELATED, "Port", "80", CORRELATION_HASH);

      // Act
      entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", OTHER_CORRELATION_HASH);
      entry(workflowState, EntryType.CORRELATED, "Port", "443", OTHER_CORRELATION_HASH);

      // Assert
      assertThat(
              workflowStateEntryRepository.findCorrelatedByHashes(
                  workflowState.getId(), new String[] {CORRELATION_HASH, OTHER_CORRELATION_HASH}))
          .hasSize(4);
    }

    @Test
    @DisplayName("a same value may exist as INPUT and as a CORRELATED field")
    void given_sameValueAsInputAndTupleField_should_storeBoth() {
      // Arrange
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");

      // Act
      entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);

      // Assert
      assertThat(
              values(
                  workflowStateEntryRepository.findInputs(workflowState.getId(), Set.of("IPv4"))))
          .containsExactly("10.0.0.1");
      assertThat(
              workflowStateEntryRepository.findCorrelationHashesHoldingKeys(
                  workflowState.getId(), Set.of("IPv4")))
          .containsExactly(CORRELATION_HASH);
    }

    @Test
    @DisplayName("a duplicate INPUT value is rejected by uq_wse_input")
    void given_duplicateInput_should_beRejected() {
      // Arrange
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");

      // Act + Assert
      assertThatThrownBy(() -> entry(EntryType.INPUT, "IPv4", "10.0.0.1"))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a duplicate execution hash is rejected by uq_wse_hash")
    void given_duplicateExecutionHash_should_beRejected() {
      // Arrange
      entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", CORRELATION_HASH);

      // Act + Assert
      assertThatThrownBy(() -> entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", CORRELATION_HASH))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a duplicate field within the same tuple is rejected by uq_wse_correlated")
    void given_duplicateTupleField_should_beRejected() {
      // Arrange
      entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH))
          .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("values larger than the B-tree row size limit can be stored and deduplicated")
    void given_valueLargerThanBtreeLimit_should_beIndexedAndDeduplicated() {
      // Arrange — incompressible (random hex) so the raw value would exceed the limit even after
      // the inline compression Postgres applies to index entries
      StringBuilder sb = new StringBuilder();
      Random random = new Random(42);
      while (sb.length() < 10_000) {
        sb.append(Long.toHexString(random.nextLong()));
      }
      String largeValue = sb.toString();

      // Act
      entry(EntryType.INPUT, "Text", largeValue);

      // Assert
      assertThat(
              values(
                  workflowStateEntryRepository.findInputs(workflowState.getId(), Set.of("Text"))))
          .containsExactly(largeValue);
      assertThatThrownBy(() -> entry(EntryType.INPUT, "Text", largeValue))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("reads")
  class Reads {

    @Test
    @DisplayName("findInputs returns the INPUT values of the requested keys only")
    void given_inputsOfSeveralKeys_should_returnOnlyTheRequestedKeys() {
      // Arrange
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");
      entry(EntryType.INPUT, "Port", "8080");
      entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", "h1");

      // Act
      List<RawWorkflowStateInput> inputs =
          workflowStateEntryRepository.findInputs(workflowState.getId(), Set.of("IPv4"));

      // Assert
      assertThat(inputs).hasSize(1);
      assertThat(inputs.getFirst().getEntryKey()).isEqualTo("IPv4");
      assertThat(inputs.getFirst().getEntryValue()).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("tuple reads are scoped to the requested state")
    void given_sameTupleInTwoStates_should_readOnlyTheRequestedState() {
      // Arrange — same content, hence same hash, e.g. global state + a local state
      WorkflowState otherState = newState();
      entry(workflowState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
      entry(workflowState, EntryType.CORRELATED, "Port", "22", CORRELATION_HASH);
      entry(otherState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
      entry(otherState, EntryType.CORRELATED, "Port", "22", CORRELATION_HASH);

      // Act
      List<String> hashes =
          workflowStateEntryRepository.findCorrelationHashesHoldingKeys(
              workflowState.getId(), Set.of("Port"));
      List<RawWorkflowStateEntry> rows =
          workflowStateEntryRepository.findCorrelatedByHashes(
              workflowState.getId(), hashes.toArray(String[]::new));

      // Assert
      assertThat(hashes).containsExactly(CORRELATION_HASH);
      assertThat(rows).hasSize(2);
      assertThat(rows).allMatch(row -> "PortsScan".equals(row.getCorrelationType()));
    }

    @Test
    @DisplayName("findExecutionHashes returns the committed hashes of the state")
    void given_committedHashes_should_returnThem() {
      // Arrange
      entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", "h1");
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");

      // Act
      List<String> hashes = workflowStateEntryRepository.findExecutionHashes(workflowState.getId());

      // Assert
      assertThat(hashes).containsExactly("h1");
    }
  }

  @Nested
  @DisplayName("writes")
  class Writes {

    @Test
    @DisplayName("insertInputs ignores the values already stored")
    void given_partlyStoredInputs_should_insertOnlyTheNewOnes() {
      // Arrange
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");

      // Act
      int inserted =
          workflowStateEntryRepository.insertInputs(
              workflowState.getId(),
              new String[] {"IPv4", "IPv4"},
              new String[] {"10.0.0.1", "10.0.0.2"});

      // Assert
      assertThat(inserted).isEqualTo(1);
      assertThat(
              values(
                  workflowStateEntryRepository.findInputs(workflowState.getId(), Set.of("IPv4"))))
          .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
    }

    @Test
    @DisplayName("insertExecutionHashes returns only the hashes it inserted")
    void given_partlyCommittedHashes_should_returnOnlyTheInsertedOnes() {
      // Arrange
      entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", "h1");

      // Act
      List<String> inserted =
          workflowStateEntryRepository.insertExecutionHashes(
              workflowState.getId(), new String[] {"h1", "h2"});

      // Assert
      assertThat(inserted).containsExactly("h2");
    }

    @Test
    @DisplayName("deleteExecutionHashes removes the hashes and nothing else")
    void given_hashesAndInputs_should_deleteOnlyTheHashes() {
      // Arrange
      entry(EntryType.HASH_EXECUTION, "HASH_EXECUTION", "h1");
      entry(EntryType.INPUT, "IPv4", "10.0.0.1");

      // Act
      int deleted = workflowStateEntryRepository.deleteExecutionHashes(workflowState.getId());

      // Assert
      assertThat(deleted).isEqualTo(1);
      assertThat(workflowStateEntryRepository.findExecutionHashes(workflowState.getId())).isEmpty();
      assertThat(workflowStateEntryRepository.findInputs(workflowState.getId(), Set.of("IPv4")))
          .hasSize(1);
    }
  }
}
