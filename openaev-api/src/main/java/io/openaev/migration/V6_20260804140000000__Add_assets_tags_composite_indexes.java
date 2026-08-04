package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260804140000000__Add_assets_tags_composite_indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tags_asset_tag ON assets_tags (asset_id, tag_id)");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_assets_tags_tag_asset ON assets_tags (tag_id, asset_id)");
    }
  }
}
