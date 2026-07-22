package io.openaev.migration;

import java.sql.SQLException;
import java.sql.Statement;
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
 * <p>Idempotent: the UPDATE only matches rows that still contain a legacy label; after the first
 * run there are none left.
 */
@Component
public class V6_20260722090000000__Remap_legacy_payload_argument_types extends BaseJavaMigration {

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

  @Override
  public void migrate(Context context) throws Exception {
    String caseExpression = buildCaseExpression("argument ->> 'type'");
    String legacyLabelList = buildLegacyLabelList();

    try (Statement stmt = context.getConnection().createStatement()) {
      remapPayloadArguments(stmt, caseExpression, legacyLabelList);
      remapStatusPayloadOutput(stmt, caseExpression, legacyLabelList);
    }
  }

  /** Rewrites legacy labels inside {@code payloads.payload_arguments} (json array column). */
  private void remapPayloadArguments(Statement stmt, String caseExpression, String legacyLabelList)
      throws SQLException {
    stmt.execute(
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
            WHERE payload_arguments IS NOT NULL
              AND json_typeof(payload_arguments) = 'array'
              AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements(payload_arguments::jsonb) argument
                WHERE argument ->> 'type' IN (%2$s)
              );
            """,
            caseExpression, legacyLabelList));
  }

  /**
   * Rewrites legacy labels inside {@code injects_statuses.status_payload_output ->
   * payload_arguments} (json object column embedding an argument array).
   */
  private void remapStatusPayloadOutput(
      Statement stmt, String caseExpression, String legacyLabelList) throws SQLException {
    stmt.execute(
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
            WHERE status_payload_output IS NOT NULL
              AND jsonb_typeof(status_payload_output::jsonb -> 'payload_arguments') = 'array'
              AND EXISTS (
                SELECT 1
                FROM jsonb_array_elements(status_payload_output::jsonb -> 'payload_arguments') argument
                WHERE argument ->> 'type' IN (%2$s)
              );
            """,
            caseExpression, legacyLabelList));
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
