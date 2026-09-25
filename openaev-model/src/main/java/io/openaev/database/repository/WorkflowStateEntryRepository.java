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

  List<WorkflowStateEntry> findByCorrelationHash(byte[] correlationHash);
}
