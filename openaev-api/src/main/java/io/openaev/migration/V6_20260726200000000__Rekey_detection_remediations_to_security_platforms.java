package io.openaev.migration;

import io.openaev.utils.CollectorTypeHumanizer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Re-keys detection remediations from collector types to security platform assets. Remediation
 * rules must work with any security platform - including manually created ones with no collector at
 * all - so the {@code detection_remediation_collector_type} link is replaced by {@code
 * detection_remediation_security_platform} (FK to {@code assets}).
 *
 * <p>Backfill strategy, per remediation row:
 *
 * <ol>
 *   <li>If a collector of the same type (and tenant as the remediation's payload) declares a
 *       security platform, reuse it (deterministically the smallest asset id).
 *   <li>Otherwise create (or reuse by case-insensitive name within the tenant) a manual security
 *       platform derived from the collector type name via {@link CollectorTypeHumanizer}.
 *   <li>Rows still unresolved after both steps (should be none) are deleted before the column is
 *       made NOT NULL.
 * </ol>
 *
 * <p>The legacy {@code detection_remediation_collector_type} column is kept (made nullable) for
 * archaeology; the entity no longer maps it.
 */
@Component
public class V6_20260726200000000__Rekey_detection_remediations_to_security_platforms
    extends BaseJavaMigration {

  private record PendingRemediationKey(
      String collectorTypeId, String collectorTypeName, String tenantId) {}

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();

    try (Statement stmt = connection.createStatement()) {
      // 1. New column, FK to assets (cascade on platform deletion) and index
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ADD COLUMN IF NOT EXISTS detection_remediation_security_platform VARCHAR(255);
          """);
      stmt.execute(
          """
          DO $$
          BEGIN
            IF NOT EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname = 'fk_detection_remediation_security_platform'
            ) THEN
              ALTER TABLE detection_remediations
                  ADD CONSTRAINT fk_detection_remediation_security_platform
                      FOREIGN KEY (detection_remediation_security_platform)
                      REFERENCES assets(asset_id) ON DELETE CASCADE;
            END IF;
          END $$;
          """);
      stmt.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_detection_remediation_security_platform
              ON detection_remediations(detection_remediation_security_platform);
          """);

      // 2. Backfill from collectors: a collector of the remediation's collector type in the same
      // tenant as the remediation's payload that declares a security platform (smallest asset id
      // wins for determinism)
      stmt.executeUpdate(
          """
          UPDATE detection_remediations dr
          SET detection_remediation_security_platform = sub.platform_id
          FROM payloads p,
               (SELECT c.collector_type_id,
                       c.tenant_id,
                       min(c.collector_security_platform) AS platform_id
                FROM collectors c
                WHERE c.collector_security_platform IS NOT NULL
                  AND c.collector_type_id IS NOT NULL
                GROUP BY c.collector_type_id, c.tenant_id) sub
          WHERE dr.detection_remediation_security_platform IS NULL
            AND p.payload_id = dr.detection_remediation_payload_id
            AND sub.collector_type_id = dr.detection_remediation_collector_type
            AND sub.tenant_id = p.tenant_id;
          """);
    }

    // 3. Remaining rows: create (or reuse by name within the tenant) a manual security platform
    // derived from the collector type name
    List<PendingRemediationKey> pendingKeys = new ArrayList<>();
    try (Statement stmt = connection.createStatement();
        ResultSet rs =
            stmt.executeQuery(
                """
                SELECT DISTINCT dr.detection_remediation_collector_type,
                                ct.collector_type_name,
                                p.tenant_id
                FROM detection_remediations dr
                JOIN payloads p ON p.payload_id = dr.detection_remediation_payload_id
                JOIN collector_types ct
                  ON ct.collector_type_id = dr.detection_remediation_collector_type
                WHERE dr.detection_remediation_security_platform IS NULL;
                """)) {
      while (rs.next()) {
        pendingKeys.add(
            new PendingRemediationKey(rs.getString(1), rs.getString(2), rs.getString(3)));
      }
    }

    for (PendingRemediationKey key : pendingKeys) {
      CollectorTypeHumanizer.HumanizedPlatform platform =
          CollectorTypeHumanizer.humanize(key.collectorTypeName());
      String platformId = findOrCreateSecurityPlatform(connection, key.tenantId(), platform);
      try (PreparedStatement update =
          connection.prepareStatement(
              """
              UPDATE detection_remediations dr
              SET detection_remediation_security_platform = ?
              FROM payloads p
              WHERE p.payload_id = dr.detection_remediation_payload_id
                AND dr.detection_remediation_security_platform IS NULL
                AND dr.detection_remediation_collector_type = ?
                AND p.tenant_id = ?;
              """)) {
        update.setString(1, platformId);
        update.setString(2, key.collectorTypeId());
        update.setString(3, key.tenantId());
        update.executeUpdate();
      }
    }

    // 4. Cleanup: drop unresolved leftovers (should be none), enforce NOT NULL on the new column
    // and relax it on the legacy one (kept for archaeology, no longer mapped)
    try (Statement stmt = connection.createStatement()) {
      stmt.executeUpdate(
          """
          DELETE FROM detection_remediations
          WHERE detection_remediation_security_platform IS NULL;
          """);
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ALTER COLUMN detection_remediation_security_platform SET NOT NULL;
          """);
      stmt.execute(
          """
          ALTER TABLE detection_remediations
              ALTER COLUMN detection_remediation_collector_type DROP NOT NULL;
          """);
    }
  }

  private String findOrCreateSecurityPlatform(
      Connection connection, String tenantId, CollectorTypeHumanizer.HumanizedPlatform platform)
      throws Exception {
    try (PreparedStatement find =
        connection.prepareStatement(
            """
            SELECT asset_id FROM assets
            WHERE asset_type = 'SecurityPlatform'
              AND tenant_id = ?
              AND lower(asset_name) = lower(?)
            ORDER BY asset_id
            LIMIT 1;
            """)) {
      find.setString(1, tenantId);
      find.setString(2, platform.name());
      try (ResultSet rs = find.executeQuery()) {
        if (rs.next()) {
          return rs.getString(1);
        }
      }
    }
    String assetId = UUID.randomUUID().toString();
    try (PreparedStatement insert =
        connection.prepareStatement(
            """
            INSERT INTO assets (asset_id, asset_type, asset_name, tenant_id,
                                security_platform_type, asset_category, asset_subcategory,
                                asset_created_at, asset_updated_at)
            VALUES (?, 'SecurityPlatform', ?, ?, ?, 'SECURITY_PLATFORM', ?, now(), now());
            """)) {
      insert.setString(1, assetId);
      insert.setString(2, platform.name());
      insert.setString(3, tenantId);
      insert.setString(4, platform.type().name());
      insert.setString(5, platform.type().name());
      insert.executeUpdate();
    }
    return assetId;
  }
}
