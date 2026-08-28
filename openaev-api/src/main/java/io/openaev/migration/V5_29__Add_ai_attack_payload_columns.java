package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code AiAttack} payload subtype columns to the {@code payloads} table (single-table
 * inheritance). An AI Attack is an adversarial action executed against an {@code AiTarget} by the
 * {@code ai-redteam} injector (prompt injection, jailbreak, data exfiltration, tool abuse, ...).
 * The discriminator column {@code payload_type} already exists and stores the {@code AiAttack}
 * value.
 */
@Component
public class V5_29__Add_ai_attack_payload_columns extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    // Each statement is executed individually: the PostgreSQL JDBC driver does not reliably run
    // several statements from a single execute() call across all configurations.
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_engine varchar(255);");
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_category varchar(255);");
      statement.execute("ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_content text;");
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_multi_turn jsonb;");
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_converters text[];");
      statement.execute(
          "ALTER TABLE payloads ADD COLUMN IF NOT EXISTS ai_attack_success_detector jsonb;");
    }
  }
}
