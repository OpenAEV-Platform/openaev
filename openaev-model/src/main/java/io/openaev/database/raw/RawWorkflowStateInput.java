package io.openaev.database.raw;

/** Read projection of an INPUT {@code workflow_state_entries} row (ADR-011): key and value only. */
public interface RawWorkflowStateInput {

  String getEntryKey();

  String getEntryValue();
}
