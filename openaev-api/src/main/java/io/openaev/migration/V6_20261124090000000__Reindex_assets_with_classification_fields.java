package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Rebuilds the {@code asset} index so its documents carry the classification fields.
 *
 * <p>The asset document now indexes {@code asset_subcategory}, {@code asset_criticality}, {@code
 * asset_internet_facing} and {@code asset_url}. The incremental cursor only re-feeds assets updated
 * after it, so every existing document would keep missing them. Dropping the {@code
 * indexing_status} row of the type makes the engine driver drop, recreate and fully re-feed that
 * index from PostgreSQL at the next startup; no other index is touched. Idempotent and lock-light
 * (targeted DELETE on a tiny bookkeeping table).
 */
@Component
public class V6_20261124090000000__Reindex_assets_with_classification_fields
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DELETE FROM indexing_status WHERE indexing_status_type = 'asset';");
    }
  }
}
