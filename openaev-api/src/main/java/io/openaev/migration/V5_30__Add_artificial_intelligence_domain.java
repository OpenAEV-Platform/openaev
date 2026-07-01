package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Backfills the "Artificial Intelligence" security domain for every existing tenant, so AI
 * adversarial actions surface under the AI tab in the Threat Arsenal and in the "Performance by
 * Security Domain" dashboard widget. New tenants receive it from {@code
 * PresetDomain.getDomainsForTenant}. Idempotent: only inserts where the tenant does not already
 * have the domain.
 */
@Component
public class V5_30__Add_artificial_intelligence_domain extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id, domain_created_at, domain_updated_at) "
              + "SELECT gen_random_uuid(), 'Artificial Intelligence', '#7C4DFF', t.tenant_id, now(), now() "
              + "FROM (SELECT DISTINCT tenant_id FROM domains) t "
              + "WHERE NOT EXISTS ("
              + "  SELECT 1 FROM domains d "
              + "  WHERE d.tenant_id = t.tenant_id AND d.domain_name = 'Artificial Intelligence');");
    }
  }
}
