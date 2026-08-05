package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Drops the natural-key unique index {@code uq_ap_find_natural_key} on {@code attackpath_finding}.
 *
 * <p>It is redundant: the primary key {@code attackpath_finding_id} is a deterministic, injective
 * encoding of the same natural key ({@code simulationId, type, field, value, endpointKey}, see
 * {@code AttackPathIds.findingRow}; the value is hashed only when its raw encoding would overflow
 * the column, so pre-existing rows keep their legacy id), so the same finding always resolves to
 * the same id and the PK alone enforces natural-key uniqueness. The findings copy now upserts via
 * {@code ON CONFLICT (attackpath_finding_id)}.
 *
 * <p>Dropping it also removes a latent failure: that index keyed on the raw {@code value} ({@code
 * text}), which for a long parsed output (ADR-004) could exceed the Postgres btree tuple limit
 * (~2704 bytes) and fail the INSERT on the index. The PK stays bounded because a long value is
 * hashed inside the id; the {@code value} column stays {@code text} (un-indexed) for display.
 * Idempotent.
 */
@Component
public class V6_20260804093000000__Drop_attackpath_finding_natural_key_index
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS uq_ap_find_natural_key;");
    }
  }
}
