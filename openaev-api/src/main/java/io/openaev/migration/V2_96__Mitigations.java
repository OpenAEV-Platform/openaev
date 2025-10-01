package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_96__Mitigations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Mitigations
    select.execute(
        """
          CREATE TABLE mitigations (
            mitigation_id varchar(255) not null constraint mitigations_pkey primary key,
            mitigation_created_at timestamp not null default now(),
            mitigation_updated_at timestamp not null default now(),
            mitigation_name varchar(255) not null,
            mitigation_description text,
            mitigation_external_id varchar(255) not null,
            mitigation_stix_id  varchar(255) not null,
            mitigation_log_sources text[],
            mitigation_threat_hunting_techniques text
          );
          CREATE INDEX idx_mitigations on mitigations (mitigation_id);
          CREATE UNIQUE INDEX mitigations_unique on mitigations (mitigation_external_id);
     """);
    select.execute(
        """
        CREATE TABLE mitigations_attack_patterns (
            mitigation_id varchar(255) not null
                constraint mitigation_id_fk
                    references mitigations
                    on delete cascade,
            attack_pattern_id varchar(255) not null
                constraint attack_pattern_id_fk
                    references attack_patterns
                    on delete cascade,
            primary key (mitigation_id, attack_pattern_id)
        );
        CREATE INDEX idx_mitigations_attack_patterns_mitigation on mitigations_attack_patterns (mitigation_id);
        CREATE INDEX idx_mitigations_attack_patterns_attack_pattern on mitigations_attack_patterns (attack_pattern_id);
    """);
  }
}
