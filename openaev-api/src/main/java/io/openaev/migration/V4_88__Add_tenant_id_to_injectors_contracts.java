package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_88__Add_tenant_id_to_injectors_contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      // 1. Add tenant_id column to injectors_contracts
      statement.execute(
          String.format(
              """
              ALTER TABLE injectors_contracts
                ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s';
              """,
              DEFAULT_TENANT_UUID));

      // 2. Backfill tenant_id from the parent injector
      statement.execute(
          """
          UPDATE injectors_contracts ic
          SET tenant_id = i.tenant_id
          FROM injectors i
          WHERE ic.injector_id = i.injector_id;
          """);

      // 3. Drop existing single-column PK and FK references

      // Drop FK from injects -> injectors_contracts
      statement.execute(
          """
          ALTER TABLE injects DROP CONSTRAINT IF EXISTS injector_contract_fk;
          """);

      // Drop FK from join tables -> injectors_contracts
      statement.execute(
          """
          ALTER TABLE injectors_contracts_attack_patterns
            DROP CONSTRAINT IF EXISTS injectors_contracts_id_fk;
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_domains
            DROP CONSTRAINT IF EXISTS injectors_contracts_domains_injector_contract_id_fk;
          """);
      // injectors_contracts_vulnerabilities has no explicit named FK constraint, drop by column
      statement.execute(
          """
          DO $$
          DECLARE fk_name TEXT;
          BEGIN
            SELECT tc.constraint_name INTO fk_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON tc.constraint_name = ccu.constraint_name
            WHERE tc.table_name = 'injectors_contracts_vulnerabilities'
              AND tc.constraint_type = 'FOREIGN KEY'
              AND ccu.column_name = 'injector_contract_id'
              AND ccu.table_name = 'injectors_contracts'
            LIMIT 1;
            IF fk_name IS NOT NULL THEN
              EXECUTE 'ALTER TABLE injectors_contracts_vulnerabilities DROP CONSTRAINT ' || fk_name;
            END IF;
          END $$;
          """);

      // Drop existing PK (CASCADE drops all dependent FKs automatically)
      statement.execute(
          """
          ALTER TABLE injectors_contracts DROP CONSTRAINT injector_contract_pkey CASCADE;
          """);

      // 4. Create composite PK
      statement.execute(
          """
          ALTER TABLE injectors_contracts
            ADD CONSTRAINT injector_contract_pkey
            PRIMARY KEY (injector_contract_id, tenant_id);
          """);

      // 5. Add FK from injectors_contracts.tenant_id -> tenants
      statement.execute(
          """
          ALTER TABLE injectors_contracts
            ADD CONSTRAINT fk_injectors_contracts_tenant_id
            FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE;
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_injectors_contracts_tenant_id
            ON injectors_contracts(tenant_id);
          """);

      // 6. Add tenant_id to join tables and recreate composite FKs

      // -- injectors_contracts_attack_patterns --
      statement.execute(
          String.format(
              """
              ALTER TABLE injectors_contracts_attack_patterns
                ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s';
              """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
          UPDATE injectors_contracts_attack_patterns jt
          SET tenant_id = ic.tenant_id
          FROM injectors_contracts ic
          WHERE jt.injector_contract_id = ic.injector_contract_id;
          """);
      // Drop old PK (attack_pattern_id, injector_contract_id) and recreate with tenant_id
      statement.execute(
          """
          ALTER TABLE injectors_contracts_attack_patterns
            DROP CONSTRAINT IF EXISTS injectors_contracts_attack_patterns_pkey;
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_attack_patterns
            ADD PRIMARY KEY (attack_pattern_id, injector_contract_id, tenant_id);
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_attack_patterns
            ADD CONSTRAINT fk_icap_injector_contract
            FOREIGN KEY (injector_contract_id, tenant_id)
            REFERENCES injectors_contracts(injector_contract_id, tenant_id)
            ON DELETE CASCADE;
          """);

      // -- injectors_contracts_domains --
      statement.execute(
          String.format(
              """
              ALTER TABLE injectors_contracts_domains
                ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s';
              """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
          UPDATE injectors_contracts_domains jt
          SET tenant_id = ic.tenant_id
          FROM injectors_contracts ic
          WHERE jt.injector_contract_id = ic.injector_contract_id;
          """);
      // Recreate PK with tenant_id
      statement.execute(
          """
          DO $$
          DECLARE pk_name TEXT;
          BEGIN
            SELECT constraint_name INTO pk_name
            FROM information_schema.table_constraints
            WHERE table_name = 'injectors_contracts_domains'
              AND constraint_type = 'PRIMARY KEY'
            LIMIT 1;
            IF pk_name IS NOT NULL THEN
              EXECUTE 'ALTER TABLE injectors_contracts_domains DROP CONSTRAINT ' || pk_name;
            END IF;
          END $$;
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_domains
            ADD PRIMARY KEY (injector_contract_id, domain_id, tenant_id);
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_domains
            ADD CONSTRAINT fk_icd_injector_contract
            FOREIGN KEY (injector_contract_id, tenant_id)
            REFERENCES injectors_contracts(injector_contract_id, tenant_id)
            ON DELETE CASCADE;
          """);

      // -- injectors_contracts_vulnerabilities --
      statement.execute(
          String.format(
              """
              ALTER TABLE injectors_contracts_vulnerabilities
                ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT '%s';
              """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
          UPDATE injectors_contracts_vulnerabilities jt
          SET tenant_id = ic.tenant_id
          FROM injectors_contracts ic
          WHERE jt.injector_contract_id = ic.injector_contract_id;
          """);
      // Recreate PK with tenant_id
      statement.execute(
          """
          DO $$
          DECLARE pk_name TEXT;
          BEGIN
            SELECT constraint_name INTO pk_name
            FROM information_schema.table_constraints
            WHERE table_name = 'injectors_contracts_vulnerabilities'
              AND constraint_type = 'PRIMARY KEY'
            LIMIT 1;
            IF pk_name IS NOT NULL THEN
              EXECUTE 'ALTER TABLE injectors_contracts_vulnerabilities DROP CONSTRAINT ' || pk_name;
            END IF;
          END $$;
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_vulnerabilities
            ADD PRIMARY KEY (injector_contract_id, vulnerability_id, tenant_id);
          """);
      statement.execute(
          """
          ALTER TABLE injectors_contracts_vulnerabilities
            ADD CONSTRAINT fk_icv_injector_contract
            FOREIGN KEY (injector_contract_id, tenant_id)
            REFERENCES injectors_contracts(injector_contract_id, tenant_id)
            ON DELETE CASCADE;
          """);

      // 7. Recreate FK from injects -> injectors_contracts (composite)
      // CASCADE: if the injector contract is deleted, the inject no longer makes sense
      statement.execute(
          """
          ALTER TABLE injects
            ADD CONSTRAINT injector_contract_fk
            FOREIGN KEY (inject_injector_contract, tenant_id)
            REFERENCES injectors_contracts(injector_contract_id, tenant_id)
            ON DELETE CASCADE;
          """);

      // 8. Add tenant_id to inject_importers and recreate FK
      statement.execute(
          """
          DO $$
          DECLARE fk_name TEXT;
          BEGIN
            SELECT tc.constraint_name INTO fk_name
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu
              ON tc.constraint_name = ccu.constraint_name
            WHERE tc.table_name = 'inject_importers'
              AND tc.constraint_type = 'FOREIGN KEY'
              AND ccu.column_name = 'importer_injector_contract_id'
            LIMIT 1;
            IF fk_name IS NOT NULL THEN
              EXECUTE 'ALTER TABLE inject_importers DROP CONSTRAINT ' || fk_name;
            END IF;
          END $$;
          """);
      statement.execute(
          String.format(
              """
              ALTER TABLE inject_importers
                ADD COLUMN importer_tenant_id VARCHAR(255) NOT NULL DEFAULT '%s';
              """,
              DEFAULT_TENANT_UUID));
      statement.execute(
          """
          UPDATE inject_importers ii
          SET importer_tenant_id = ic.tenant_id
          FROM injectors_contracts ic
          WHERE ii.importer_injector_contract_id = ic.injector_contract_id;
          """);
      statement.execute(
          """
          ALTER TABLE inject_importers
            ADD CONSTRAINT fk_inject_importers_injector_contract
            FOREIGN KEY (importer_injector_contract_id, importer_tenant_id)
            REFERENCES injectors_contracts(injector_contract_id, tenant_id)
            ON DELETE CASCADE;
          """);
    }
  }
}
