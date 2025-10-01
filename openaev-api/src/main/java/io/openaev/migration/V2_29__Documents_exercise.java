package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_29__Documents_exercise extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Document can be private or public
    //noinspection SqlResolve
    select.execute("ALTER TABLE documents DROP COLUMN document_public;");
    select.execute("ALTER TABLE documents DROP COLUMN document_path;");
    // Create inject_documents
    select.execute(
        """
                CREATE TABLE exercises_documents (
                    exercise_id varchar(255) not null constraint exercise_id_fk references exercises on delete cascade,
                    document_id varchar(255) not null constraint document_id_fk references documents on delete cascade,
                    constraint exercises_documents_pkey primary key (exercise_id, document_id)
                );
                CREATE INDEX idx_exercises_documents_inject on exercises_documents (exercise_id);
                CREATE INDEX idx_exercises_documents_document on exercises_documents (document_id);
                """);
  }
}
