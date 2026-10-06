package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntry.EntryType;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA slice tests (ADR-011) for {@link WorkflowStateEntryRepository}. Persists a workflow run + a
 * workflow state, then exercises every finder method and the per-entry-type deduplication enforced
 * by the partial unique indexes of {@code workflow_state_entries}.
 */
@Transactional
class WorkflowStateEntryRepositoryTest extends IntegrationTest {

  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowStateRepository workflowStateRepository;
  @Autowired private WorkflowStateEntryRepository workflowStateEntryRepository;

  private WorkflowState workflowState;
  private static final String CORRELATION_HASH = "0123456789abcdef0123456789abcdef";
  private static final String OTHER_CORRELATION_HASH = "fedcba9876543210fedcba9876543210";

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
    return entry(type, key, value, null);
  }

  private WorkflowStateEntry entry(EntryType type, String key, String value, String hash) {
    return entry(workflowState, type, key, value, hash);
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

  @Test
  @DisplayName("existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue detects duplicates")
  void exists_by_natural_key() {
    entry(EntryType.INPUT, "ip", "10.0.0.1");

    assertThat(
            workflowStateEntryRepository
                .existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue(
                    workflowState.getId(), EntryType.INPUT, "ip", "10.0.0.1"))
        .isTrue();
    assertThat(
            workflowStateEntryRepository
                .existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue(
                    workflowState.getId(), EntryType.INPUT, "ip", "10.0.0.2"))
        .isFalse();
  }

  @Test
  @DisplayName("findByWorkflowState_IdAndEntryType returns only the matching type")
  void find_by_state_and_type() {
    entry(EntryType.INPUT, "ip", "10.0.0.1");
    entry(EntryType.INPUT, "port", "8080");
    entry(EntryType.HASH_EXECUTION, "exec", "h1");

    List<WorkflowStateEntry> inputs =
        workflowStateEntryRepository.findByWorkflowState_IdAndEntryType(
            workflowState.getId(), EntryType.INPUT);

    assertThat(inputs).hasSize(2);
    assertThat(inputs).allMatch(e -> e.getEntryType() == EntryType.INPUT);
  }

  @Test
  @DisplayName(
      "findByWorkflowState_IdAndCorrelationHash returns only correlated rows sharing that hash")
  void find_by_correlation_hash() {
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
    entry(EntryType.CORRELATED, "Port", "22", CORRELATION_HASH);
    entry(EntryType.INPUT, "IPv4", "10.0.0.1");

    List<WorkflowStateEntry> correlated =
        workflowStateEntryRepository.findByWorkflowState_IdAndCorrelationHash(
            workflowState.getId(), CORRELATION_HASH);

    assertThat(correlated).hasSize(2);
    assertThat(correlated).allMatch(e -> e.getEntryType() == EntryType.CORRELATED);
    assertThat(correlated).allMatch(e -> "PortsScan".equals(e.getCorrelationType()));
  }

  @Test
  @DisplayName("Two correlated tuples sharing a field value are both fully stored")
  void correlated_tuples_sharing_a_value_are_both_stored() {
    // {IPv4=10.0.0.1, Port=80} and {IPv4=10.0.0.1, Port=443}: same IPv4 row content, two tuples.
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
    entry(EntryType.CORRELATED, "Port", "80", CORRELATION_HASH);
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", OTHER_CORRELATION_HASH);
    entry(EntryType.CORRELATED, "Port", "443", OTHER_CORRELATION_HASH);

    assertThat(
            workflowStateEntryRepository.findByWorkflowState_IdAndCorrelationHash(
                workflowState.getId(), CORRELATION_HASH))
        .hasSize(2);
    assertThat(
            workflowStateEntryRepository.findByWorkflowState_IdAndCorrelationHash(
                workflowState.getId(), OTHER_CORRELATION_HASH))
        .hasSize(2);
  }

  @Test
  @DisplayName("A same tuple stored in two states is read back only from the requested state")
  void correlated_tuple_lookup_is_scoped_by_state() {
    // Same tuple content -> same hash, e.g. global state + a local state it was propagated to.
    WorkflowState otherState = newState();
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
    entry(EntryType.CORRELATED, "Port", "22", CORRELATION_HASH);
    entry(otherState, EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);
    entry(otherState, EntryType.CORRELATED, "Port", "22", CORRELATION_HASH);

    List<WorkflowStateEntry> correlated =
        workflowStateEntryRepository.findByWorkflowState_IdAndCorrelationHash(
            workflowState.getId(), CORRELATION_HASH);

    assertThat(correlated).hasSize(2);
    assertThat(correlated)
        .allMatch(e -> e.getWorkflowState().getId().equals(workflowState.getId()));
  }

  @Test
  @DisplayName("A same value may exist as INPUT and as a CORRELATED field")
  void same_value_across_entry_types_is_allowed() {
    entry(EntryType.INPUT, "IPv4", "10.0.0.1");
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);

    assertThat(
            workflowStateEntryRepository.findByWorkflowState_IdAndEntryType(
                workflowState.getId(), EntryType.CORRELATED))
        .hasSize(1);
  }

  @Test
  @DisplayName("A duplicate INPUT value is rejected by uq_wse_input")
  void duplicate_input_is_rejected() {
    entry(EntryType.INPUT, "IPv4", "10.0.0.1");

    assertThatThrownBy(() -> entry(EntryType.INPUT, "IPv4", "10.0.0.1"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("A duplicate execution hash is rejected by uq_wse_hash")
  void duplicate_execution_hash_is_rejected() {
    entry(EntryType.HASH_EXECUTION, "exec", CORRELATION_HASH);

    assertThatThrownBy(() -> entry(EntryType.HASH_EXECUTION, "exec", CORRELATION_HASH))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName(
      "A duplicate field within the same correlated tuple is rejected by uq_wse_correlated")
  void duplicate_correlated_field_is_rejected() {
    entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH);

    assertThatThrownBy(() -> entry(EntryType.CORRELATED, "IPv4", "10.0.0.1", CORRELATION_HASH))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("Values larger than the B-tree row size limit can be stored and deduplicated")
  void large_values_are_indexable() {
    // Incompressible (random hex) so the raw value would exceed the limit even after the inline
    // compression Postgres applies to index entries.
    StringBuilder sb = new StringBuilder();
    Random random = new Random(42);
    while (sb.length() < 10_000) {
      sb.append(Long.toHexString(random.nextLong()));
    }
    String largeValue = sb.toString();
    entry(EntryType.INPUT, "Text", largeValue);

    assertThat(
            workflowStateEntryRepository
                .existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue(
                    workflowState.getId(), EntryType.INPUT, "Text", largeValue))
        .isTrue();
    assertThatThrownBy(() -> entry(EntryType.INPUT, "Text", largeValue))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
