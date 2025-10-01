package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_38__Challenge_flags extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Challenge - remove simple attribute flag
    // select.execute("ALTER TABLE challenges DROP COLUMN challenge_flag;");
    // Challenges flags
    select.execute(
        """
                CREATE TABLE challenges_flags (
                    flag_id varchar(255) not null constraint challenges_flags_pkey primary key,
                    flag_created_at timestamp not null default now(),
                    flag_updated_at timestamp not null default now(),
                    flag_type varchar(255) not null,
                    flag_value text not null,
                    flag_challenge varchar(255) not null
                        constraint fk_flag_challenge references challenges on delete cascade
                );
                CREATE INDEX idx_flag_challenge on challenges_flags (flag_challenge);
                CREATE INDEX idx_challenges_flags on challenges_flags (flag_id);
                """);
  }
}
