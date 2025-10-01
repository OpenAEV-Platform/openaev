package io.openaev.migration;

import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_47__Enforce_payload_command_definition_consistency extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws SQLException {
    Statement migrator = context.getConnection().createStatement();
    migrator.execute(
        """
                    UPDATE payloads
                    SET command_executor = NULL, command_content = NULL
                    WHERE (command_executor = '') IS NOT FALSE OR (command_content = '') IS NOT FALSE
                    """);
    migrator.execute(
        """
                    ALTER TABLE payloads
                    ADD CONSTRAINT chk_payload_cmd_consistency
                    CHECK ((command_executor IS NULL AND command_content IS NULL)
                          OR ((command_executor <> '') IS TRUE AND (command_content <> '') IS TRUE))
                    """);

    migrator.execute(
        """
                    UPDATE payloads
                    SET payload_cleanup_executor = NULL, payload_cleanup_command = NULL
                    WHERE (payload_cleanup_executor = '') IS NOT FALSE OR (payload_cleanup_command = '') IS NOT FALSE
                    """);
    migrator.execute(
        """
                    ALTER TABLE payloads
                    ADD CONSTRAINT chk_payload_cleanup_cmd_consistency
                    CHECK ((payload_cleanup_executor IS NULL AND payload_cleanup_command IS NULL)
                          OR ((payload_cleanup_executor <> '') IS TRUE AND (payload_cleanup_command <> '') IS TRUE))
                    """);
  }
}
