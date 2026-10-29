package io.openaev.database.raw;

/**
 * Read projection of a {@code workflow_state_entries} row (ADR-011): the columns the chaining
 * engine needs to rebuild a state view, without loading entities into the persistence context.
 */
public interface RawWorkflowStateEntry {

  String getEntryKey();

  String getEntryValue();

  /** Non-null only for CORRELATED rows. */
  String getCorrelationHash();

  /** Non-null only for CORRELATED rows. */
  String getCorrelationType();
}
