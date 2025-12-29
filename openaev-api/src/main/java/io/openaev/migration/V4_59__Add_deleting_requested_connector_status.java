package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_59__Add_deleting_requested_connector_status extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute("ALTER TYPE connector_instance_requested_status_type ADD VALUE 'deleting'; ");
      select.execute(
          """
                          ALTER TABLE connector_instances
                          ADD COLUMN connector_instance_enable_deletion boolean DEFAULT FALSE;
                      """);
    }
  }
}
