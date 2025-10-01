package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_5__Organization_tags extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Add association table between organization and tag
    select.execute(
        """
                CREATE TABLE organizations_tags (
                    organization_id varchar(255) not null constraint organization_id_fk references organizations,
                    tag_id varchar(255) not null constraint tag_id_fk references tags,
                    constraint organizations_tags_pkey primary key (organization_id, tag_id)
                );
                CREATE INDEX idx_organization_tags_organization on organizations_tags (organization_id);
                CREATE INDEX idx_organization_tags_tag on organizations_tags (tag_id);
                """);
    // created_at / updated_at
    select.execute(
        "ALTER TABLE organizations ADD organization_created_at timestamp not null default now();");
    select.execute(
        "ALTER TABLE organizations ADD organization_updated_at timestamp not null default now();");
  }
}
