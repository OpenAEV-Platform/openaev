package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Security platforms registered by an injector (e.g. the Nuclei vulnerability scanner, which
 * declares itself as a VULNERABILITY_SCANNER platform at startup) must be read-only in the UI while
 * the registering injector is alive, exactly like collector-managed platforms (#7063, follow-up to
 * #7025). The collector side models this with the {@code collector_security_platform} FK ({@code ON
 * DELETE SET NULL}), serialized as {@code security_platform_collectors}; this migration adds the
 * symmetric injector -> platform link:
 *
 * <ul>
 *   <li>{@code injectors.injector_security_platform}, FK to {@code assets} {@code ON DELETE SET
 *       NULL}: deleting the injector from the catalog releases the platform (and its logos) for
 *       manual cleanup, and a redeployed injector relinks it through the security platform upsert
 *       it performs at startup.
 *   <li>Backfill: an injector-registered platform carries the registering injector's type in {@code
 *       asset_external_reference} (that is the documented upsert key), so live links are restored
 *       by matching it against {@code injector_type} within the same tenant. Collector external
 *       references are collector ids and never collide with injector types.
 * </ul>
 *
 * <p>Idempotent: the column and constraint are guarded with IF NOT EXISTS, and the backfill only
 * touches rows whose link is still unset.
 */
@Component
public class V6_20260729170000000__Link_security_platforms_to_registering_injectors
    extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          ALTER TABLE injectors ADD COLUMN IF NOT EXISTS injector_security_platform varchar(255);
          """);
      statement.executeUpdate(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint WHERE conname = 'fk_injector_security_platform'
            ) THEN
              ALTER TABLE injectors
                ADD CONSTRAINT fk_injector_security_platform
                FOREIGN KEY (injector_security_platform) REFERENCES assets(asset_id)
                ON DELETE SET NULL;
            END IF;
          END $$;
          """);
      statement.executeUpdate(
          """
          UPDATE injectors i
          SET injector_security_platform = a.asset_id
          FROM assets a
          WHERE i.injector_security_platform IS NULL
            AND a.asset_type = 'SecurityPlatform'
            AND a.asset_external_reference = i.injector_type
            AND a.tenant_id = i.tenant_id;
          """);
    }
  }
}
