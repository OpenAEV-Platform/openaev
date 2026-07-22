package io.openaev.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Remaps legacy {@code ArgumentType} labels still stored in payload argument JSON to their {@code
 * PrimitiveType} replacement.
 *
 * <p>Root cause: the chaining refactor (#6536) replaced the {@code ArgumentType} enum (labels such
 * as {@code credentials}, {@code portscan}, {@code share}, ...) with {@code PrimitiveType}, which
 * does not define most of those labels. The accompanying migration ({@code V6_20260720178453597})
 * only stripped the {@code subtype} key - it never rewrote the {@code type} values. Every payload
 * created before the refactor with a complex argument type therefore became unreadable: Jackson
 * fails to map e.g. {@code "credentials"} to {@code PrimitiveType}, hypersistence's {@code
 * JsonType} fallback rethrows as "cannot be transformed to Json object", and the whole entity load
 * fails. In production this broke injector execution and trace logging for payloads such as NetExec
 * (arguments {@code client_id: credentials} / {@code ip: targeted-asset}).
 *
 * <p>The mapping below mirrors {@code PrimitiveType.LEGACY_ARGUMENT_TYPE_LABELS} (which keeps reads
 * tolerant for data written between deploy and migration): each legacy complex type is mapped to
 * the primitive that carries its main scalar projection.
 *
 * <p>Execution is batched to stay production-safe on large tables ({@code injects_statuses} can
 * hold millions of rows): the affected row ids are collected first with a read-only scan, then
 * rewritten in chunks of {@value #BATCH_SIZE} ids. The migration runs OUTSIDE a wrapping Flyway
 * transaction ({@link #canExecuteInTransaction()} returns false) so each chunked UPDATE commits on
 * its own and row locks / WAL churn stay bounded instead of accumulating in one long transaction.
 *
 * <p>Idempotent: the UPDATEs only match rows that still contain a legacy label; after the first run
 * (or an interrupted partial run) only the remaining rows are picked up again.
 */
@Component
public class V6_20260722090000000__Remap_legacy_payload_argument_types extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;

  private static final String[][] LEGACY_TO_PRIMITIVE = {
    {"credentials", "username"},
    {"portscan", "port"},
    {"share", "share_name"},
    {"admin_username", "username"},
    {"group", "text"},
    {"computer", "hostname"},
    {"password_policy", "text"},
    {"delegation", "text"},
    {"sid", "text"},
    {"vulnerability", "cve"},
    {"account_with_password_not_required", "username"},
    {"asreproastable_account", "username"},
    {"kerberoastable_account", "username"}
  };

  /** No wrapping transaction: each chunked UPDATE commits on its own to keep locks bounded. */
  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    remapPayloadArguments(connection);
    remapStatusPayloadOutput(connection);
  }

  /** Rewrites legacy labels inside {@code payloads.payload_arguments} (json array column). */
  private void remapPayloadArguments(Connection connection) throws SQLException {
    String selectSql =
        String.format(
            """
            SELECT payload_id
            FROM payloads
            WHERE payload_arguments IS NOT NULL
              AND json_typeof(payload_arguments) = 'array'
              AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements(payload_arguments::jsonb) argument
                WHERE argument ->> 'type' IN (%s)
              )
            """,
            buildLegacyLabelList());
    String updateSql =
        String.format(
            """
            UPDATE payloads
            SET payload_arguments = (
              SELECT COALESCE(
                jsonb_agg(
                  CASE
                    WHEN argument ->> 'type' IN (%2$s)
                    THEN jsonb_set(argument, '{type}', to_jsonb(%1$s))
                    ELSE argument
                  END
                ),
                '[]'::jsonb
              )::json
              FROM jsonb_array_elements(payload_arguments::jsonb) argument
            )
            WHERE payload_id = ANY (?)
              AND payload_arguments IS NOT NULL
              AND json_typeof(payload_arguments) = 'array'
            """,
            buildCaseExpression("argument ->> 'type'"), buildLegacyLabelList());
    updateInBatches(connection, selectSql, updateSql);
  }

  /**
   * Rewrites legacy labels inside {@code injects_statuses.status_payload_output ->
   * payload_arguments} (json object column embedding an argument array).
   */
  private void remapStatusPayloadOutput(Connection connection) throws SQLException {
    String selectSql =
        String.format(
            """
            SELECT status_id
            FROM injects_statuses
            WHERE status_payload_output IS NOT NULL
              AND jsonb_typeof(status_payload_output::jsonb -> 'payload_arguments') = 'array'
              AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements(status_payload_output::jsonb -> 'payload_arguments') argument
                WHERE argument ->> 'type' IN (%s)
              )
            """,
            buildLegacyLabelList());
    String updateSql =
        String.format(
            """
            UPDATE injects_statuses
            SET status_payload_output = jsonb_set(
              status_payload_output::jsonb,
              '{payload_arguments}',
              (
                SELECT COALESCE(
                  jsonb_agg(
                    CASE
                      WHEN argument ->> 'type' IN (%2$s)
                      THEN jsonb_set(argument, '{type}', to_jsonb(%1$s))
                      ELSE argument
                    END
                  ),
                  '[]'::jsonb
                )
                FROM jsonb_array_elements(status_payload_output::jsonb -> 'payload_arguments') argument
              )
            )::json
            WHERE status_id = ANY (?)
              AND status_payload_output IS NOT NULL
              AND jsonb_typeof(status_payload_output::jsonb -> 'payload_arguments') = 'array'
            """,
            buildCaseExpression("argument ->> 'type'"), buildLegacyLabelList());
    updateInBatches(connection, selectSql, updateSql);
  }

  /**
   * Collects the ids of the rows still holding legacy labels, then applies the rewrite in chunks of
   * {@value #BATCH_SIZE} ids so each UPDATE stays short-lived. The connection is in autocommit
   * (non-transactional migration), so every chunk releases its row locks immediately.
   */
  private void updateInBatches(Connection connection, String selectSql, String updateSql)
      throws SQLException {
    List<String> ids = new ArrayList<>();
    try (Statement select = connection.createStatement();
        ResultSet results = select.executeQuery(selectSql)) {
      while (results.next()) {
        ids.add(results.getString(1));
      }
    }
    try (PreparedStatement update = connection.prepareStatement(updateSql)) {
      for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
        List<String> chunk = ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()));
        update.setArray(1, connection.createArrayOf("varchar", chunk.toArray()));
        update.executeUpdate();
      }
    }
  }

  private String buildCaseExpression(String typeAccessor) {
    StringBuilder sb = new StringBuilder("CASE ").append(typeAccessor);
    for (String[] mapping : LEGACY_TO_PRIMITIVE) {
      sb.append(" WHEN '").append(mapping[0]).append("' THEN '").append(mapping[1]).append("'");
    }
    sb.append(" END");
    return sb.toString();
  }

  private String buildLegacyLabelList() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < LEGACY_TO_PRIMITIVE.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append("'").append(LEGACY_TO_PRIMITIVE[i][0]).append("'");
    }
    return sb.toString();
  }
}
