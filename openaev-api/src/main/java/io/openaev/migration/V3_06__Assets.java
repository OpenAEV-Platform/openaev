package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_06__Assets extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute("ALTER TABLE assets DROP column asset_temporary_execution;");
    select.execute(
        "ALTER TABLE assets ADD column asset_parent varchar(255) constraint asset_parent_fk references assets on delete cascade;");
    select.execute(
        "ALTER TABLE assets ADD column asset_inject varchar(255) constraint asset_inject_fk references injects on delete cascade;");
    select.execute("CREATE UNIQUE INDEX assets_unique on assets (asset_external_reference);");
  }
}
