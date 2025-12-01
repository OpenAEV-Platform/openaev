package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_51__Add_Connector_Instance_Logs extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement select = context.getConnection().createStatement()) {
      select.execute(
          """
            CREATE TABLE connector_instance_logs(
                connector_instance_log_id VARCHAR(255) NOT NULL CONSTRAINT connector_instance_logs_pkey PRIMARY KEY,
                connector_instance_log TEXT NOT NULL,
                connector_configuration_created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
                connector_instance_id VARCHAR(255) NOT NULL REFERENCES connector_instances(connector_instance_id) ON DELETE CASCADE
            );
            """);

      select.execute(
          """
         CREATE TYPE connector_instance_current_status_type AS ENUM ('started','stopped');
         CREATE TYPE connector_instance_requested_status_type AS ENUM ('starting','stopping');
         """);
      select.execute(
          """
        ALTER TABLE connector_instances
        DROP COLUMN IF EXISTS connector_instance_current_status,
        DROP COLUMN IF EXISTS connector_instance_requested_status,
        ADD COLUMN connector_instance_current_status connector_instance_current_status_type NOT NULL,
        ADD COLUMN connector_instance_requested_status connector_instance_requested_status_type NOT NULL
        """);
    }
  }
}
