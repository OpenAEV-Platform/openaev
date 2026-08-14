package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a supporting index for the findings list's "distinct per (type, value)" deduplication query
 * (see {@code FindingSpecification#distinctTypeValueWithFilter}), which groups by {@code
 * (finding_type, finding_value)} and, within each group, picks the row with the greatest {@code
 * finding_updated_at} via a correlated subquery.
 *
 * <p>The only pre-existing index touching these columns is {@code unique_finding_constraint} on
 * {@code (finding_inject_id, finding_type, finding_value, finding_field)} - its leading column is
 * {@code finding_inject_id}, so Postgres cannot use it for a query that groups across all injects.
 * Without a matching index, every findings list request forces a sequential scan plus an in-memory
 * sort/group over the whole (filtered) table to resolve the correlated "max updated_at per group"
 * subquery, which is the main driver of the slow list load reported on this branch.
 *
 * <p>Column order matches the query's access pattern: {@code finding_type} and {@code
 * finding_value} are the {@code GROUP BY} / equality-join keys, and the trailing {@code
 * finding_updated_at} lets Postgres resolve the correlated {@code MAX(finding_updated_at)} for a
 * group directly from the index without a separate heap/sort step.
 *
 * <p>Additive, idempotent, and lock-light: {@code CREATE INDEX IF NOT EXISTS} takes only a
 * short-lived lock to register the new index and does not rewrite the table, so it is safe to run
 * on a live database without blocking reads/writes to {@code findings} for more than the index
 * build itself.
 */
@Component
public class V6_20260812082950000__Add_finding_type_value_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_type_value_updated_at"
              + " ON findings (finding_type, finding_value, finding_updated_at);");
    }
  }
}
