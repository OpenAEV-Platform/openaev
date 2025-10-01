package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_66__Migrate_agents_to_same_endpoint extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Statement select = context.getConnection().createStatement();
    select.execute(
        """
                    -- update hostnames to have lowercase for every executors
                    UPDATE assets SET endpoint_hostname = lower(endpoint_hostname) WHERE asset_type='Endpoint';
                    -- update mac addresses to sanitize them
                    UPDATE ASSETS as ASS
                    SET endpoint_mac_addresses = ARRAY
                                                 (
                            SELECT DISTINCT LOWER(REGEXP_REPLACE(mac, '[^a-zA-Z0-9]', '', 'g'))
                            FROM unnest(endpoint_mac_addresses) AS mac
                                                 )
                    WHERE array_length(endpoint_mac_addresses, 1) > 0;

                    UPDATE ASSETS as ASS
                    SET endpoint_mac_addresses = ARRAY(
                            SELECT DISTINCT *
                            FROM unnest(endpoint_mac_addresses) AS mac
                            WHERE mac IS NOT NULL
                              AND mac NOT IN (
                                              'ffffffffffff', '000000000000', '0180c2000000'
                                ))
                    WHERE array_length(endpoint_mac_addresses, 1) > 0;

                    UPDATE ASSETS as ASS
                    SET endpoint_ips = ARRAY(
                            SELECT DISTINCT *
                            FROM unnest(endpoint_ips) AS ip
                            WHERE ip IS NOT NULL
                              AND ip NOT IN (
                                              '127.0.0.1', '::1', '169.254.0.0'
                                ))
                    WHERE array_length(endpoint_ips, 1) > 0;

                    -- update agent table to add cleared at column from asset table
                    ALTER TABLE agents ADD column agent_cleared_at timestamp default now();
                    UPDATE agents ag set agent_cleared_at = a.asset_cleared_at FROM (SELECT asset_id, asset_cleared_at FROM assets) AS a WHERE ag.agent_asset=a.asset_id;
                    ALTER TABLE assets DROP column asset_cleared_at;
                    -- create temp asset table to group the identical endpoints (same mac address)
                    CREATE TABLE temp_assets
                    AS SELECT endpoint_mac_addresses,
                        cast(array_agg(asset_id) AS VARCHAR) AS array_asset_id,
                        min(asset_id)                        AS uniq_asset_id,
                        string_agg(asset_description, '; ')  AS agg_description
                    FROM assets
                    WHERE assets.endpoint_mac_addresses &&
                      (select array_agg(maca)
                       from (select maca, count(*)
                             from (select unnest(endpoint_mac_addresses) as maca
                                   from assets
                                   WHERE asset_type = 'Endpoint') t
                             group by maca
                             HAVING count(*) > 1) as mac)
                    GROUP BY assets.endpoint_mac_addresses
                    HAVING count(asset_id) > 1;
                    -- update the assets table with the aggregated description for the corresponding unique endpoint
                    UPDATE assets asset
                    SET asset_description = ta.agg_description
                    FROM temp_assets ta WHERE asset.asset_id = ta.uniq_asset_id;
                    -- update the agents to match with an identical endpoint if it is possible
                    UPDATE agents a SET agent_asset=ta.uniq_asset_id
                    FROM (SELECT array_asset_id, uniq_asset_id FROM temp_assets) AS ta
                    WHERE ta.array_asset_id LIKE concat('%',a.agent_asset,'%');

                    -- update the relation injects_assets
                    ALTER TABLE injects_assets DROP CONSTRAINT injects_assets_pkey;

                    UPDATE injects_assets injects_asset SET asset_id=ta.uniq_asset_id
                    FROM (SELECT array_asset_id, uniq_asset_id FROM temp_assets) AS ta
                    WHERE ta.array_asset_id LIKE concat('%',injects_asset.asset_id,'%');

                    CREATE TABLE injects_assets_temp AS SELECT DISTINCT * FROM injects_assets;
                    DROP TABLE injects_assets;
                    ALTER TABLE injects_assets_temp RENAME TO injects_assets;

                    ALTER TABLE injects_assets ADD CONSTRAINT injects_assets_pkey PRIMARY KEY (inject_id, asset_id);
                    ALTER TABLE injects_assets ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE;
                    ALTER TABLE injects_assets ADD CONSTRAINT inject_id_fk FOREIGN KEY (inject_id) REFERENCES injects(inject_id) ON DELETE CASCADE;
                    CREATE INDEX IF NOT EXISTS idx_injects_assets_inject on injects_assets (inject_id);
                    CREATE INDEX IF NOT EXISTS idx_injects_assets_asset on injects_assets (asset_id);

                    -- update the relation asset_groups_assets
                    ALTER TABLE asset_groups_assets DROP CONSTRAINT asset_groups_assets_pkey;

                    UPDATE asset_groups_assets asset_groups_asset SET asset_id=ta.uniq_asset_id
                    FROM (SELECT array_asset_id, uniq_asset_id FROM temp_assets) AS ta
                    WHERE ta.array_asset_id LIKE concat('%',asset_groups_asset.asset_id,'%');

                    CREATE TABLE asset_groups_assets_temp AS SELECT DISTINCT * FROM asset_groups_assets;
                    DROP TABLE asset_groups_assets;
                    ALTER TABLE asset_groups_assets_temp RENAME TO asset_groups_assets;

                    ALTER TABLE asset_groups_assets ADD CONSTRAINT asset_groups_assets_pkey PRIMARY KEY (asset_group_id, asset_id);
                    ALTER TABLE asset_groups_assets ADD CONSTRAINT asset_id_fk FOREIGN KEY (asset_id) REFERENCES assets(asset_id) ON DELETE CASCADE;
                    ALTER TABLE asset_groups_assets ADD CONSTRAINT asset_group_id_fk FOREIGN KEY (asset_group_id) REFERENCES asset_groups(asset_group_id) ON DELETE CASCADE;
                    CREATE INDEX IF NOT EXISTS idx_asset_groups_assets_asset_group on asset_groups_assets (asset_group_id);
                    CREATE INDEX IF NOT EXISTS idx_asset_groups_assets_asset on asset_groups_assets (asset_id);

                    -- update the relation assets_tags
                    ALTER TABLE assets_tags DROP CONSTRAINT assets_tags_pkey;

                    UPDATE assets_tags assets_tag SET asset_id=ta.uniq_asset_id
                    FROM (SELECT array_asset_id, uniq_asset_id FROM temp_assets) AS ta
                    WHERE ta.array_asset_id LIKE concat('%',assets_tag.asset_id,'%');

                    CREATE TABLE assets_tags_temp AS SELECT DISTINCT * FROM assets_tags;
                    DROP TABLE assets_tags;
                    ALTER TABLE assets_tags_temp RENAME TO assets_tags;

                    ALTER TABLE assets_tags ADD CONSTRAINT assets_tags_pkey PRIMARY KEY (asset_id, tag_id);
                    CREATE INDEX IF NOT EXISTS idx_assets_tags_asset on assets_tags (asset_id);
                    CREATE INDEX IF NOT EXISTS idx_assets_tags_tag on assets_tags (tag_id);

                    -- update the relation inject_expectations
                    UPDATE injects_expectations injects_expectation SET asset_id=ta.uniq_asset_id
                    FROM (SELECT array_asset_id, uniq_asset_id FROM temp_assets) AS ta
                    WHERE ta.array_asset_id LIKE concat('%',injects_expectation.asset_id,'%');

                    -- drop temp asset table
                    DROP TABLE temp_assets;
                    -- delete old endpoints which are unused now
                    DELETE FROM assets WHERE asset_type='Endpoint' AND asset_id NOT IN (SELECT DISTINCT agent_asset FROM agents);
                    """);
  }
}
