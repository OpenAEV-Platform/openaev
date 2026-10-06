package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowState;
import io.openaev.database.model.WorkflowStateEntries;
import io.openaev.database.raw.RawWorkflowStateEntry;
import io.openaev.database.repository.WorkflowStateEntryRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence of the chaining engine execution state (ADR-011): one {@link WorkflowState} row per
 * global/local state, and one {@code workflow_state_entries} row per input value, correlated-tuple
 * field and execution hash.
 *
 * <p>This is the only component aware of the row layout. Callers exchange {@link
 * WorkflowStateEntries} objects with it: deltas to append on the write side, views restricted to
 * the keys they need on the read side. Writes never read the existing state, and their cost does
 * not depend on the state size.
 */
@Component
@RequiredArgsConstructor
public class WorkflowStateStore {

  private final WorkflowStateRepository workflowStateRepository;
  private final WorkflowStateEntryRepository workflowStateEntryRepository;

  // -- States ------------------------------------------------------------------------------------

  /** Id of the global state of a run, if it exists. */
  public Optional<String> findGlobalStateId(String workflowRunId) {
    return Optional.ofNullable(
            workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowRunId))
        .map(WorkflowState::getId);
  }

  /** Id of the local state of a step template in a run, if it exists. */
  public Optional<String> findLocalStateId(String stepTemplateId, String workflowRunId) {
    return Optional.ofNullable(
            workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
                stepTemplateId, workflowRunId))
        .map(WorkflowState::getId);
  }

  /** Id of the global state of a run, created atomically if missing. */
  public String getOrCreateGlobalStateId(Workflow workflowRun) {
    return findGlobalStateId(workflowRun.getId())
        .orElseGet(
            () -> {
              workflowStateRepository.insertGlobalStateIfAbsent(
                  UUID.randomUUID().toString(), workflowRun.getId());
              return findGlobalStateId(workflowRun.getId()).orElseThrow();
            });
  }

  /** Id of the local state of a step template in a run, created atomically if missing. */
  public String getOrCreateLocalStateId(Step stepTemplate, Workflow workflowRun) {
    return findLocalStateId(stepTemplate.getId(), workflowRun.getId())
        .orElseGet(
            () -> {
              workflowStateRepository.insertLocalStateIfAbsent(
                  UUID.randomUUID().toString(), workflowRun.getId(), stepTemplate.getId());
              return findLocalStateId(stepTemplate.getId(), workflowRun.getId()).orElseThrow();
            });
  }

  /** Deletes every state of the runs of a simulation (entries are removed by cascade). */
  public int deleteAllBySimulationId(String simulationId) {
    return workflowStateRepository.deleteAllBySimulationId(simulationId);
  }

  // -- Writes ------------------------------------------------------------------------------------

  /**
   * Appends the inputs and correlated tuples of {@code delta} to a state. Entries already stored
   * are ignored (database-level deduplication); the hashes of {@code delta} are not written, see
   * {@link #commitExecutionHashes}.
   */
  public void append(String stateId, WorkflowStateEntries delta) {
    appendInputs(stateId, delta.getInputs());
    appendCorrelated(stateId, delta.getCorrelated());
  }

  private void appendInputs(String stateId, List<WorkflowStateEntries.Input> inputs) {
    if (inputs == null || inputs.isEmpty()) {
      return;
    }
    List<String> keys = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (WorkflowStateEntries.Input input : inputs) {
      if (input.getKey() == null || input.getValues() == null) {
        continue;
      }
      for (String value : new LinkedHashSet<>(input.getValues())) {
        if (value != null) {
          keys.add(input.getKey());
          values.add(value);
        }
      }
    }
    if (!keys.isEmpty()) {
      workflowStateEntryRepository.insertInputs(
          stateId, keys.toArray(String[]::new), values.toArray(String[]::new));
    }
  }

  private void appendCorrelated(String stateId, List<WorkflowStateEntries.Correlated> tuples) {
    if (tuples == null || tuples.isEmpty()) {
      return;
    }
    List<String> keys = new ArrayList<>();
    List<String> values = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    List<String> types = new ArrayList<>();
    for (WorkflowStateEntries.Correlated tuple : tuples) {
      if (tuple.getValues() == null) {
        continue;
      }
      List<WorkflowStateEntries.Pair> pairs =
          tuple.getValues().stream()
              .filter(pair -> pair.key() != null && pair.value() != null)
              .toList();
      if (pairs.isEmpty()) {
        continue;
      }
      // Must stay consistent with the hash computed by the JSONB conversion migration.
      String hash = ChainingHashUtils.hashTuple(pairs);
      for (WorkflowStateEntries.Pair pair : pairs) {
        keys.add(pair.key());
        values.add(pair.value());
        hashes.add(hash);
        types.add(tuple.getType());
      }
    }
    if (!keys.isEmpty()) {
      workflowStateEntryRepository.insertCorrelated(
          stateId,
          keys.toArray(String[]::new),
          values.toArray(String[]::new),
          hashes.toArray(String[]::new),
          types.toArray(String[]::new));
    }
  }

  /**
   * Commits execution hashes and returns the ones actually inserted (anti-replay guard): a hash
   * already committed, including by a concurrent transaction, is not returned.
   */
  public Set<String> commitExecutionHashes(String stateId, Collection<String> hashes) {
    String[] distinct = hashes.stream().filter(Objects::nonNull).distinct().toArray(String[]::new);
    if (distinct.length == 0) {
      return Set.of();
    }
    return new LinkedHashSet<>(
        workflowStateEntryRepository.insertExecutionHashes(stateId, distinct));
  }

  /** Deletes the committed execution hashes of a state. */
  public void clearExecutionHashes(String stateId) {
    workflowStateEntryRepository.deleteExecutionHashes(stateId);
  }

  // -- Reads -------------------------------------------------------------------------------------

  /**
   * Builds a view of a state restricted to {@code keys}: the input values of these keys, and every
   * correlated tuple holding at least one of them (with all its fields). Execution hashes are
   * loaded only when {@code withHashes} is set.
   */
  public WorkflowStateEntries load(String stateId, Collection<String> keys, boolean withHashes) {
    WorkflowStateEntries view = WorkflowStateEntries.empty();
    if (keys != null && !keys.isEmpty()) {
      Set<String> distinctKeys = new HashSet<>(keys);
      for (RawWorkflowStateEntry row :
          workflowStateEntryRepository.findInputs(stateId, distinctKeys)) {
        view.getInputByKey(row.getEntryKey()).getValues().add(row.getEntryValue());
      }
      List<String> candidateHashes =
          workflowStateEntryRepository.findCorrelationHashesHoldingKeys(stateId, distinctKeys);
      if (!candidateHashes.isEmpty()) {
        view.getCorrelated()
            .addAll(
                toTuples(
                    workflowStateEntryRepository.findCorrelatedByHashes(
                        stateId, candidateHashes.toArray(String[]::new))));
      }
    }
    if (withHashes) {
      view.getHashExecution().addAll(loadExecutionHashes(stateId));
    }
    return view;
  }

  /** Committed execution hashes of a state. */
  public Set<String> loadExecutionHashes(String stateId) {
    return new HashSet<>(workflowStateEntryRepository.findExecutionHashes(stateId));
  }

  /** Input values of a state for the given keys. */
  public Set<String> loadInputValues(String stateId, Collection<String> keys) {
    if (keys == null || keys.isEmpty()) {
      return Set.of();
    }
    Set<String> values = new LinkedHashSet<>();
    for (RawWorkflowStateEntry row :
        workflowStateEntryRepository.findInputs(stateId, new HashSet<>(keys))) {
      values.add(row.getEntryValue());
    }
    return values;
  }

  private static List<WorkflowStateEntries.Correlated> toTuples(List<RawWorkflowStateEntry> rows) {
    Map<String, WorkflowStateEntries.Correlated> byHash = new LinkedHashMap<>();
    for (RawWorkflowStateEntry row : rows) {
      byHash
          .computeIfAbsent(
              row.getCorrelationHash(),
              hash ->
                  new WorkflowStateEntries.Correlated(new HashSet<>(), row.getCorrelationType()))
          .getValues()
          .add(new WorkflowStateEntries.Pair(row.getEntryKey(), row.getEntryValue()));
    }
    return new ArrayList<>(byHash.values());
  }
}
