package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_51__Challenges_documents extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add association table between challenge and tag
    select.execute(
        """
                CREATE TABLE challenges_documents (
                    challenge_id varchar(255) not null constraint challenge_id_fk references challenges,
                    document_id varchar(255) not null constraint document_id_fk references documents,
                    constraint challenges_documents_pkey primary key (challenge_id, document_id)
                );
                CREATE INDEX idx_challenge_documents_challenge on challenges_documents (challenge_id);
                CREATE INDEX idx_challenge_documents_document on challenges_documents (document_id);
                """);
  }
}
