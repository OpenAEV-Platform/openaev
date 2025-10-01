package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_23__Force_es_reindex extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // FIXME replace this trigger by another method, update the updated at attribute of the asset
      // when we delete an inject from an exercise or a scenario is not the best way to re-index the
      // endpoints in ElasticSearch
      // Add trigger after deleting an injects_assets to update the asset updated at
      statement.executeUpdate(
          """
            CREATE OR REPLACE FUNCTION update_asset_updated_at_after_delete_inject()
                RETURNS TRIGGER AS $$
            BEGIN
                UPDATE public.assets
                SET asset_updated_at = now()
                WHERE asset_id = OLD.asset_id;
                RETURN OLD;
            END;
            $$ LANGUAGE plpgsql;

            -- Trigger for AFTER DELETE
            CREATE TRIGGER after_delete_update_asset_updated_at
                AFTER DELETE ON public.injects_assets
                FOR EACH ROW EXECUTE FUNCTION update_asset_updated_at_after_delete_inject();
        """);
      // re-index in ES
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type in ('expectation-inject', 'simulation', 'scenario', 'endpoint', 'inject', 'vulnerable-endpoint', 'finding', 'security-platform', 'tag', 'attack-pattern');");
    }
  }
}
