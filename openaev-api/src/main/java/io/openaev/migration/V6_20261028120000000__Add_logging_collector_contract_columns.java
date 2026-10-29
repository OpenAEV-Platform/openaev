package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Logging expectation collector contract: the expectation types a collector declares at
 * registration (null keeps the legacy Detection and Prevention behaviour), and how a Logging trace
 * matched the attack.
 */
@Component
public class V6_20261028120000000__Add_logging_collector_contract_columns
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE collectors ADD COLUMN IF NOT EXISTS collector_supported_expectation_types text[]");
      statement.execute(
          "ALTER TABLE injects_expectations_traces ADD COLUMN IF NOT EXISTS inject_expectation_trace_match_type varchar(255)");
    }
  }
}
