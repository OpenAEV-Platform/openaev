package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** What a catalogue item needs to run: OpenAEV version, edition, injectors and executors. */
@Component
public class V6_20261015100000000__Catalog_items_requires extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE catalog_items ADD COLUMN IF NOT EXISTS catalog_item_requires TEXT");
    }
  }
}
