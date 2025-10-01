package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_10__Update_contrainst_documents extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement stmt = context.getConnection().createStatement()) {

      // Drop and recreate foreign key in scenarios_documents
      stmt.execute(
          """
                ALTER TABLE scenarios_documents
                DROP CONSTRAINT document_id_fk,
                ADD CONSTRAINT document_id_fk
                FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE
            """);

      // Drop and recreate foreign key in challenges_documents
      stmt.execute(
          """
                ALTER TABLE challenges_documents
                DROP CONSTRAINT document_id_fk,
                ADD CONSTRAINT document_id_fk
                FOREIGN KEY (document_id) REFERENCES documents(document_id) ON DELETE CASCADE
            """);
    }
  }
}
