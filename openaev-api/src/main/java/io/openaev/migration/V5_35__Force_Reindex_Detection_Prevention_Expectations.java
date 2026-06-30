package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Forces re-indexing of non-agent DETECTION and PREVENTION expectations so that {@code
 * base_security_platforms_side} is populated from their child agent-level expectations.
 *
 * <p>Before this fix, the {@code findForIndexing} SQL only aggregated security-platform IDs from
 * the expectation's own {@code inject_expectation_results}, which is always empty for non-agent
 * expectations (asset-level / asset-group-level). As a result, the ES field {@code
 * base_security_platforms_side} was always an empty set, causing the "Inject Undetected by Security
 * Platform" dashboard widget to show no data.
 *
 * <p>Bumping {@code inject_expectation_updated_at} ensures the next indexing pass picks up all
 * affected expectations and re-indexes them with the fixed query.
 *
 * @see <a href="https://github.com/OpenAEV-Platform/openaev/issues/6497">#6497</a>
 */
@Component
public class V5_35__Force_Reindex_Detection_Prevention_Expectations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "UPDATE injects_expectations"
              + " SET inject_expectation_updated_at = now()"
              + " WHERE agent_id IS NULL"
              + " AND inject_expectation_type IN ('DETECTION', 'PREVENTION')");
    }
  }
}
