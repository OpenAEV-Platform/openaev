package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Merges {@code ASSIGN_MARKING}/{@code DELETE_MARKING_ASSIGNMENT} into {@code
 * MANAGE_MARKING_ASSIGNMENT}; inserts the merged row before deleting the old ones to avoid a
 * primary-key collision on roles holding both.
 */
@Component
public class V6_20261028090000000__Merge_marking_assignment_capabilities extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          INSERT INTO roles_capabilities (role_id, capability)
          SELECT DISTINCT role_id, 'MANAGE_MARKING_ASSIGNMENT'
          FROM roles_capabilities
          WHERE capability IN ('ASSIGN_MARKING', 'DELETE_MARKING_ASSIGNMENT')
          ON CONFLICT (role_id, capability) DO NOTHING
          """);
      statement.execute(
          """
          DELETE FROM roles_capabilities
          WHERE capability IN ('ASSIGN_MARKING', 'DELETE_MARKING_ASSIGNMENT')
          """);
    }
  }
}
