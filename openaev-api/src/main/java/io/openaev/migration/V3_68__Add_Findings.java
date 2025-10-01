package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_68__Add_Findings extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
              CREATE TABLE findings (
                  finding_id varchar(255) NOT NULL CONSTRAINT findings_pkey PRIMARY KEY,
                  finding_field VARCHAR(255) NOT NULL,
                  finding_type VARCHAR(255) NOT NULL,
                  finding_value TEXT NOT NULL,
                  finding_labels TEXT[],
                  finding_inject_id VARCHAR(255) NOT NULL CONSTRAINT finding_inject_id_fk REFERENCES injects ON DELETE CASCADE,
                  finding_created_at TIMESTAMP DEFAULT now(),
                  finding_updated_at TIMESTAMP DEFAULT now()
              );
              """);
    }
  }
}
