package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds partial and composite indexes that accelerate the {@code findForIndexing} queries used by
 * the ES/OpenSearch indexing cursor loop.
 *
 * <ul>
 *   <li>InjectExpectation: partial index on {@code (inject_expectation_updated_at) WHERE agent_id
 *       IS NULL} — avoids bitmap-AND or seq-scan on every 15-second tick.
 *   <li>VulnerableEndpoint: partial index on {@code (asset_updated_at) WHERE asset_type =
 *       'Endpoint'}, partial index on {@code (finding_inject_id) WHERE finding_type = 'CVE'}, and
 *       composite index on {@code findings_assets(asset_id, finding_id)}.
 * </ul>
 */
@Component
public class V6_20260630141546018__Add_Indexing_Query_Indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // InjectExpectation: partial index for the cursor CTE branch
      // WHERE ie.agent_id IS NULL AND ie.inject_expectation_updated_at > :from
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_expectations_indexing_cursor "
              + "ON injects_expectations (inject_expectation_updated_at) "
              + "WHERE agent_id IS NULL");

      // VulnerableEndpoint: partial index for endpoints updated after :from
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_endpoint_updated_at "
              + "ON assets (asset_updated_at) "
              + "WHERE asset_type = 'Endpoint'");

      // VulnerableEndpoint: partial index for CVE findings joined on inject
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_cve_inject "
              + "ON findings (finding_inject_id) "
              + "WHERE finding_type = 'CVE'");

      // VulnerableEndpoint: composite index for findings_assets reverse lookup
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_assets_asset_finding "
              + "ON findings_assets (asset_id, finding_id)");
    }
  }
}
