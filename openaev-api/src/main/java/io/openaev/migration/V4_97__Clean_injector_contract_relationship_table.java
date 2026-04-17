package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_97__Clean_injector_contract_relationship_table extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- TAGS --
      // Add relation table injector_contract_tags
      statement.execute(
          """
                      CREATE TABLE IF NOT EXISTS injector_contract_tags (
                        injector_contract_id VARCHAR(255) NOT NULL,
                        tag_id VARCHAR(255) NOT NULL,
                        tenant_id VARCHAR(255) NOT NULL,
                        PRIMARY KEY (injector_contract_id, tag_id, tenant_id),
                        CONSTRAINT injector_contract_id_fk
                            FOREIGN KEY (injector_contract_id, tenant_id)
                                REFERENCES injectors_contracts(injector_contract_id, tenant_id)
                                ON DELETE CASCADE,
                        CONSTRAINT tag_id_fk
                            FOREIGN KEY (tag_id)
                                REFERENCES tags(tag_id)
                                ON DELETE CASCADE
                      );
                  """);

      // Retrieve all tags on a specific payload and apply to the injector contract
      statement.execute(
          """
                      INSERT INTO injector_contract_tags (injector_contract_id, tag_id, tenant_id)
                      SELECT DISTINCT ic.injector_contract_id, pt.tag_id, ic.tenant_id
                      FROM injectors_contracts ic
                      JOIN payloads p ON ic.injector_contract_payload = p.payload_id
                      JOIN payloads_tags pt ON p.payload_id = pt.payload_id
                      WHERE ic.injector_contract_id IS NOT NULL;
                  """);

      //  Drop payloads_tags as it's no longer needed
      statement.execute("DROP TABLE IF EXISTS payloads_tags;");

      // -- DOMAINS --
      // Retrieve all domains on a specific payload and apply to the injector contract
      statement.execute(
          """
                      INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id, tenant_id)
                      SELECT DISTINCT ic.injector_contract_id, pd.domain_id, ic.tenant_id
                      FROM injectors_contracts ic
                      JOIN payloads p ON ic.injector_contract_payload = p.payload_id
                      JOIN payloads_domains pd ON p.payload_id = pd.payload_id
                      WHERE ic.injector_contract_id IS NOT NULL;
                  """);

      // after that if there is at least one domain on the injector_contract i need to
      //       delete the one call to classify
      statement.execute(
          """
                      DELETE FROM injectors_contracts_domains
                      WHERE domain_id = (SELECT domain_id FROM domains WHERE domain_name = 'To classify')
                      AND injector_contract_id IN (
                          SELECT injector_contract_id
                          FROM injectors_contracts_domains
                          GROUP BY injector_contract_id
                          HAVING COUNT(domain_id) > 1
                      );
                  """);

      // Delete payloads_domains as it's no longer needed
      statement.execute("DROP TABLE IF EXISTS payloads_domains;");

      // -- ATTACK PATTERNS
      // Drop the payloads_attack_patterns table as it's duplicate from
      // injectors_contracts_attack_patterns
      statement.execute("DROP TABLE IF EXISTS payloads_attack_patterns;");

      // -- Add a unit constraint on injector_contract_payload
      statement.execute(
          """
                ALTER TABLE injectors_contracts
                ADD CONSTRAINT unique_injector_contract_payload UNIQUE (injector_contract_payload);
            """);
    }
  }
}
