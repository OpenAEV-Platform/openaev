package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_82__Collectors extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Create table
    select.execute(
        """
          CREATE TABLE collectors (
            collector_id varchar(255) not null constraint collector_pkey primary key,
            collector_created_at timestamp not null default now(),
            collector_updated_at timestamp not null default now(),
            collector_name varchar(255) not null,
            collector_type varchar(255) not null,
            collector_period int not null,
            collector_last_execution timestamp
          );
          CREATE INDEX idx_collectors on collectors (collector_id);
          CREATE UNIQUE INDEX collectors_unique on collectors (collector_type);
     """);
  }
}
