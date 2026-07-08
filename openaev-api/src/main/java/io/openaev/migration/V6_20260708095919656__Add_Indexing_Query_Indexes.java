package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds partial indexes that accelerate the {@code findForIndexing} queries used by the
 * ES/OpenSearch indexing cursor loop. Only indexes whose usage was confirmed by EXPLAIN ANALYZE.
 *
 * <ul>
 *   <li>InjectExpectation: partial index on {@code (inject_expectation_updated_at) WHERE agent_id
 *       IS NULL} — 437× faster (24.9 ms → 0.057 ms), 5 410 → 12 buffer hits.
 *   <li>Findings: partial index on {@code (finding_inject_id) WHERE finding_type = 'CVE'} — 32×
 *       faster (27.3 ms → 0.84 ms), 3 385 → 106 buffer hits.
 * </ul>
 */
@Component
public class V6_20260708095919656__Add_Indexing_Query_Indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // InjectExpectation: partial index for the cursor CTE branch
      // WHERE ie.agent_id IS NULL AND ie.inject_expectation_updated_at > :from
      // EXPLAIN proof: 24.9ms → 0.057ms (437×), 5410 → 12 buffers
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_injects_expectations_indexing_cursor "
              + "ON injects_expectations (inject_expectation_updated_at) "
              + "WHERE agent_id IS NULL");

      // VulnerableEndpoint: partial index for CVE findings joined on inject
      // EXPLAIN proof: 27.3ms → 0.84ms (32×), 3385 → 106 buffers
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_findings_cve_inject "
              + "ON findings (finding_inject_id) "
              + "WHERE finding_type = 'CVE'");
    }
  }
}
