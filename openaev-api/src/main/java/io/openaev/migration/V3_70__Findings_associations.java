package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_70__Findings_associations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
        CREATE TABLE findings_assets (
            finding_id varchar(255) not null
                constraint finding_id_fk
                    references findings
                    on delete cascade,
            asset_id varchar(255) not null
                constraint asset_id_fk
                    references assets
                    on delete cascade,
            primary key (finding_id, asset_id)
        );
        CREATE INDEX idx_fingings_assets_finding on findings_assets (finding_id);
        CREATE INDEX idx_fingings_assets_asset on findings_assets (asset_id);
    """);

    select.execute(
        """
            CREATE TABLE findings_teams (
                finding_id varchar(255) not null
                    constraint finding_id_fk
                        references findings
                        on delete cascade,
                team_id varchar(255) not null
                    constraint team_id_fk
                        references teams
                        on delete cascade,
                primary key (finding_id, team_id)
            );
            CREATE INDEX idx_fingings_teams_finding on findings_teams (finding_id);
            CREATE INDEX idx_fingings_teams_team on findings_teams (team_id);
        """);

    select.execute(
        """
            CREATE TABLE findings_users (
                finding_id varchar(255) not null
                    constraint finding_id_fk
                        references findings
                        on delete cascade,
                user_id varchar(255) not null
                    constraint user_id_fk
                        references users
                        on delete cascade,
                primary key (finding_id, user_id)
            );
            CREATE INDEX idx_fingings_users_finding on findings_users (finding_id);
            CREATE INDEX idx_fingings_users_user on findings_users (user_id);
        """);
  }
}
