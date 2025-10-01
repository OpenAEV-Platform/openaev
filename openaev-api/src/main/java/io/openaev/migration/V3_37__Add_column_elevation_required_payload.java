package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_37__Add_column_elevation_required_payload extends BaseJavaMigration {

  @Override
  public void migrate(final Context context) throws Exception {
    final Statement select = context.getConnection().createStatement();
    select.execute("ALTER TABLE payloads ADD payload_elevation_required bool default false;");
  }
}
