package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_56__TagRule extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
               CREATE TABLE tag_rules (
                   tag_rule_id varchar(255) not null,
                   tag_id varchar(255) not null
                       constraint tag_id_fk
                           references tags,
                   primary key (tag_rule_id)
               );
           CREATE INDEX idx_tag_id ON tag_rules (tag_id);
            """);

    select.execute(
        """
                   CREATE TABLE tag_rule_assets (
                       tag_rule_id varchar(255) not null
                            constraint tag_rule_id_fk
                               references tag_rules,
                       asset_id varchar(255) not null
                           constraint asset_id_fk
                               references assets
                                 on delete cascade,
                       primary key (tag_rule_id, asset_id)
                   );
                """);
  }
}
