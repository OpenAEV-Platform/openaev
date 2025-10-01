package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_81__Contracts extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Cleanup injectors
    select.execute("ALTER TABLE injectors DROP column injector_contracts;");
    // Create injectors_contracts table
    select.execute(
        """
          CREATE TABLE injectors_contracts (
            injector_contract_id varchar(255) not null constraint injector_contract_pkey primary key,
            injector_contract_created_at timestamp not null default now(),
            injector_contract_updated_at timestamp not null default now(),
            injector_contract_labels hstore,
            injector_contract_manual bool,
            injector_contract_content text not null,
            injector_id varchar(255) not null
                constraint injector_id_fk
                    references injectors
                    on delete cascade
          );
          CREATE INDEX idx_injectors_contracts on injectors_contracts (injector_contract_id);
     """);
    // Create relations between contracts and attack_patterns
    select.execute(
        """
        CREATE TABLE injectors_contracts_attack_patterns (
            attack_pattern_id varchar(255) not null
                constraint attack_pattern_id_fk
                    references attack_patterns
                    on delete cascade,
            injector_contract_id varchar(255) not null
                constraint injectors_contracts_id_fk
                    references injectors_contracts
                    on delete cascade,
            primary key (attack_pattern_id, injector_contract_id)
        );
        CREATE INDEX idx_injectors_contracts_attack_patterns_pattern on injectors_contracts_attack_patterns (attack_pattern_id);
        CREATE INDEX idx_injectors_contracts_attack_patterns_contract on injectors_contracts_attack_patterns (injector_contract_id);
     """);
  }
}
