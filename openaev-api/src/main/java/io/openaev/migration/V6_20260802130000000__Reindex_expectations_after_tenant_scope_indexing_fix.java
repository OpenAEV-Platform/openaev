package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the inject-expectation index after fixing the tenant-scope-less indexing sweep.
 *
 * <p>Since the {@code collectors} table was activated for multi-tenancy v2 filtering, the engine
 * sync job fetched indexing rows without any tenant scope: {@code can_access_tenant} is fail-closed
 * with no scope set, so the {@code collectors} joins of the expectation indexing query silently
 * matched nothing. Every expectation document indexed since then carries an empty {@code
 * base_security_platforms_side} for collector-sourced results (e.g. EDR collectors such as
 * Microsoft Defender), which emptied the security platform posture pages and the security platform
 * chips of asset expectation lists. Asset-sourced results (e.g. Nuclei registered as a security
 * platform asset) were unaffected.
 *
 * <p>The sweep now runs under an explicit all-tenants scope; this migration converges the documents
 * indexed while the attribution was broken. Dropping the {@code indexing_status} row of a type
 * makes the engine driver drop, recreate and fully re-feed that index from PostgreSQL at the next
 * startup, so only the affected type is rebuilt. Idempotent and lock-light (targeted DELETE on a
 * tiny bookkeeping table).
 */
@Component
public class V6_20260802130000000__Reindex_expectations_after_tenant_scope_indexing_fix
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'expectation-inject';");
    }
  }
}
