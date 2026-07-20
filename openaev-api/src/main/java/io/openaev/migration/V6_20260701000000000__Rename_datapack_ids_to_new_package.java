package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260701000000000__Rename_datapack_ids_to_new_package extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE datapacks SET datapack_id = REPLACE(datapack_id, 'io.openaev.datapack.packs.', 'io.openaev.processor.datapack.')
          WHERE datapack_id LIKE 'io.openaev.datapack.packs.%'
          """);
    }
  }
}
