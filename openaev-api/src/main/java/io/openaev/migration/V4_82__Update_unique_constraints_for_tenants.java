package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_82__Update_unique_constraints_for_tenants extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Tags
      statement.execute(
          """
                        DROP INDEX IF EXISTS tag_name_unique;
                        """);
      statement.execute(
          """
                        ALTER TABLE tags
                        ADD CONSTRAINT tag_name_tenant_unique UNIQUE (tag_name, tenant_id);
                        """);
      // Attack patterns
      statement.execute(
          """
                            DROP INDEX IF EXISTS idx_attack_patterns_external_id;
                            """);
      statement.execute(
          """
                            ALTER TABLE attack_patterns
                            ADD CONSTRAINT attack_patterns_external_id_tenant_unique UNIQUE (attack_pattern_external_id, tenant_id);
                            """);
      statement.execute(
          """
                            DROP INDEX IF EXISTS idx_attack_patterns_stix_id;
                            """);
      statement.execute(
          """
                            ALTER TABLE attack_patterns
                            ADD CONSTRAINT attack_patterns_stix_id_tenant_unique UNIQUE (attack_pattern_stix_id, tenant_id);
                            """);
      // Kill chain phases
      statement.execute(
          """
                            DROP INDEX IF EXISTS idx_kill_chain_phases_stix_id;
                            """);
      statement.execute(
          """
                            ALTER TABLE kill_chain_phases
                            ADD CONSTRAINT kill_chain_phases_stix_id_tenant_unique UNIQUE (phase_stix_id, tenant_id);
                            """);
      statement.execute(
          """
                            DROP INDEX IF EXISTS kill_chain_phases_unique;
                            """);
      statement.execute(
          """
                            ALTER TABLE kill_chain_phases
                            ADD CONSTRAINT kill_chain_phases_tenant_unique UNIQUE (phase_name, phase_kill_chain_name, tenant_id);
                            """);
      // Assets
      statement.execute(
          """
                            DROP INDEX IF EXISTS assets_unique;
                            """);
      statement.execute(
          """
                            ALTER TABLE assets
                            ADD CONSTRAINT assets_tenant_unique UNIQUE (asset_external_reference, tenant_id);
                            """);
      // Payloads
      statement.execute(
          """
                            DROP INDEX IF EXISTS payloads_unique;
                            """);
      statement.execute(
          """
                            ALTER TABLE payloads
                            ADD CONSTRAINT payloads_tenant_unique UNIQUE (payload_external_id, tenant_id);
                            """);
    }
  }
}
