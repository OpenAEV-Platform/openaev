package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_04__Assets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("TRUNCATE assets CASCADE;");
    select.execute("ALTER TABLE assets DROP column asset_sources;");
    select.execute("ALTER TABLE assets DROP column asset_blobs;");
    select.execute("ALTER TABLE assets ADD asset_executor varchar(255);");
    select.execute("ALTER TABLE assets ADD asset_external_reference varchar(255);");
    select.execute("ALTER TABLE assets ADD asset_temporary_execution bool default false;");
    select.execute(
        "ALTER TABLE assets ADD CONSTRAINT executor_fk FOREIGN KEY (asset_executor) REFERENCES executors(executor_id) ON DELETE SET NULL;");
    select.execute("ALTER TABLE injectors ADD injector_executor_commands hstore;");
  }
}
