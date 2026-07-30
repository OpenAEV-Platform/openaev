package io.openaev.migration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V6_20260728114408000__Add_providing_filter_indexes extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_output_parsers_payload_id
          ON output_parsers(output_parser_payload_id)
          """);

      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_contract_output_elements_parser_type
          ON contract_output_elements(
            contract_output_element_output_parser_id,
            contract_output_element_type
          )
          """);

      if (isTrigramExtensionAvailable(statement)) {
        statement.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_trgm_injectors_contracts_content_payloadless
            ON injectors_contracts
            USING gin (lower(injector_contract_content) gin_trgm_ops)
            WHERE injector_contract_payload IS NULL
            """);
      } else {
        log.warn(
            "pg_trgm extension is not available; skipping "
                + "idx_trgm_injectors_contracts_content_payloadless creation.");
      }
    }
  }

  private boolean isTrigramExtensionAvailable(Statement statement) {
    try {
      try (ResultSet rs =
          statement.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm'")) {
        if (rs.next()) {
          return true;
        }
      }

      statement.execute("SAVEPOINT before_trgm_extension");
      try {
        statement.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        statement.execute("RELEASE SAVEPOINT before_trgm_extension");
        return true;
      } catch (SQLException e) {
        statement.execute("ROLLBACK TO SAVEPOINT before_trgm_extension");
        log.warn("Unable to create pg_trgm extension: {}", e.getMessage());
        return false;
      }
    } catch (SQLException e) {
      log.warn("Unable to check pg_trgm extension availability: {}", e.getMessage());
      return false;
    }
  }
}
