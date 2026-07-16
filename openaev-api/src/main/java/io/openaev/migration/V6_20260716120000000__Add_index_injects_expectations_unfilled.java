package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds a partial index on {@code injects_expectations} to speed up the expiration manager query
 * that fetches unfilled expectations ({@code inject_expectation_score IS NULL}).
 *
 * <p>Without this index, the query performs a full sequential scan of the entire table — which can
 * take several minutes on large datasets — just to find the small subset of rows that have not been
 * scored yet.
 *
 * <p>The index covers {@code inject_expectation_created_at} (used in {@code ORDER BY}) and is
 * filtered to rows where {@code inject_expectation_score IS NULL}, keeping it small and
 * automatically maintained as expectations get scored.
 */
@Component
public class V6_20260716120000000__Add_index_injects_expectations_unfilled
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_injects_expectations_unfilled
              ON injects_expectations (inject_expectation_created_at)
              WHERE inject_expectation_score IS NULL
          """);
    }
  }
}
