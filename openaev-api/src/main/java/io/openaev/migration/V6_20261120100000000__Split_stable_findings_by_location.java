package io.openaev.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Findings PoC aligned with US.0.0: a finding is identified by Type + Value + Location.
 *
 * <p>Until now a stable finding was tenant + source namespace (injector and output) + type + value,
 * with its locations on the occurrences. This migration gives every (tenant, type, value, location)
 * its own stable finding: a finding seen on 3 assets becomes 3 findings, and two findings that only
 * differed by injector become one, with both injectors as Source. Occurrences and tags move to the
 * new finding; First seen, Last seen, source and lifecycle are computed again from the occurrences.
 */
@Component
public class V6_20261120100000000__Split_stable_findings_by_location extends BaseJavaMigration {

  private static final List<String> RESET_COLUMNS =
      List.of(
          "stable_finding_id",
          "stable_finding_key",
          "stable_finding_location_type",
          "stable_finding_location_key",
          "stable_finding_location",
          "stable_finding_location_asset_id",
          "stable_finding_location_user_id");

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    addColumns(connection);
    List<String> copyColumns = copyableColumns(connection);
    Set<String> usedTargets = new HashSet<>();
    for (Group group : groups(connection)) {
      String target = target(connection, group, usedTargets, copyColumns);
      removeDuplicateLiveOccurrences(connection, group);
      moveOccurrencesAndTags(connection, group, target);
    }
    recomputeFromOccurrences(connection);
  }

  private void addColumns(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
          ALTER TABLE stable_findings
              ADD COLUMN IF NOT EXISTS stable_finding_location_type VARCHAR(255),
              ADD COLUMN IF NOT EXISTS stable_finding_location_key VARCHAR(1024),
              ADD COLUMN IF NOT EXISTS stable_finding_location TEXT,
              ADD COLUMN IF NOT EXISTS stable_finding_location_asset_id VARCHAR(255)
                  CONSTRAINT stable_findings_location_asset_fk
                  REFERENCES assets(asset_id) ON DELETE SET NULL,
              ADD COLUMN IF NOT EXISTS stable_finding_location_user_id VARCHAR(255)
                  CONSTRAINT stable_findings_location_user_fk
                  REFERENCES users(user_id) ON DELETE SET NULL
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_stable_findings_location_asset
              ON stable_findings(stable_finding_location_asset_id)
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_stable_findings_type_value_location
              ON stable_findings(tenant_id, stable_finding_type, stable_finding_location_key)
          """);
      statement.execute(
          "ALTER TABLE stable_findings DROP CONSTRAINT IF EXISTS stable_finding_lifecycle_chk");
      statement.execute(
          """
          ALTER TABLE stable_findings ADD CONSTRAINT stable_finding_lifecycle_chk
              CHECK (stable_finding_lifecycle IN ('ACTIVE', 'REVIEW_REQUIRED', 'MUTED', 'FIXED'))
          """);
      statement.execute(
          """
          ALTER TABLE finding_occurrences
              ADD COLUMN IF NOT EXISTS finding_occurrence_source_injector_id VARCHAR(255)
          """);
      // Under the previous identity a stable finding had exactly one injector: its occurrences
      // inherit it before findings of different injectors are merged.
      statement.execute(
          """
          UPDATE finding_occurrences o
             SET finding_occurrence_source_injector_id = sf.stable_finding_source_injector_id
            FROM stable_findings sf
           WHERE sf.stable_finding_id = o.finding_occurrence_stable_finding_id
             AND o.finding_occurrence_source_injector_id IS NULL
          """);
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_finding_occurrences_source_injector
              ON finding_occurrences(finding_occurrence_source_injector_id)
          """);
    }
  }

  /** Columns copied as-is when a stable finding is split; identity and location are reset. */
  private List<String> copyableColumns(Connection connection) throws Exception {
    List<String> columns = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                """
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'stable_findings'
                 ORDER BY ordinal_position
                """)) {
      while (rs.next()) {
        String column = rs.getString(1);
        if (!RESET_COLUMNS.contains(column)) {
          columns.add(column);
        }
      }
    }
    return columns;
  }

  private List<Group> groups(Connection connection) throws Exception {
    List<Group> groups = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                """
                SELECT o.tenant_id,
                       sf.stable_finding_type,
                       sf.stable_finding_value,
                       o.finding_occurrence_location_type,
                       o.finding_occurrence_location_key,
                       array_agg(o.finding_occurrence_id) AS occurrence_ids,
                       array_agg(DISTINCT sf.stable_finding_id) AS old_ids,
                       (array_agg(o.finding_occurrence_location
                                  ORDER BY o.finding_occurrence_observed_at DESC))[1] AS display,
                       (array_agg(o.finding_occurrence_location_asset_id)
                            FILTER (WHERE o.finding_occurrence_location_asset_id IS NOT NULL))[1]
                           AS asset_id,
                       (array_agg(o.finding_occurrence_location_user_id)
                            FILTER (WHERE o.finding_occurrence_location_user_id IS NOT NULL))[1]
                           AS user_id
                  FROM finding_occurrences o
                  JOIN stable_findings sf
                    ON sf.stable_finding_id = o.finding_occurrence_stable_finding_id
                 GROUP BY o.tenant_id, sf.stable_finding_type, sf.stable_finding_value,
                          o.finding_occurrence_location_type, o.finding_occurrence_location_key
                 ORDER BY min(o.finding_occurrence_observed_at)
                """)) {
      while (rs.next()) {
        String[] oldIds = strings(rs.getArray("old_ids"));
        Arrays.sort(oldIds);
        groups.add(
            new Group(
                rs.getString("tenant_id"),
                rs.getString("stable_finding_type"),
                rs.getString("stable_finding_value"),
                rs.getString("finding_occurrence_location_type"),
                rs.getString("finding_occurrence_location_key"),
                strings(rs.getArray("occurrence_ids")),
                oldIds,
                rs.getString("display"),
                rs.getString("asset_id"),
                rs.getString("user_id")));
      }
    }
    return groups;
  }

  /**
   * Reuses one of the group's former findings when it is not taken yet, otherwise copies one: the
   * first location of a former finding keeps its id (and its links), the others get a new one.
   */
  private String target(
      Connection connection, Group group, Set<String> usedTargets, List<String> copyColumns)
      throws Exception {
    String target = null;
    for (String oldId : group.oldIds()) {
      if (!usedTargets.contains(oldId)) {
        target = oldId;
        break;
      }
    }
    if (target == null) {
      target = UUID.randomUUID().toString();
      String columns = String.join(", ", copyColumns);
      try (PreparedStatement insert =
          connection.prepareStatement(
              "INSERT INTO stable_findings (stable_finding_id, stable_finding_key, "
                  + columns
                  + ") SELECT ?, ?, "
                  + columns
                  + " FROM stable_findings WHERE stable_finding_id = ?")) {
        insert.setString(1, target);
        insert.setString(2, "split-" + target);
        insert.setString(3, group.oldIds()[0]);
        insert.executeUpdate();
      }
    }
    usedTargets.add(target);
    try (PreparedStatement update =
        connection.prepareStatement(
            """
            UPDATE stable_findings
               SET stable_finding_key = ?,
                   stable_finding_location_type = ?,
                   stable_finding_location_key = ?,
                   stable_finding_location = ?,
                   stable_finding_location_asset_id = ?,
                   stable_finding_location_user_id = ?
             WHERE stable_finding_id = ?
            """)) {
      update.setString(1, stableKey(group));
      update.setString(2, group.locationType());
      update.setString(3, group.locationKey());
      update.setString(4, group.display());
      update.setString(5, group.assetId());
      update.setString(6, group.userId());
      update.setString(7, target);
      update.executeUpdate();
    }
    return target;
  }

  /**
   * Two former findings of one inject (two outputs with the same type and value) become one: keep a
   * single live occurrence per inject at this location, as the live-callback unique index requires.
   */
  private void removeDuplicateLiveOccurrences(Connection connection, Group group) throws Exception {
    if (group.oldIds().length < 2) {
      return;
    }
    try (PreparedStatement delete =
        connection.prepareStatement(
            """
            DELETE FROM finding_occurrences d
             USING finding_occurrences k
             WHERE d.finding_occurrence_id = ANY (?)
               AND k.finding_occurrence_id = ANY (?)
               AND d.finding_occurrence_migrated_from IS NULL
               AND k.finding_occurrence_migrated_from IS NULL
               AND d.finding_occurrence_inject_id = k.finding_occurrence_inject_id
               AND d.finding_occurrence_id > k.finding_occurrence_id
            """)) {
      Array ids = connection.createArrayOf("varchar", group.occurrenceIds());
      delete.setArray(1, ids);
      delete.setArray(2, ids);
      delete.executeUpdate();
    }
  }

  private void moveOccurrencesAndTags(Connection connection, Group group, String target)
      throws Exception {
    try (PreparedStatement move =
        connection.prepareStatement(
            """
            UPDATE finding_occurrences SET finding_occurrence_stable_finding_id = ?
             WHERE finding_occurrence_id = ANY (?)
            """)) {
      move.setString(1, target);
      move.setArray(2, connection.createArrayOf("varchar", group.occurrenceIds()));
      move.executeUpdate();
    }
    try (PreparedStatement tags =
        connection.prepareStatement(
            """
            INSERT INTO stable_findings_tags (stable_finding_id, tag_id)
            SELECT ?, t.tag_id FROM stable_findings_tags t WHERE t.stable_finding_id = ANY (?)
            ON CONFLICT DO NOTHING
            """)) {
      tags.setString(1, target);
      tags.setArray(2, connection.createArrayOf("varchar", group.oldIds()));
      tags.executeUpdate();
    }
  }

  private void recomputeFromOccurrences(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      // Former findings whose locations all moved to other findings.
      statement.execute(
          """
          DELETE FROM stable_findings sf
           WHERE NOT EXISTS (
               SELECT 1 FROM finding_occurrences o
                WHERE o.finding_occurrence_stable_finding_id = sf.stable_finding_id)
          """);
      statement.execute(
          """
          UPDATE stable_findings sf
             SET stable_finding_first_seen = agg.first_seen,
                 stable_finding_last_seen = agg.last_seen
            FROM (SELECT o.finding_occurrence_stable_finding_id AS id,
                         min(o.finding_occurrence_observed_at) AS first_seen,
                         max(o.finding_occurrence_observed_at) AS last_seen
                    FROM finding_occurrences o
                   GROUP BY o.finding_occurrence_stable_finding_id) agg
           WHERE agg.id = sf.stable_finding_id
          """);
      // Source of the finding = injector of its first detection; the others are listed as "+N".
      statement.execute(
          """
          UPDATE stable_findings sf
             SET stable_finding_source_injector_id = first_occurrence.injector_id
            FROM (SELECT DISTINCT ON (o.finding_occurrence_stable_finding_id)
                         o.finding_occurrence_stable_finding_id AS id,
                         o.finding_occurrence_source_injector_id AS injector_id
                    FROM finding_occurrences o
                   WHERE o.finding_occurrence_source_injector_id IS NOT NULL
                   ORDER BY o.finding_occurrence_stable_finding_id,
                            o.finding_occurrence_observed_at, o.finding_occurrence_id) first_occurrence
           WHERE first_occurrence.id = sf.stable_finding_id
          """);
      statement.execute(
          """
          UPDATE stable_findings sf
             SET stable_finding_lifecycle = CASE upper(coalesce(latest.outcome, ''))
                     WHEN 'MANUAL' THEN 'REVIEW_REQUIRED'
                     WHEN 'MUTED' THEN 'MUTED'
                     ELSE 'ACTIVE'
                 END
            FROM (SELECT DISTINCT ON (o.finding_occurrence_stable_finding_id)
                         o.finding_occurrence_stable_finding_id AS id,
                         o.finding_occurrence_outcome AS outcome
                    FROM finding_occurrences o
                   ORDER BY o.finding_occurrence_stable_finding_id,
                            o.finding_occurrence_observed_at DESC,
                            o.finding_occurrence_id DESC) latest
           WHERE latest.id = sf.stable_finding_id
          """);
    }
  }

  /** Same key as StableFindingIngestionService#stableKey: tenant, type, value and location. */
  static String stableKey(Group group) {
    return sha256(
        group.tenantId(),
        group.type(),
        group.value(),
        group.locationType() == null ? "" : group.locationType(),
        group.locationKey() == null ? "" : group.locationKey());
  }

  private static String sha256(String... components) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (String component : components) {
        byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String[] strings(Array array) throws Exception {
    Object[] values = (Object[]) array.getArray();
    return Arrays.stream(values).map(String::valueOf).toArray(String[]::new);
  }

  record Group(
      String tenantId,
      String type,
      String value,
      String locationType,
      String locationKey,
      String[] occurrenceIds,
      String[] oldIds,
      String display,
      String assetId,
      String userId) {}
}
