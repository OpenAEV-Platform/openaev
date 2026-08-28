package io.openaev.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repairs, and then permanently guards against, JSON columns persisted as the Hypersistence {@code
 * JsonType} envelope object ({@code {"type":"json","value":"[...]","null":false}}) instead of the
 * plain JSON value the column is meant to hold.
 *
 * <p>Root cause: hypersistence-utils' {@code JsonJavaTypeDescriptor.wrap} has a fallback that, when
 * the first parse of a column's content into the declared Java type fails, Jackson-serializes the
 * raw JDBC {@code PGobject} itself - yielding {@code
 * {"type":"json","value":"<json-string>","null":false}} (the bean view of {@code PGobject}: {@code
 * getType()="json"}, {@code getValue()=<raw json>}, {@code isNull()=false}). The strict {@code
 * PayloadArgument} change in the chaining refactor (#6536) made legacy rows fail that first parse,
 * so the envelope got written. Strict list columns then throw {@code MismatchedInputException} on
 * read (this took down {@code ManagerIntegrationsSyncJob} and the Assets page); tolerant columns
 * silently re-persisted it.
 *
 * <p>The earlier {@code V6_20260720178453597} unwrap only handled array-shaped {@code
 * payload_arguments} (stripping {@code subtype}) and {@code status_payload_output}; its {@code
 * json_typeof = 'array'} guard skipped the object-wrapped shape, so wrapped rows survived. This
 * migration closes the gap across every strict JSON list/set column.
 *
 * <p>Per column it:
 *
 * <ol>
 *   <li>UNWRAPS the envelope - re-parses the inner {@code value} back into the real JSON (no data
 *       loss; the original value is recovered);
 *   <li>ADDS a CHECK constraint (via {@code NOT VALID} + {@code VALIDATE}, so validation does not
 *       take an {@code ACCESS EXCLUSIVE} lock for the scan) that forbids the envelope shape from
 *       ever being persisted again, by any code path or client - this is the "cannot insert bad
 *       data anymore" guarantee. The constraint is intentionally narrow (it only rejects the exact
 *       {@code type/value/null} PGobject envelope), so it never rejects legitimate arrays or
 *       objects.
 * </ol>
 *
 * <p>Idempotent and re-runnable: the UNWRAP only matches the envelope (gone after the first run),
 * and the guard is added only when absent. A fresh install applies the constraints and no-ops the
 * updates.
 */
@Component
public class V6_20260720193000000__Repair_wrapped_json_payload_arguments extends BaseJavaMigration {

  /** A strict JSON list/set column to repair and guard. */
  private record JsonColumn(String table, String column, String type, String constraint) {}

  // Every @Type(JsonType.class) column whose Java type is a List/Set - a surviving envelope makes
  // the row unreadable. `type` is the physical column type used for the write-back cast (json vs
  // jsonb).
  private static final JsonColumn[] COLUMNS = {
    new JsonColumn(
        "payloads", "payload_arguments", "json", "payloads_payload_arguments_no_json_envelope"),
    new JsonColumn(
        "payloads",
        "payload_prerequisites",
        "json",
        "payloads_payload_prerequisites_no_json_envelope"),
    new JsonColumn(
        "injects_expectations",
        "inject_expectation_results",
        "json",
        "iexp_results_no_json_envelope"),
    new JsonColumn(
        "injects_expectations",
        "inject_expectation_expected_security_platforms",
        "jsonb",
        "iexp_expected_sp_no_json_envelope"),
    new JsonColumn(
        "steps", "step_condition_key_types", "jsonb", "steps_condition_key_types_no_json_envelope"),
    new JsonColumn(
        "security_coverages",
        "security_coverage_attack_pattern_refs",
        "jsonb",
        "seccov_attack_pattern_refs_no_json_envelope"),
    new JsonColumn(
        "security_coverages",
        "security_coverage_vulnerabilities_refs",
        "jsonb",
        "seccov_vulnerabilities_refs_no_json_envelope"),
    new JsonColumn(
        "security_coverages",
        "security_coverage_indicators_refs",
        "jsonb",
        "seccov_indicators_refs_no_json_envelope"),
    new JsonColumn(
        "security_coverages",
        "security_coverage_artifacts_refs",
        "jsonb",
        "seccov_artifacts_refs_no_json_envelope"),
  };

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      for (JsonColumn c : COLUMNS) {
        repairAndGuard(stmt, c);
      }
    }
  }

  // Table / column / constraint names are fixed code literals (never user input), so formatting
  // them into the statements is safe.
  private void repairAndGuard(Statement stmt, JsonColumn c) throws SQLException {
    // Reusable predicate: the value is the exact PGobject JsonType envelope.
    String isEnvelope =
        String.format(
            "jsonb_typeof(%1$s::jsonb) = 'object' "
                + "AND (%1$s::jsonb) ? 'type' AND (%1$s::jsonb) ? 'value' AND (%1$s::jsonb) ? 'null' "
                + "AND (%1$s::jsonb) ->> 'type' = 'json'",
            c.column());

    // 1. Unwrap: recover the inner value into the column's physical type.
    stmt.execute(
        String.format(
            "UPDATE %1$s SET %2$s = ((%2$s::jsonb) ->> 'value')::%3$s "
                + "WHERE %2$s IS NOT NULL AND (%4$s);",
            c.table(), c.column(), c.type(), isEnvelope));

    // 2. Guard: forbid the envelope shape forever (lock-light NOT VALID add + VALIDATE).
    stmt.execute(
        String.format(
            "DO $$ BEGIN "
                + "IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '%4$s') THEN "
                + "ALTER TABLE %1$s ADD CONSTRAINT %4$s CHECK (%2$s IS NULL OR NOT (%3$s)) NOT VALID; "
                + "ALTER TABLE %1$s VALIDATE CONSTRAINT %4$s; "
                + "END IF; END $$;",
            c.table(), c.column(), isEnvelope, c.constraint()));
  }
}
