package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_69__Add_user_event extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      // Table creation
      stmt.execute(
          """
        CREATE TABLE user_events (
          user_event_id varchar(255) PRIMARY KEY,
          user_id varchar(255),
          user_event_type VARCHAR(50) NOT NULL,
          user_event_payload JSONB,
          user_event_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
        );
      """);

      // Indexes
      stmt.execute(
          """
        CREATE INDEX idx_user_events_type_created_at
        ON user_events (user_event_type, user_event_created_at);
      """);
      stmt.execute(
          """
        CREATE INDEX idx_user_events_user_id
        ON user_events (user_id);
      """);
    }
  }
}
