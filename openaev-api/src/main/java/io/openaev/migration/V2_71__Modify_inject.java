package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_71__Modify_inject extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add table link asset to inject
    select.execute(
        """
        ALTER TABLE injects ADD COLUMN inject_assets varchar(256);
        ALTER TABLE injects ADD COLUMN injects_asset_groups varchar(256);
        """);
    // Add association table between asset and inject
    select.execute(
        """
        CREATE TABLE injects_assets (
          inject_id varchar(255) not null constraint inject_id_fk references injects on delete cascade,
          asset_id varchar(255) not null constraint asset_id_fk references assets on delete cascade,
          constraint injects_assets_pkey primary key (inject_id, asset_id)
        );
        CREATE INDEX idx_injects_assets_inject on injects_assets (inject_id);
        CREATE INDEX idx_injects_assets_asset on injects_assets (asset_id);
        """);
    // Add association table between asset group and inject
    select.execute(
        """
        CREATE TABLE injects_asset_groups (
          inject_id varchar(255) not null constraint inject_id_fk references injects on delete cascade,
          asset_group_id varchar(255) not null constraint asset_group_id_fk references asset_groups on delete cascade,
          constraint injects_asset_groups_pkey primary key (inject_id, asset_group_id)
        );
        CREATE INDEX idx_injects_asset_groups_inject on injects_asset_groups (inject_id);
        CREATE INDEX idx_injects_asset_groups_asset_groups on injects_asset_groups (asset_group_id);
        """);
    // Add asset to inject expectation
    select.execute(
        """
        ALTER TABLE injects_expectations ADD COLUMN inject_expectation_group bool default false;
        ALTER TABLE injects_expectations ADD COLUMN asset_id varchar(256) constraint fk_asset references assets on delete cascade;
        ALTER TABLE injects_expectations ADD COLUMN asset_group_id varchar(256) constraint fk_asset_group references asset_groups on delete cascade;
        ALTER TABLE injects_expectations ALTER COLUMN team_id DROP NOT NULL;
        """);
  }
}
