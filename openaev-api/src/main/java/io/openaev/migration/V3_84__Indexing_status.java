package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_84__Indexing_status extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                   ALTER TABLE exercises ALTER COLUMN exercise_created_at type timestamp with time zone using exercise_created_at::timestamp with time zone;
                   ALTER TABLE exercises ALTER COLUMN exercise_updated_at type timestamp with time zone using exercise_updated_at::timestamp with time zone;

                   ALTER TABLE scenarios ALTER COLUMN scenario_created_at type timestamp with time zone using scenario_created_at::timestamp with time zone;
                   ALTER TABLE scenarios ALTER COLUMN scenario_updated_at type timestamp with time zone using scenario_updated_at::timestamp with time zone;

                   ALTER TABLE injects ALTER COLUMN inject_created_at type timestamp with time zone using inject_created_at::timestamp with time zone;
                   ALTER TABLE injects ALTER COLUMN inject_updated_at type timestamp with time zone using inject_updated_at::timestamp with time zone;

                   ALTER TABLE findings ALTER COLUMN finding_created_at type timestamp with time zone using finding_created_at::timestamp with time zone;
                   ALTER TABLE findings ALTER COLUMN finding_updated_at type timestamp with time zone using finding_updated_at::timestamp with time zone;

                   CREATE TABLE indexing_status (
                       indexing_status_type text not null,
                       indexing_status_indexing_date timestamp with time zone not null,
                       primary key (indexing_status_type)
                   );
                   CREATE INDEX idx_indexing_status_type ON indexing_status (indexing_status_type);
                """);
  }
}
