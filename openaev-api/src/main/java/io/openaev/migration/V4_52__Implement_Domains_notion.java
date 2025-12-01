package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_52__Implement_Domains_notion extends BaseJavaMigration {
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
              + "  (gen_random_uuid(), 'Endpoint', '#389CFF'),"
              + "  (gen_random_uuid(), 'Network', '#009933'),"
              + "  (gen_random_uuid(), 'Web App', '#FF9933'),"
              + "  (gen_random_uuid(), 'E-mail Infiltration', '#FF6666'),"
              + "  (gen_random_uuid(), 'Data Exfiltration', '#9933CC'),"
              + "  (gen_random_uuid(), 'URL Filtering', '#66CCFF'),"
              + "  (gen_random_uuid(), 'Cloud', '#9999CC'),"
              + "  (gen_random_uuid(), 'Table-Top', '#FFCC33'),"
              + "  (gen_random_uuid(), 'To classify', '#FFFFFF');");

      stmt.execute(
          """
insert into payloads_domains (payload_id, domain_id)
select p.payload_id, d.domain_id from payloads p
inner join domains d on d.domain_name = 'To classify';""");

      stmt.execute(
          """
insert into injectors_contracts_domains (injector_contract_id, domain_id)
select ic.injector_contract_id, d.domain_id from injectors_contracts ic
inner join domains d on d.domain_name = 'To classify'
where ic.injector_contract_payload is null;""");
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
