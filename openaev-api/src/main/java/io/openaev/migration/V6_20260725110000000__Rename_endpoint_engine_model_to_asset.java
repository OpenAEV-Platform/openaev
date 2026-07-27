package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Repoints saved dashboard widgets at the {@code asset} engine model, which replaces {@code
 * endpoint}.
 *
 * <p>The index used to hold host rows only, so nothing on a dashboard could count the rest of the
 * inventory (AI targets, web / cloud / network assets were indexed nowhere). It now mirrors {@code
 * POST /api/assets/search} - every asset except security platforms - and is named after what it
 * holds, with the asset-level fields renamed accordingly. Widgets store their perspective and their
 * columns as literal entity / field names in {@code widget_config}, so every saved widget has to be
 * rewritten or it would silently resolve to nothing.
 *
 * <p>Only whole tokens are rewritten: the host-only {@code endpoint_platform}, {@code
 * endpoint_arch} and {@code endpoint_is_eol} fields keep their names (they are null outside hosts,
 * which is exactly how they read in a filter), and {@code vulnerable-endpoint} is untouched.
 *
 * <p>No reindex trigger is needed: {@code asset} has no {@code indexing_status} row, so the engine
 * driver creates the index and the indexer feeds it from PostgreSQL on the next startup, while the
 * retired {@code endpoint} index is dropped by the driver (see {@code RetiredIndexes}). Its
 * bookkeeping row is deleted here so no cursor survives for a model that no longer exists.
 *
 * <p>Idempotent (the rewrites no longer match once applied) and lock-light (targeted UPDATE /
 * DELETE on small tables).
 */
@Component
public class V6_20260725110000000__Rename_endpoint_engine_model_to_asset extends BaseJavaMigration {

  /** Asset-level fields that lost their endpoint_ prefix with the model. */
  private static final String[] RENAMED_FIELDS = {
    "name",
    "description",
    "external_reference",
    "category",
    "ips",
    "hostname",
    "mac_addresses",
    "seen_ip"
  };

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- 1. Repoint the perspective of every widget built on the endpoint entity. Scoped to the
      // base_entity filter so a widget that happens to filter another field on the literal value
      // "endpoint" (e.g. a payload type affinity) is left alone. --
      statement.executeUpdate(
          "UPDATE widgets SET widget_config = regexp_replace("
              + "widget_config::text, "
              + "'(\"key\": \"base_entity\"[^}]*\"values\": \\[[^]]*)\"endpoint\"', "
              + "'\\1\"asset\"', 'g')::jsonb "
              + "WHERE widget_config::text LIKE '%\"base_entity\"%' "
              + "AND widget_config::text LIKE '%\"endpoint\"%';");

      // -- 2. Rename the fields the widgets reference (list columns, sorts, structural attributes,
      // filter keys). Quoted on both sides so vulnerable_endpoint_* never matches. --
      for (String field : RENAMED_FIELDS) {
        statement.executeUpdate(
            "UPDATE widgets SET widget_config = REPLACE("
                + "widget_config::text, '\"endpoint_"
                + field
                + "\"', '\"asset_"
                + field
                + "\"')::jsonb "
                + "WHERE widget_config::text LIKE '%\"endpoint_"
                + field
                + "\"%';");
      }

      // -- 3. Relabel the inventory KPI seeded by the starter pack, which counted endpoints and now
      // counts the whole inventory. Its seeded title and series name are lowercase; titles written
      // by users are left as they are. --
      statement.executeUpdate(
          "UPDATE widgets SET widget_config = REPLACE(REPLACE("
              + "widget_config::text, '\"title\": \"endpoints\"', '\"title\": \"assets\"'), "
              + "'\"name\": \"endpoints\"', '\"name\": \"assets\"')::jsonb "
              + "WHERE widget_config::text LIKE '%\"endpoints\"%';");

      // -- 4. Drop the indexing cursor of the retired model. --
      statement.executeUpdate(
          "DELETE FROM indexing_status WHERE indexing_status_type = 'endpoint';");
    }
  }
}
