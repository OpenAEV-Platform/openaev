package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_48__Media_upgrade extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Article table upgrade
    select.execute("ALTER TABLE articles DROP column article_header;");
    select.execute("ALTER TABLE articles DROP column article_footer;");
    select.execute("ALTER TABLE articles ADD article_author text;");
    select.execute("ALTER TABLE articles ADD article_shares int;");
    select.execute("ALTER TABLE articles ADD article_likes int;");
    select.execute("ALTER TABLE articles ADD article_comments int;");
    // Create article_documents
    select.execute(
        """
                CREATE TABLE articles_documents (
                    article_id varchar(255) not null constraint article_id_fk references articles on delete cascade,
                    document_id varchar(255) not null constraint document_id_fk references documents on delete cascade,
                    constraint articles_documents_pkey primary key (article_id, document_id)
                );
                CREATE INDEX idx_articles_documents_article on articles_documents (article_id);
                CREATE INDEX idx_articles_documents_document on articles_documents (document_id);
                """);
  }
}
