package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code AiTarget} asset subtype columns to the {@code assets} table (single-table
 * inheritance). An AI Target is the AI system under adversarial test (LLM endpoint or AI agent). No
 * discriminator change is needed: {@code asset_type} already exists and stores the {@code AiTarget}
 * value for these rows. Secrets are never stored - {@code ai_target_api_key_variable} only names
 * the injector configuration key that resolves the credential at execution time.
 */
@Component
public class V5_28__Add_ai_target_columns extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    // Each statement is executed individually: the PostgreSQL JDBC driver does not reliably run
    // several statements from a single execute() call across all configurations.
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_provider varchar(255);");
      statement.execute("ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_endpoint text;");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_model varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_modality varchar(255);");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_system_prompt text;");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_configuration jsonb;");
      statement.execute(
          "ALTER TABLE assets ADD COLUMN IF NOT EXISTS ai_target_api_key_variable varchar(255);");
    }
  }
}
