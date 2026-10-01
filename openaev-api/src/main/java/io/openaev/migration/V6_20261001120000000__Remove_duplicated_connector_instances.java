package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Removes the connector instances that share the same connector id ({@code COLLECTOR_ID}, {@code
 * INJECTOR_ID}, ...) inside a tenant.
 *
 * <p>Migrating an existing connector twice (double submit, second tab, stale page) created two
 * instances carrying the same connector id. XTM Composer names a deployment after the catalog title
 * and that id, so both instances got the same deployment name: the composer saw a deployment owned
 * by another instance id and looped on the name collision, recreating the pod forever. The
 * single-result lookups by connector id also failed on these rows. Creation is now idempotent.
 *
 * <p>For each (tenant, id key, connector id) group, the started instance is kept (it owns the
 * running deployment), then the oldest one. Secret references of the removed instances are moved to
 * the kept one. Configurations and logs cascade on delete. The connector row itself (collector,
 * injector, ...) is not touched.
 *
 * <p>Idempotent: re-runs find no group with more than one instance.
 */
@Component
public class V6_20261001120000000__Remove_duplicated_connector_instances extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP TABLE IF EXISTS pg_temp.duplicated_connector_instances;");
      statement.execute(
          """
          CREATE TEMP TABLE duplicated_connector_instances AS
          SELECT ranked.instance_id, ranked.kept_instance_id
          FROM (
            SELECT ci.connector_instance_id AS instance_id,
                   FIRST_VALUE(ci.connector_instance_id) OVER instance_group AS kept_instance_id,
                   ROW_NUMBER() OVER instance_group AS instance_rank
            FROM connector_instances ci
            JOIN catalog_connectors cc
              ON cc.catalog_connector_id = ci.connector_instance_catalog_id
            JOIN connector_instance_configurations conf
              ON conf.connector_instance_id = ci.connector_instance_id
             AND conf.connector_instance_configuration_key = cc.catalog_connector_type::text || '_ID'
            WINDOW instance_group AS (
              PARTITION BY ci.tenant_id,
                           conf.connector_instance_configuration_key,
                           conf.connector_instance_configuration_value #>> '{}'
              ORDER BY CASE WHEN ci.connector_instance_current_status = 'started' THEN 0 ELSE 1 END,
                       ci.connector_instance_created_at,
                       ci.connector_instance_id
            )
          ) ranked
          WHERE ranked.instance_rank > 1;
          """);

      statement.execute(
          """
          UPDATE secret_references sr
          SET secret_reference_connector_instance_id = dup.kept_instance_id
          FROM duplicated_connector_instances dup
          WHERE sr.secret_reference_connector_instance_id = dup.instance_id;
          """);

      statement.execute(
          """
          DELETE FROM connector_instances ci
          USING duplicated_connector_instances dup
          WHERE ci.connector_instance_id = dup.instance_id;
          """);

      statement.execute("DROP TABLE pg_temp.duplicated_connector_instances;");
    }
  }
}
