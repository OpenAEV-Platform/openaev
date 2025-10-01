package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_57__Group_organizations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    // Create groups_organizations
    select.execute(
        """
                CREATE TABLE groups_organizations (
                    group_id varchar(255) not null constraint group_id_fk references groups on delete cascade,
                    organization_id varchar(255) not null constraint organization_id_fk references organizations on delete cascade,
                    constraint groups_organizations_pkey primary key (group_id, organization_id)
                );
                CREATE INDEX idx_groups_organizations_group on groups_organizations (group_id);
                CREATE INDEX idx_groups_organizations_organization on groups_organizations (organization_id);
                """);
  }
}
