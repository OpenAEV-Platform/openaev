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
      statement.execute(
          """
                        DELETE FROM parameters WHERE parameter_key = 'starterpack';
                        """);
      statement.execute(
          """
                        DROP INDEX IF EXISTS tag_name_unique;
                        """);
      statement.execute(
          """
                        ALTER TABLE tags
                        ADD CONSTRAINT tag_name_tenant_unique UNIQUE (tag_name, tenant_id);
                        """);
    }
  }
}
