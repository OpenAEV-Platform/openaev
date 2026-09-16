package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Data backfill for external URLs containing duplicated slashes.
 *
 * <p>When the configured OpenCTI base URL ended with a trailing {@code /}, the concatenation with
 * the relative path produced URLs such as {@code https://opencti.local//dashboard/id/xxx}. The
 * concatenation is now normalised in {@code SecurityCoverageService}, but rows persisted before the
 * fix keep the extra slash.
 *
 * <p>This migration rewrites {@code security_coverages.security_coverage_external_url} and {@code
 * scenarios.scenario_external_url}: the scheme separator ({@code ://}) is preserved as-is and every
 * remaining sequence of consecutive slashes in the path is collapsed into a single one. Only rows
 * actually affected are updated, which makes the migration idempotent.
 */
@Component
public class V6_20260902100000000__Fix_duplicated_slash_in_external_urls extends BaseJavaMigration {

  /** Matches the scheme part of an absolute URL, e.g. {@code https://}. */
  private static final String SCHEME_PATTERN = "^[a-zA-Z][a-zA-Z0-9+.-]*://";

  private static String normalizeUrlStatement(String table, String column) {
    return """
        UPDATE %s
        SET %s = substring(%s from '%s')
                 || regexp_replace(substring(%s from '%s(.*)$'), '/{2,}', '/', 'g')
        WHERE %s ~ '%s.*//';
        """
        .formatted(
            table, column, column, SCHEME_PATTERN, column, SCHEME_PATTERN, column, SCHEME_PATTERN);
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // 1. Security coverages external URLs (built from the OpenCTI base URL).
      statement.execute(
          normalizeUrlStatement("security_coverages", "security_coverage_external_url"));

      // 2. Scenario external URLs (propagated from the same source).
      statement.execute(normalizeUrlStatement("scenarios", "scenario_external_url"));
    }
  }
}
