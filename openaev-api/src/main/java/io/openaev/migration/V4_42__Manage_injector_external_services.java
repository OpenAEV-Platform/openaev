package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_42__Manage_injector_external_services extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
                    ALTER TABLE injectors
                    ADD COLUMN IF NOT EXISTS injector_dependencies text[] DEFAULT '{}';
                """);
      statement.execute(
          """
                    UPDATE injectors
                    SET injector_dependencies = array_append(injector_dependencies, 'SMTP')
                    WHERE
                     injector_type IN ('openbas_email', 'openbas_challenge', 'openbas_channel') AND
                     ('SMTP' != ALL(injector_dependencies) OR injector_dependencies IS NULL);
                """);
      statement.execute(
          """
                    UPDATE injectors
                    SET injector_dependencies = array_append(injector_dependencies, 'IMAP')
                    WHERE
                     injector_type IN ('openbas_email', 'openbas_challenge', 'openbas_channel') AND
                     ('IMAP' != ALL(injector_dependencies) OR injector_dependencies IS NULL);
                """);
      statement.execute(
          """
                    UPDATE injectors
                    SET injector_dependencies = array_append(injector_dependencies, 'SMTP')
                    WHERE
                     injector_type IN ('openeav_email', 'openeav_challenge', 'openeav_channel') AND
                     ('SMTP' != ALL(injector_dependencies) OR injector_dependencies IS NULL);
                """);
      statement.execute(
          """
                    UPDATE injectors
                    SET injector_dependencies = array_append(injector_dependencies, 'IMAP')
                    WHERE
                     injector_type IN ('openeav_email', 'openeav_challenge', 'openeav_channel') AND
                     ('IMAP' != ALL(injector_dependencies) OR injector_dependencies IS NULL);
                """);
      statement.execute(
          """
                    DELETE FROM parameters
                    WHERE parameter_key = 'imap_unavailable';
                """);
    }
  }

  /* ROLLBACK
   ALTER TABLE injectors DROP COLUMN IF EXISTS injector_dependencies;
  */
}
