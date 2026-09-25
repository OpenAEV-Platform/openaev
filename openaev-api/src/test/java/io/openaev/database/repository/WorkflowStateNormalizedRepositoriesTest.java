package io.openaev.database.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.WorkflowStateEntry.EntryType;
import io.openaev.utils.fixtures.WorkflowFixture;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA slice tests (ADR-007) for the two normalized WorkflowState repositories. Persists a workflow
 * run + a workflow state, then exercises every finder method declared on {@link
 * WorkflowStateEntryRepository} and {@link WorkflowStateCorrelationProgressRepository}.
 */
@SpringBootTest
@Transactional
class WorkflowStateNormalizedRepositoriesTest extends IntegrationTest {

  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private WorkflowStateRepository workflowStateRepository;
  @Autowired private WorkflowStateEntryRepository workflowStateEntryRepository;

  @Autowired
  private WorkflowStateCorrelationProgressRepository workflowStateCorrelationProgressRepository;

  private WorkflowState workflowState;
  private final byte[] correlationHash = "hash-abc".getBytes(StandardCharsets.UTF_8);

  @BeforeEach
  void setUp() {
    Workflow workflow =
        workflowRepository.save(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN));
    WorkflowState state = new WorkflowState();
    state.setWorkflowExecution(workflow);
    workflowState = workflowStateRepository.save(state);
  }

  private WorkflowStateEntry entry(EntryType type, String key, String value, byte[] hash) {
    WorkflowStateEntry e = new WorkflowStateEntry();
    e.setWorkflowState(workflowState);
    e.setEntryType(type);
    e.setEntryKey(key);
    e.setEntryValue(value);
    e.setCorrelationHash(hash);
    return workflowStateEntryRepository.save(e);
  }

  @Test
  @DisplayName("existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue detects duplicates")
  void exists_by_natural_key() {
    entry(EntryType.INPUT, "ip", "10.0.0.1", null);

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
    entry(EntryType.INPUT, "ip", "10.0.0.1", null);
    entry(EntryType.INPUT, "port", "8080", null);
    entry(EntryType.HASH_EXECUTION, "exec", "h1", null);

    List<WorkflowStateEntry> inputs =
        workflowStateEntryRepository.findByWorkflowState_IdAndEntryType(
            workflowState.getId(), EntryType.INPUT);

    assertThat(inputs).hasSize(2);
    assertThat(inputs).allMatch(e -> e.getEntryType() == EntryType.INPUT);
  }

  @Test
  @DisplayName("findByCorrelationHash returns only correlated rows sharing that hash")
  void find_by_correlation_hash() {
    entry(EntryType.CORRELATED, "Host", "10.0.0.1", correlationHash);
    entry(EntryType.CORRELATED, "Port", "22", correlationHash);
    entry(EntryType.INPUT, "ip", "10.0.0.1", null);

    List<WorkflowStateEntry> correlated =
        workflowStateEntryRepository.findByCorrelationHash(correlationHash);

    assertThat(correlated).hasSize(2);
    assertThat(correlated).allMatch(e -> e.getEntryType() == EntryType.CORRELATED);
  }

  @Test
  @DisplayName("findByWorkflowState_IdAndIsCompleteTrue returns only completed correlations")
  void find_completed_correlations() {
    WorkflowStateCorrelationProgress complete =
        WorkflowStateCorrelationProgress.builder()
            .correlationHash("complete".getBytes(StandardCharsets.UTF_8))
            .workflowState(workflowState)
            .keysPresent(List.of("Host", "Port"))
            .isComplete(true)
            .build();
    WorkflowStateCorrelationProgress incomplete =
        WorkflowStateCorrelationProgress.builder()
            .correlationHash("incomplete".getBytes(StandardCharsets.UTF_8))
            .workflowState(workflowState)
            .keysPresent(List.of("Host"))
            .isComplete(false)
            .build();
    workflowStateCorrelationProgressRepository.save(complete);
    workflowStateCorrelationProgressRepository.save(incomplete);

    List<WorkflowStateCorrelationProgress> ready =
        workflowStateCorrelationProgressRepository.findByWorkflowState_IdAndIsCompleteTrue(
            workflowState.getId());

    assertThat(ready).hasSize(1);
    assertThat(ready.getFirst().isComplete()).isTrue();
    assertThat(ready.getFirst().getKeysPresent()).containsExactlyInAnyOrder("Host", "Port");
  }
}
