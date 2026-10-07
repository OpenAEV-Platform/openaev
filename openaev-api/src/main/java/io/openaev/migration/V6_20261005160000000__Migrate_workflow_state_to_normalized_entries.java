package io.openaev.migration;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * WorkflowState storage refactor (ADR-011) — moves the execution state of in-flight runs from the
 * legacy JSONB column to the normalized {@code workflow_state_entries} rows, so that the engine
 * only ever reads and writes the normalized store. No dual-run coexistence: the {@code
 * workflows.storage_mode} routing flag added by {@code V6_20261005105200000} was never used by the
 * application and is dropped here.
 *
 * <ol>
 *   <li>Deletes the states of runs that are neither {@code RUN} nor {@code STOP} (paused): states
 *       of ended runs are deleted at end of run, and the remaining ones predate that cleanup.
 *   <li>Converts the JSONB document of every remaining state into rows: one {@code INPUT} row per
 *       value, one {@code CORRELATED} row per tuple field (sharing the tuple's MurmurHash3-128
 *       content hash and type), one {@code HASH_EXECUTION} row per committed execution hash.
 *       Duplicate global states of a same run (possible before global-state uniqueness was
 *       enforced) are merged into the oldest one, then deleted.
 *   <li>Enforces one global state per run ({@code uq_workflow_state_global}).
 *   <li>Makes the legacy JSONB column nullable and drops its GIN index: the column is no longer
 *       read nor written by the application, and is kept one release as a safety net before being
 *       dropped (its database default still fills it for new rows).
 *   <li>Drops {@code workflows.storage_mode}: unused by any code, so there is no reader to
 *       deprecate first.
 * </ol>
 *
 * <p>Runs in a single transaction (rolled back as a whole on failure). Conversion inserts use
 * {@code ON CONFLICT DO NOTHING}, so re-running the conversion never duplicates entries — but a
 * manual re-run is only safe before the engine has run: afterwards the JSONB documents are stale,
 * and re-converting them would bring back entries the engine has removed since (e.g. execution
 * hashes cleared to re-arm a step).
 */
@Component
public class V6_20261005160000000__Migrate_workflow_state_to_normalized_entries
    extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;

  private static final String INSERT_ENTRY =
      "INSERT INTO workflow_state_entries"
          + " (workflow_state_id, entry_type, entry_key, entry_value,"
          + " correlation_hash, correlation_type)"
          + " VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();

    // 1. States of non-active runs.
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "DELETE FROM workflow_states s USING workflows w"
              + " WHERE s.workflow_execution_id = w.workflow_id"
              + " AND w.workflow_status NOT IN ('RUN', 'STOP');");
    }

    // 2. Conversion, merging duplicate global states into the oldest one of their run.
    Map<String, String> targetGlobalStateByRun = oldestGlobalStateByRun(connection);
    List<String[]> states = statesWithLegacyEntries(connection);
    try (PreparedStatement select =
            connection.prepareStatement(
                "SELECT workflow_state_entries::text FROM workflow_states"
                    + " WHERE workflow_state_id = ?");
        PreparedStatement insert = connection.prepareStatement(INSERT_ENTRY)) {
      EntryBatch batch = new EntryBatch(insert);
      for (String[] state : states) {
        String stateId = state[0];
        String targetStateId =
            state[2] == null ? targetGlobalStateByRun.getOrDefault(state[1], stateId) : stateId;
        select.setString(1, stateId);
        try (ResultSet rs = select.executeQuery()) {
          if (rs.next() && rs.getString(1) != null) {
            addEntries(batch, targetStateId, rs.getString(1));
          }
        }
      }
      batch.flush();
    }

    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "DELETE FROM workflow_states s USING ("
              + " SELECT workflow_state_id, row_number() OVER ("
              + "   PARTITION BY workflow_execution_id"
              + "   ORDER BY workflow_state_created_at, workflow_state_id) AS rn"
              + " FROM workflow_states WHERE workflow_step_template_id IS NULL) d"
              + " WHERE s.workflow_state_id = d.workflow_state_id AND d.rn > 1;");

      // 3. One global state per run. Supersedes the non-unique idx_wf_state_global_lookup.
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_state_global"
              + " ON workflow_states (workflow_execution_id)"
              + " WHERE workflow_step_template_id IS NULL;");
      statement.execute("DROP INDEX IF EXISTS idx_wf_state_global_lookup;");

      // 4. Legacy JSONB column: no longer mapped, kept one release.
      statement.execute(
          "ALTER TABLE workflow_states ALTER COLUMN workflow_state_entries DROP NOT NULL;");
      // Maintained on every state insert for a column nothing reads any more.
      statement.execute("DROP INDEX IF EXISTS idx_wf_state_entries_gin;");
      statement.execute(
          "COMMENT ON COLUMN workflow_states.workflow_state_entries IS"
              + " 'DEPRECATED — ADR-011: legacy JSONB state, no longer read nor written."
              + " Kept one release as a safety net, then dropped.';");

      // 5. Unused dual-run routing flag.
      statement.execute("ALTER TABLE workflows DROP COLUMN IF EXISTS storage_mode;");
    }
  }

  private static Map<String, String> oldestGlobalStateByRun(Connection connection)
      throws Exception {
    Map<String, String> result = new HashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT DISTINCT ON (workflow_execution_id) workflow_execution_id, workflow_state_id"
                    + " FROM workflow_states WHERE workflow_step_template_id IS NULL"
                    + " ORDER BY workflow_execution_id, workflow_state_created_at,"
                    + " workflow_state_id")) {
      while (rs.next()) {
        result.put(rs.getString(1), rs.getString(2));
      }
    }
    return result;
  }

  /** {id, run id, step template id} of every state still holding a JSONB document. */
  private static List<String[]> statesWithLegacyEntries(Connection connection) throws Exception {
    List<String[]> result = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT workflow_state_id, workflow_execution_id, workflow_step_template_id"
                    + " FROM workflow_states WHERE workflow_state_entries IS NOT NULL"
                    + " ORDER BY workflow_state_id")) {
      while (rs.next()) {
        result.add(new String[] {rs.getString(1), rs.getString(2), rs.getString(3)});
      }
    }
    return result;
  }

  /** Adds the rows of one legacy JSONB document to the batch. */
  private static void addEntries(EntryBatch batch, String stateId, String json)
      throws SQLException {
    JsonElement root = JsonParser.parseString(json);
    if (!root.isJsonObject()) {
      return;
    }
    JsonObject document = root.getAsJsonObject();

    for (JsonElement inputElement : array(document, "inputs")) {
      if (!inputElement.isJsonObject()) {
        continue;
      }
      String key = string(inputElement.getAsJsonObject(), "key");
      if (key == null) {
        continue;
      }
      for (JsonElement value : array(inputElement.getAsJsonObject(), "values")) {
        if (value.isJsonPrimitive()) {
          batch.add(stateId, "INPUT", key, value.getAsString(), null, null);
        }
      }
    }

    for (JsonElement tupleElement : array(document, "correlated")) {
      if (!tupleElement.isJsonObject()) {
        continue;
      }
      String type = string(tupleElement.getAsJsonObject(), "type");
      List<String[]> pairs = new ArrayList<>();
      for (JsonElement pairElement : array(tupleElement.getAsJsonObject(), "values")) {
        if (pairElement.isJsonObject()) {
          String key = string(pairElement.getAsJsonObject(), "key");
          String value = string(pairElement.getAsJsonObject(), "value");
          if (key != null && value != null) {
            pairs.add(new String[] {key, value});
          }
        }
      }
      if (pairs.isEmpty()) {
        continue;
      }
      String hash = hashTuple(pairs);
      for (String[] pair : pairs) {
        batch.add(stateId, "CORRELATED", pair[0], pair[1], hash, type);
      }
    }

    for (JsonElement hash : array(document, "hashExecution")) {
      if (hash.isJsonPrimitive()) {
        batch.add(stateId, "HASH_EXECUTION", "HASH_EXECUTION", hash.getAsString(), null, null);
      }
    }
  }

  /**
   * JDBC insert batch executed every {@link #BATCH_SIZE} rows while rows are being added, so that
   * its size stays bounded whatever the size of a single legacy document.
   */
  private static final class EntryBatch {
    private final PreparedStatement insert;
    private int pending;

    private EntryBatch(PreparedStatement insert) {
      this.insert = insert;
    }

    void add(
        String stateId,
        String type,
        String key,
        String value,
        String correlationHash,
        String correlationType)
        throws SQLException {
      insert.setString(1, stateId);
      insert.setString(2, type);
      insert.setString(3, key);
      insert.setString(4, value);
      insert.setString(5, correlationHash);
      insert.setString(6, correlationType);
      insert.addBatch();
      if (++pending >= BATCH_SIZE) {
        flush();
      }
    }

    void flush() throws SQLException {
      if (pending > 0) {
        insert.executeBatch();
        pending = 0;
      }
    }
  }

  /**
   * Content hash of a correlated tuple. Frozen copy of {@code ChainingHashUtils.hashTuple} (a
   * migration must not change behavior when application code evolves): pairs sorted by key then
   * value, each key and value length-prefixed ({@code <length>:<text>}) so that no value can forge
   * a field boundary, MurmurHash3-128 hex.
   */
  static String hashTuple(List<String[]> pairs) {
    StringBuilder sb = new StringBuilder();
    pairs.stream()
        .sorted(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]))
        .forEach(
            p -> {
              sb.append(p[0].length()).append(':').append(p[0]);
              sb.append(p[1].length()).append(':').append(p[1]);
            });
    return Hashing.murmur3_128().hashString(sb.toString(), StandardCharsets.UTF_8).toString();
  }

  private static JsonArray array(JsonObject object, String member) {
    JsonElement element = object.get(member);
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
  }

  private static String string(JsonObject object, String member) {
    JsonElement element = object.get(member);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
  }
}
