package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_27__Document_refactor extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Document can be private or public
    select.execute("ALTER TABLE documents ADD document_public bool default true;");
    // Create inject_documents
    select.execute(
        """
                CREATE TABLE injects_documents (
                    inject_id varchar(255) not null constraint inject_id_fk references injects on delete cascade,
                    document_id varchar(255) not null constraint document_id_fk references documents on delete cascade,
                    document_attached bool default false,
                    constraint injects_documents_pkey primary key (inject_id, document_id)
                );
                CREATE INDEX idx_injects_documents_inject on injects_documents (inject_id);
                CREATE INDEX idx_injects_documents_document on injects_documents (document_id);
                """);
  }
}
