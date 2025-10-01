package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V2_68__Assets_Asset_Groups extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    // Add extension
    select.execute(
        """
        CREATE EXTENSION IF NOT EXISTS hstore;
        """);
    // Create table asset
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS assets (
            asset_id varchar(255) not null constraint assets_pkey primary key,
            asset_type varchar(255) not null,
            asset_sources hstore,
            asset_blobs hstore,
            asset_name varchar(255) not null,
            asset_description text,
            asset_last_seen timestamp,
            asset_created_at timestamp not null default now(),
            asset_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_assets on assets (asset_id);
        """);
    // Add column for endpoint type
    select.execute(
        """
        ALTER TABLE assets ADD COLUMN endpoint_ips text[];
        ALTER TABLE assets ADD COLUMN endpoint_hostname varchar(255);
        ALTER TABLE assets ADD COLUMN endpoint_platform varchar(255) not null;
        ALTER TABLE assets ADD COLUMN endpoint_mac_addresses text[];
        """);
    // Add association table between asset and tag
    select.execute(
        """
        CREATE TABLE assets_tags (
          asset_id varchar(255) not null constraint asset_id_fk references assets,
          tag_id varchar(255) not null constraint tag_id_fk references tags,
          constraint assets_tags_pkey primary key (asset_id, tag_id)
        );
        CREATE INDEX idx_assets_tags_asset on assets_tags (asset_id);
        CREATE INDEX idx_assets_tags_tag on assets_tags (tag_id);
        """);
    // Create table asset groups
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS asset_groups (
            asset_group_id varchar(255) not null constraint asset_groups_pkey primary key,
            asset_group_name varchar(255) not null,
            asset_group_description text,
            asset_group_created_at timestamp not null default now(),
            asset_group_updated_at timestamp not null default now()
        );
        CREATE INDEX IF NOT EXISTS idx_asset_groups on asset_groups (asset_group_id);
        """);
    // Add association table between asset group and tag
    select.execute(
        """
        CREATE TABLE asset_groups_tags (
          asset_group_id varchar(255) not null constraint asset_group_id_fk references asset_groups on delete cascade,
          tag_id varchar(255) not null constraint tag_id_fk references tags on delete cascade,
          constraint asset_groups_tags_pkey primary key (asset_group_id, tag_id)
        );
        CREATE INDEX idx_asset_groups_tags_asset_group on asset_groups_tags (asset_group_id);
        CREATE INDEX idx_asset_groups_tags_tag on asset_groups_tags (tag_id);
        """);
    // Add association table between asset and asset groups
    select.execute(
        """
        CREATE TABLE IF NOT EXISTS asset_groups_assets (
            asset_group_id varchar(255) not null constraint asset_group_id_fk references asset_groups on delete cascade,
            asset_id varchar(255) not null constraint asset_id_fk references assets on delete cascade,
            constraint asset_groups_assets_pkey primary key (asset_group_id, asset_id)
        );
        CREATE INDEX IF NOT EXISTS idx_asset_groups_assets_asset_group on asset_groups_assets (asset_group_id);
        CREATE INDEX IF NOT EXISTS idx_asset_groups_assets_asset on asset_groups_assets (asset_id);
        """);
  }
}
