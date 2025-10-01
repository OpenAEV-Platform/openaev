package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_13__Payloads_Attack_Patterns extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Create relations between contracts and attack_patterns
    select.execute(
        """
               CREATE TABLE payloads_attack_patterns (
                   attack_pattern_id varchar(255) not null
                       constraint attack_pattern_id_fk
                           references attack_patterns
                           on delete cascade,
                   payload_id varchar(255) not null
                       constraint payloads_id_fk
                           references payloads
                           on delete cascade,
                   primary key (attack_pattern_id, payload_id)
               );
               CREATE INDEX idx_payloads_attack_patterns_pattern on payloads_attack_patterns (attack_pattern_id);
               CREATE INDEX idx_payloads_attack_patterns_contract on payloads_attack_patterns (payload_id);
            """);
  }
}
