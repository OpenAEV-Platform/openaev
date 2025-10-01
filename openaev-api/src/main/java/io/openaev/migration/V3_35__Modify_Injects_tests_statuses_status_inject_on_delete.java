package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_35__Modify_Injects_tests_statuses_status_inject_on_delete
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Drop the old foreign key constraint
    select.execute(
        """
            ALTER TABLE injects_tests_statuses
            DROP CONSTRAINT inject_test_status_inject_id_fkey;
        """);

    // Add the new foreign key constraint with ON DELETE CASCADE
    select.execute(
        """
            ALTER TABLE injects_tests_statuses
            ADD CONSTRAINT inject_test_status_inject_id_fkey
            FOREIGN KEY (status_inject) REFERENCES injects(inject_id) ON DELETE CASCADE;
        """);

    // Optionally, you can reindex if necessary (usually not required just for FK changes)
    select.execute(
        """
            CREATE INDEX IF NOT EXISTS idx_inject_test_inject ON injects_tests_statuses(status_inject);
        """);
  }
}
