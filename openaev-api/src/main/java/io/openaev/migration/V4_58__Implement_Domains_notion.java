package io.openaev.migration;

import io.openaev.rest.domain.enums.PresetDomain;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_58__Implement_Domains_notion extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
              CREATE TABLE IF NOT EXISTS domains (
                  domain_id VARCHAR(255) NOT NULL CONSTRAINT domains_pkey PRIMARY KEY,
                  domain_name VARCHAR(255) NOT NULL UNIQUE,
                  domain_color VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
                  domain_created_at TIMESTAMPTZ DEFAULT now(),
                  domain_updated_at TIMESTAMPTZ DEFAULT now()
              );
          """);

      stmt.execute(
          """
                CREATE INDEX IF NOT EXISTS idx_domains_domain_name
                ON domains(domain_name);
              """);

      stmt.execute(
          """
                CREATE TABLE IF NOT EXISTS payloads_domains (
                    payload_id VARCHAR(255) NOT NULL,
                    domain_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (payload_id, domain_id),
                    CONSTRAINT fk_payloads_domains_domain FOREIGN KEY (domain_id) REFERENCES domains(domain_id) ON DELETE CASCADE,
                    CONSTRAINT fk_payloads_domains_payload FOREIGN KEY (payload_id) REFERENCES payloads(payload_id) ON DELETE CASCADE
                );
            """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_payloads_domains_domain_id ON payloads_domains(domain_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_payloads_domains_payload_id ON payloads_domains(payload_id);");

      stmt.execute(
          """
                CREATE TABLE IF NOT EXISTS injectors_contracts_domains (
                    injector_contract_id VARCHAR(255) NOT NULL,
                    domain_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (injector_contract_id, domain_id),

                    CONSTRAINT fk_icd_injector_contract
                        FOREIGN KEY (injector_contract_id)
                        REFERENCES injectors_contracts(injector_contract_id)
                        ON DELETE CASCADE,

                    CONSTRAINT fk_icd_domain
                        FOREIGN KEY (domain_id)
                        REFERENCES domains(domain_id)
                        ON DELETE CASCADE
                );
            """);

      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_icd_injector_contract_id ON injectors_contracts_domains(injector_contract_id);");
      stmt.execute(
          "CREATE INDEX IF NOT EXISTS idx_icd_domain_id ON injectors_contracts_domains(domain_id);");

      stmt.execute(
          "INSERT INTO domains (domain_id, domain_name, domain_color) VALUES "
              + "  (gen_random_uuid(), '"
              + PresetDomain.getEndpoint().getName()
              + "', '"
              + PresetDomain.getEndpoint().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getNetwork().getName()
              + "', '"
              + PresetDomain.getNetwork().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getWebApp().getName()
              + "', '"
              + PresetDomain.getWebApp().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getEmailInfiltration().getName()
              + "', '"
              + PresetDomain.getEmailInfiltration().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getDataExfiltration().getName()
              + "', '"
              + PresetDomain.getDataExfiltration().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getUrlFiltering().getName()
              + "', '"
              + PresetDomain.getUrlFiltering().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getCloud().getName()
              + "', '"
              + PresetDomain.getCloud().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getTabletop().getName()
              + "', '"
              + PresetDomain.getTabletop().getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.getToClassify().getName()
              + "', '"
              + PresetDomain.getToClassify().getColor()
              + "') ON CONFLICT (domain_name) DO NOTHING;");

      stmt.execute(
          """
            WITH unknown_payloads AS (
              SELECT p.payload_id
              FROM payloads p
                LEFT JOIN payloads_domains pd ON p.payload_id = pd.payload_id
              WHERE pd.payload_id IS NULL
            )
            INSERT INTO payloads_domains (payload_id, domain_id)
                SELECT p.payload_id, d.domain_id
                FROM unknown_payloads p
                  INNER JOIN domains d ON d.domain_name = '%s'
                ON CONFLICT (payload_id, domain_id) DO NOTHING;
            """
              .formatted(PresetDomain.getToClassify().getName()));

      stmt.execute(
          """
            WITH unknown_contracts AS (
              SELECT ic.injector_contract_id, ic.injector_contract_payload
              FROM injectors_contracts ic
                LEFT JOIN injectors_contracts_domains icd ON ic.injector_contract_id = icd.injector_contract_id
              WHERE icd.injector_contract_id IS NULL
            )
            INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id)
                SELECT ic.injector_contract_id, d.domain_id
                FROM unknown_contracts ic
                  INNER JOIN domains d ON d.domain_name = '%s'
                WHERE ic.injector_contract_payload IS NULL
                ON CONFLICT (injector_contract_id, domain_id) DO NOTHING;
            """
              .formatted(PresetDomain.getToClassify().getName()));
    }
  }
}

// Rollback script

// DROP TABLE IF EXISTS domains;
// DROP INDEX IF EXISTS idx_payloads_domains_domain_id;
// DROP INDEX IF EXISTS idx_payloads_domains_payload_id;
// DROP TABLE IF EXISTS payloads_domains;
// DROP INDEX IF EXISTS idx_injectors_contracts_domains_domain_id;
// DROP INDEX IF EXISTS idx_injectors_contracts_domains_injector_contract_id;
// DROP TABLE IF EXISTS injectors_contracts_domains;
