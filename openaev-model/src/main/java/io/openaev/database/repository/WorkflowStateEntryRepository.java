package io.openaev.database.repository;

import io.openaev.database.model.WorkflowStateEntry;
import io.openaev.database.raw.RawWorkflowStateEntry;
import io.openaev.database.raw.RawWorkflowStateInput;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Normalized WorkflowState entries (ADR-011).
 *
 * <p>Queries filter {@code entry_type} with literals (never a bind parameter) so that PostgreSQL
 * can match the per-type partial indexes ({@code uq_wse_input}, {@code uq_wse_hash}, {@code
 * uq_wse_correlated}) on generic plans too.
 *
 * <p>Writes are native {@code INSERT ... ON CONFLICT DO NOTHING}: JPA cannot express an atomic
 * insert-or-ignore, and no session side effect is lost — {@link WorkflowStateEntry} is not indexed,
 * audited nor streamed (no entity listener), and inserted rows are never read back from the
 * session.
 *
 * <p>Every insert orders its rows ({@code ORDER BY}): a multi-row insert locks the unique-index
 * keys it writes in row order, so two concurrent inserts of overlapping rows in different orders
 * would deadlock and PostgreSQL would abort one of them. A deterministic order makes them queue
 * instead.
 */
@Repository
public interface WorkflowStateEntryRepository extends JpaRepository<WorkflowStateEntry, Long> {

  // -- Reads -------------------------------------------------------------------------------------

  /** INPUT values of a state, restricted to the given keys. */
  @Query(
      "SELECT e.entryKey AS entryKey, e.entryValue AS entryValue"
          + " FROM WorkflowStateEntry e"
          + " WHERE e.workflowState.id = :stateId"
          + " AND e.entryType = io.openaev.database.model.WorkflowStateEntry.EntryType.INPUT"
          + " AND e.entryKey IN :keys")
  List<RawWorkflowStateInput> findInputs(
      @Param("stateId") String stateId, @Param("keys") Collection<String> keys);

  /**
   * Hashes of the correlated tuples of a state holding at least one of the given keys (candidate
   * tuples). Their fields are then read with {@link #findCorrelatedByHashes}.
   */
  @Query(
      "SELECT DISTINCT e.correlationHash FROM WorkflowStateEntry e"
          + " WHERE e.workflowState.id = :stateId"
          + " AND e.entryType = io.openaev.database.model.WorkflowStateEntry.EntryType.CORRELATED"
          + " AND e.entryKey IN :keys")
  List<String> findCorrelationHashesHoldingKeys(
      @Param("stateId") String stateId, @Param("keys") Collection<String> keys);

  /**
   * Every field of the given correlated tuples of a state.
   *
   * <p>Kept separate from {@link #findCorrelationHashesHoldingKeys} on purpose: a single query with
   * an {@code IN (subquery)} gets, once PostgreSQL switches to a generic plan for the prepared
   * statement, a plan scanning every correlated row of the state even when only a few tuples match.
   * Here the hashes are known values, so the lookup always goes through {@code uq_wse_correlated}.
   */
  // Native: an index-usable "= ANY(array)" with a single bind parameter whatever the number of
  // hashes is not expressible in JPQL (an IN list binds one parameter per value and hits the JDBC
  // limit; array_contains renders "@>", which cannot use uq_wse_correlated); read-only.
  @Query(
      value =
          "SELECT entry_key AS \"entryKey\", entry_value AS \"entryValue\","
              + " correlation_hash AS \"correlationHash\","
              + " correlation_type AS \"correlationType\""
              + " FROM workflow_state_entries"
              + " WHERE workflow_state_id = :stateId AND entry_type = 'CORRELATED'"
              + " AND correlation_hash = ANY(CAST(:hashes AS text[]))",
      nativeQuery = true)
  List<RawWorkflowStateEntry> findCorrelatedByHashes(
      @Param("stateId") String stateId, @Param("hashes") String[] hashes);

  /** Committed execution hashes of a state. */
  @Query(
      "SELECT e.entryValue FROM WorkflowStateEntry e"
          + " WHERE e.workflowState.id = :stateId"
          + " AND e.entryType = io.openaev.database.model.WorkflowStateEntry.EntryType.HASH_EXECUTION")
  List<String> findExecutionHashes(@Param("stateId") String stateId);

  // -- Writes ------------------------------------------------------------------------------------

  /** Inserts INPUT rows ({@code keys[i]} = {@code values[i]}), ignoring the ones already stored. */
  // Native: atomic insert-or-ignore (ON CONFLICT) over unnest() arrays, not expressible in JPA;
  // rows are not indexed, audited nor streamed.
  @Transactional
  @Modifying
  @Query(
      value =
          "INSERT INTO workflow_state_entries (workflow_state_id, entry_type, entry_key, entry_value)"
              + " SELECT :stateId, 'INPUT', t.k, t.v"
              + " FROM unnest(CAST(:keys AS text[]), CAST(:values AS text[])) AS t(k, v)"
              + " ORDER BY t.k, t.v"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertInputs(
      @Param("stateId") String stateId,
      @Param("keys") String[] keys,
      @Param("values") String[] values);

  /**
   * Inserts CORRELATED rows, one per tuple field (all fields of a tuple share its hash and type),
   * ignoring the ones already stored.
   */
  // Native: atomic insert-or-ignore (ON CONFLICT) over unnest() arrays, not expressible in JPA;
  // rows are not indexed, audited nor streamed.
  @Transactional
  @Modifying
  @Query(
      value =
          "INSERT INTO workflow_state_entries"
              + " (workflow_state_id, entry_type, entry_key, entry_value,"
              + " correlation_hash, correlation_type)"
              + " SELECT :stateId, 'CORRELATED', t.k, t.v, t.h, t.ty"
              + " FROM unnest(CAST(:keys AS text[]), CAST(:values AS text[]),"
              + " CAST(:hashes AS text[]), CAST(:types AS text[])) AS t(k, v, h, ty)"
              + " ORDER BY t.h, t.k, t.v"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertCorrelated(
      @Param("stateId") String stateId,
      @Param("keys") String[] keys,
      @Param("values") String[] values,
      @Param("hashes") String[] hashes,
      @Param("types") String[] types);

  /**
   * Inserts execution hashes and returns only the ones actually inserted. This is the anti-replay
   * guard: a hash already committed — including by a concurrent transaction, which the unique index
   * {@code uq_wse_hash} serializes — is not returned, so its combination must not be executed
   * again.
   *
   * <p>Written as a data-modifying CTE so that it is executed as a query returning rows.
   */
  // Native: atomic insert-or-ignore (ON CONFLICT) over unnest() arrays, not expressible in JPA;
  // rows are not indexed, audited nor streamed.
  @Transactional
  @Query(
      value =
          "WITH inserted AS ("
              + " INSERT INTO workflow_state_entries"
              + " (workflow_state_id, entry_type, entry_key, entry_value)"
              + " SELECT :stateId, 'HASH_EXECUTION', 'HASH_EXECUTION', t.h"
              + " FROM unnest(CAST(:hashes AS text[])) AS t(h)"
              + " ORDER BY t.h"
              + " ON CONFLICT DO NOTHING"
              + " RETURNING entry_value)"
              + " SELECT entry_value FROM inserted",
      nativeQuery = true)
  List<String> insertExecutionHashes(
      @Param("stateId") String stateId, @Param("hashes") String[] hashes);

  /** Deletes the committed execution hashes of a state (re-arms its step). */
  @Transactional
  @Modifying
  @Query(
      "DELETE FROM WorkflowStateEntry e"
          + " WHERE e.workflowState.id = :stateId"
          + " AND e.entryType = io.openaev.database.model.WorkflowStateEntry.EntryType.HASH_EXECUTION")
  int deleteExecutionHashes(@Param("stateId") String stateId);
}
