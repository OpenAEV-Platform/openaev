package io.openaev.database.repository;

import io.openaev.database.model.WorkflowStateEntry;
import io.openaev.database.model.WorkflowStateEntry.EntryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowStateEntryRepository extends JpaRepository<WorkflowStateEntry, Long> {

  boolean existsByWorkflowState_IdAndEntryTypeAndEntryKeyAndEntryValue(
      String workflowStateId, EntryType entryType, String entryKey, String entryValue);

  List<WorkflowStateEntry> findByWorkflowState_IdAndEntryType(
      String workflowStateId, EntryType entryType);

  /**
   * Rows of one correlated tuple within one state. Always scoped by state: the hash identifies the
   * tuple content, so the same tuple (and hash) exists in the global state, in every local state it
   * was propagated to, and in other runs.
   */
  List<WorkflowStateEntry> findByWorkflowState_IdAndCorrelationHash(
      String workflowStateId, String correlationHash);
}
