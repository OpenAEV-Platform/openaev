package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_58__TagRule_Update extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                   CREATE TABLE tag_rule_asset_groups (
                       tag_rule_id varchar(255) not null
                            constraint tag_rule_id_fk
                               references tag_rules,
                       asset_group_id varchar(255) not null
                           constraint asset_group_id_fk
                               references asset_groups
                                 on delete cascade,
                       primary key (tag_rule_id, asset_group_id)
                   );
                """);

    select.execute(
        """
                   DROP TABLE IF EXISTS tag_rule_assets
               """);
  }
}
