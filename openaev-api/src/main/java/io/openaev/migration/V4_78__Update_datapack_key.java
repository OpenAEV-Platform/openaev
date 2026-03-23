package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_78__Update_datapack_key extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                        ALTER TABLE datapacks
                        DROP CONSTRAINT datapacks_pkey;
                        """);
      statement.execute(
          """
                        ALTER TABLE datapacks
                        ADD CONSTRAINT datapacks_pkey UNIQUE (datapack_id, tenant_id);
                        """);
    }
  }
}
