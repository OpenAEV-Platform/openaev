package io.openaev.migration;

import io.openaev.rest.domain.enums.PresetDomain;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_53__Implement_Domains_notion extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {
      stmt.execute(
          """
              CREATE TABLE domains (
                  domain_id VARCHAR(255) NOT NULL CONSTRAINT domains_pkey PRIMARY KEY,
                  domain_name VARCHAR(255) NOT NULL UNIQUE,
                  domain_color VARCHAR(255) NOT NULL DEFAULT '#FFFFFF',
                  domain_created_at TIMESTAMPTZ DEFAULT now(),
                  domain_updated_at TIMESTAMPTZ DEFAULT now()
              );
          """);

      stmt.execute(
          """
                CREATE INDEX idx_domains_domain_name
                ON domains(domain_name);
              """);

      stmt.execute(
          """
                CREATE TABLE payloads_domains (
                    payload_id VARCHAR(255) NOT NULL,
                    domain_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (payload_id, domain_id),
                    CONSTRAINT fk_payloads_domains_domain FOREIGN KEY (domain_id) REFERENCES domains(domain_id) ON DELETE CASCADE,
                    CONSTRAINT fk_payloads_domains_payload FOREIGN KEY (payload_id) REFERENCES payloads(payload_id) ON DELETE CASCADE
                );
            """);

      stmt.execute("CREATE INDEX idx_payloads_domains_domain_id ON payloads_domains(domain_id);");
      stmt.execute("CREATE INDEX idx_payloads_domains_payload_id ON payloads_domains(payload_id);");

      stmt.execute(
          """
                CREATE TABLE injectors_contracts_domains (
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
          "CREATE INDEX idx_icd_injector_contract_id ON injectors_contracts_domains(injector_contract_id);");
      stmt.execute("CREATE INDEX idx_icd_domain_id ON injectors_contracts_domains(domain_id);");

      stmt.execute(
          "INSERT INTO domains (domain_id, domain_name, domain_color) VALUES "
              + "  (gen_random_uuid(), '"
              + PresetDomain.ENDPOINT.getName()
              + "', '"
              + PresetDomain.ENDPOINT.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.NETWORK.getName()
              + "', '"
              + PresetDomain.NETWORK.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.WEB_APP.getName()
              + "', '"
              + PresetDomain.WEB_APP.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.EMAIL_INFILTRATION.getName()
              + "', '"
              + PresetDomain.EMAIL_INFILTRATION.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.DATA_EXFILTRATION.getName()
              + "', '"
              + PresetDomain.DATA_EXFILTRATION.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.URL_FILTERING.getName()
              + "', '"
              + PresetDomain.URL_FILTERING.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.CLOUD.getName()
              + "', '"
              + PresetDomain.CLOUD.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.TABLETOP.getName()
              + "', '"
              + PresetDomain.TABLETOP.getColor()
              + "'),"
              + "  (gen_random_uuid(), '"
              + PresetDomain.TOCLASSIFY.getName()
              + "', '"
              + PresetDomain.TOCLASSIFY.getColor()
              + "');");

      stmt.execute(
          "INSERT INTO payloads_domains (payload_id, domain_id) "
              + "SELECT p.payload_id, d.domain_id FROM payloads p "
              + "INNER JOIN domains d ON d.domain_name = '"
              + PresetDomain.TOCLASSIFY.getName()
              + "';");

      stmt.execute(
          "INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id) "
              + "SELECT ic.injector_contract_id, d.domain_id FROM injectors_contracts ic "
              + "INNER JOIN domains d ON d.domain_name = '"
              + PresetDomain.TOCLASSIFY.getName()
              + "' "
              + "WHERE ic.injector_contract_payload IS NULL;");
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
