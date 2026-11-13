package io.openaev.migration;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.FindingLocationKeys;
import io.openaev.finding.identity.FindingValueNormalizer;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.finding.identity.LocationIdentity.UserLocation;
import io.openaev.service.EsIndexingUtils;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Converts the old-shape findings (one row per inject, no identity hash) into stable findings, one
 * per (tenant, type, normalized value hash, location) (#7848, chunk 5).
 *
 * <p>Each old row becomes one finding per location: each of its assets, else each of its users,
 * else the unknown location. The first location without a stable finding converts the row in
 * place; another location gets a copy, and a location that already has one merges the row into
 * it. A finding fed by a row gets its occurrences (inject, date, field), tags, teams and its
 * location link; a row merged everywhere is deleted, its phishing results moved to the finding it
 * merged into. First and last seen are recomputed from the occurrences.
 *
 * <p>Also drops {@code finding_labels}, no longer mapped, and makes {@code finding_location_key} a
 * plain column written by the application ({@link FindingLocationKeys}). Deleting an inject now
 * keeps its findings: {@code finding_inject_id} goes from {@code ON DELETE CASCADE} to {@code SET
 * NULL}, like the inject of their occurrences. Drops the old unique key (inject, value, type,
 * field): the identity key {@code findings_identity_unique} exists since chunk 1. The search index of the
 * findings is reset, since merged rows lose their id. Runs outside a transaction, one commit per
 * batch: a rerun skips the rows already converted.
 */
@Slf4j
@Component
public class V6_20261112160000000__Convert_findings_to_identity extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;
  private static final String FINDING_MODEL = "finding";

  /** An old-shape row and what it is linked to. */
  private record OldFinding(
      String id,
      String tenantId,
      String type,
      String value,
      String valueDisplay,
      String name,
      Timestamp createdAt,
      Timestamp updatedAt,
      List<String> assetIds,
      List<String> userIds,
      List<Occurrence> occurrences) {

    List<LocationIdentity> locations() {
      List<LocationIdentity> locations = new ArrayList<>();
      assetIds.forEach(assetId -> locations.add(new AssetLocation(assetId)));
      userIds.forEach(userId -> locations.add(new UserLocation(userId)));
      return locations.isEmpty() ? List.of(UnknownLocation.INSTANCE) : locations;
    }

    Timestamp firstDate() {
      return occurrences.stream().map(Occurrence::date).min(Timestamp::compareTo).orElse(createdAt);
    }

    Timestamp lastDate() {
      return occurrences.stream().map(Occurrence::date).max(Timestamp::compareTo).orElse(updatedAt);
    }
  }

  private record Occurrence(String injectId, Timestamp date, String field) {}

  @Override
  public boolean canExecuteInTransaction() {
    return false;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    boolean autoCommit = connection.getAutoCommit();
    connection.setAutoCommit(false);
    try {
      try (Statement statement = connection.createStatement()) {
        statement.execute("ALTER TABLE findings DROP CONSTRAINT IF EXISTS finding_inject_id_fk");
        statement.execute(
            "ALTER TABLE findings ADD CONSTRAINT finding_inject_id_fk"
                + " FOREIGN KEY (finding_inject_id) REFERENCES injects (inject_id) ON DELETE SET NULL");
        statement.execute(
            "ALTER TABLE finding_locations ALTER COLUMN finding_location_key"
                + " DROP EXPRESSION IF EXISTS");
        statement.execute("ALTER TABLE findings DROP COLUMN IF EXISTS finding_labels");
        statement.execute("ALTER TABLE findings DROP CONSTRAINT IF EXISTS unique_finding_constraint");
      }
      connection.commit();

      String lastId = "";
      while ((lastId = convertNextBatch(connection, lastId)) != null) {
        connection.commit();
      }

      requestReindex(connection);
      connection.commit();
    } catch (Exception e) {
      connection.rollback();
      throw e;
    } finally {
      connection.setAutoCommit(autoCommit);
    }
    EsIndexingUtils.requestReindexAtStartup(FINDING_MODEL);
  }

  /**
   * Converts the next batch of old-shape rows after {@code lastId}, without committing.
   *
   * @return the last id of the batch, or {@code null} when no row is left
   */
  String convertNextBatch(Connection connection, String lastId) throws SQLException {
    List<OldFinding> batch = readBatch(connection, lastId);
    if (batch.isEmpty()) {
      return null;
    }
    Map<String, String> locationIdByKey = new HashMap<>();
    Set<String> touched = new LinkedHashSet<>();
    for (OldFinding row : batch) {
      convert(connection, row, locationIdByKey, touched);
    }
    recomputeSeen(connection, touched);
    return batch.getLast().id();
  }

  // -- READ --

  private List<OldFinding> readBatch(Connection connection, String lastId) throws SQLException {
    Map<String, Object[]> rows = new LinkedHashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT finding_id, tenant_id, finding_type, finding_value, finding_value_display,"
                + " finding_name, finding_created_at, finding_updated_at FROM findings"
                + " WHERE finding_value_hash IS NULL AND finding_id > ?"
                + " ORDER BY finding_id LIMIT "
                + BATCH_SIZE)) {
      statement.setString(1, lastId);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          rows.put(
              resultSet.getString(1),
              new Object[] {
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getTimestamp(7),
                resultSet.getTimestamp(8)
              });
        }
      }
    }
    if (rows.isEmpty()) {
      return List.of();
    }
    Array ids = connection.createArrayOf("varchar", rows.keySet().toArray());
    Map<String, List<String>> assetIds = readLinks(connection, "findings_assets", "asset_id", ids);
    Map<String, List<String>> userIds = readLinks(connection, "findings_users", "user_id", ids);
    Map<String, List<Occurrence>> occurrences = readOccurrences(connection, ids);
    List<OldFinding> batch = new ArrayList<>();
    rows.forEach(
        (id, row) ->
            batch.add(
                new OldFinding(
                    id,
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (Timestamp) row[5],
                    (Timestamp) row[6],
                    assetIds.getOrDefault(id, List.of()),
                    userIds.getOrDefault(id, List.of()),
                    occurrences.getOrDefault(id, List.of()))));
    return batch;
  }

  private static Map<String, List<String>> readLinks(
      Connection connection, String table, String column, Array findingIds) throws SQLException {
    Map<String, List<String>> links = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT DISTINCT finding_id, "
                + column
                + " FROM "
                + table
                + " WHERE finding_id = ANY (?) ORDER BY 1, 2")) {
      statement.setArray(1, findingIds);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          links
              .computeIfAbsent(resultSet.getString(1), id -> new ArrayList<>())
              .add(resultSet.getString(2));
        }
      }
    }
    return links;
  }

  private static Map<String, List<Occurrence>> readOccurrences(
      Connection connection, Array findingIds) throws SQLException {
    Map<String, List<Occurrence>> occurrences = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT finding_occurrence_finding_id, finding_occurrence_inject_id,"
                + " finding_occurrence_date, finding_occurrence_field FROM finding_occurrences"
                + " WHERE finding_occurrence_finding_id = ANY (?)")) {
      statement.setArray(1, findingIds);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          occurrences
              .computeIfAbsent(resultSet.getString(1), id -> new ArrayList<>())
              .add(
                  new Occurrence(
                      resultSet.getString(2), resultSet.getTimestamp(3), resultSet.getString(4)));
        }
      }
    }
    return occurrences;
  }

  // -- CONVERT --

  private static void convert(
      Connection connection,
      OldFinding row,
      Map<String, String> locationIdByKey,
      Set<String> touched)
      throws SQLException {
    ContractOutputType type;
    try {
      type = ContractOutputType.valueOf(row.type());
    } catch (IllegalArgumentException e) {
      log.warn("Finding {} left unconverted: unknown type {}", row.id(), row.type());
      return;
    }
    LocationIdentity convertedWith = null;
    String firstTarget = null;
    for (LocationIdentity location : row.locations()) {
      String hash;
      try {
        hash = FindingValueNormalizer.hash(type, row.value(), location);
      } catch (IllegalArgumentException e) {
        log.warn("Finding {} left unconverted: {}", row.id(), e.getMessage());
        return;
      }
      String locationId = locationId(connection, location, locationIdByKey);
      String target = findStableFinding(connection, row, hash, locationId);
      if (target == null && convertedWith == null) {
        convertInPlace(connection, row, hash, locationId);
        convertedWith = location;
        target = row.id();
      } else {
        if (target == null) {
          target = copy(connection, row, hash, locationId);
        } else {
          takeLatestValue(connection, row, target);
        }
        copyOccurrences(connection, row, target);
        copyLinks(connection, "findings_tags", "tag_id", row.id(), target);
        copyLinks(connection, "findings_teams", "team_id", row.id(), target);
      }
      linkLocation(connection, location, target);
      touched.add(target);
      if (firstTarget == null) {
        firstTarget = target;
      }
    }
    if (convertedWith != null) {
      keepOnlyLocationLinks(connection, row.id(), convertedWith);
    } else {
      moveAndDelete(connection, row.id(), firstTarget);
    }
  }

  private static String locationId(
      Connection connection, LocationIdentity location, Map<String, String> locationIdByKey)
      throws SQLException {
    String assetId = location instanceof AssetLocation(String id) ? id : null;
    String userId = location instanceof UserLocation(String id) ? id : null;
    String key = FindingLocationKeys.keyOf(assetId, userId);
    String cached = locationIdByKey.get(key);
    if (cached != null) {
      return cached;
    }
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO finding_locations (finding_location_id, finding_location_asset_id,"
                + " finding_location_user_id, finding_location_key) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT DO NOTHING")) {
      insert.setString(1, UUID.randomUUID().toString());
      insert.setString(2, assetId);
      insert.setString(3, userId);
      insert.setString(4, key);
      insert.executeUpdate();
    }
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT finding_location_id FROM finding_locations WHERE finding_location_key = ?")) {
      select.setString(1, key);
      try (ResultSet resultSet = select.executeQuery()) {
        resultSet.next();
        String id = resultSet.getString(1);
        locationIdByKey.put(key, id);
        return id;
      }
    }
  }

  private static String findStableFinding(
      Connection connection, OldFinding row, String hash, String locationId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT finding_id FROM findings WHERE tenant_id = ? AND finding_type = ?"
                + " AND finding_value_hash = ? AND finding_location_id = ?")) {
      statement.setString(1, row.tenantId());
      statement.setString(2, row.type());
      statement.setString(3, hash);
      statement.setString(4, locationId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next() ? resultSet.getString(1) : null;
      }
    }
  }

  // The inject and the field moved to the occurrences in chunk 1
  private static void convertInPlace(
      Connection connection, OldFinding row, String hash, String locationId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE findings SET finding_value_hash = ?, finding_location_id = ?,"
                + " finding_inject_id = NULL, finding_field = NULL, finding_first_seen = ?,"
                + " finding_last_seen = ? WHERE finding_id = ?")) {
      statement.setString(1, hash);
      statement.setString(2, locationId);
      statement.setTimestamp(3, row.firstDate());
      statement.setTimestamp(4, row.lastDate());
      statement.setString(5, row.id());
      statement.executeUpdate();
    }
  }

  private static String copy(Connection connection, OldFinding row, String hash, String locationId)
      throws SQLException {
    String id = UUID.randomUUID().toString();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO findings (finding_id, tenant_id, finding_type, finding_value,"
                + " finding_value_display, finding_name, finding_value_hash, finding_location_id,"
                + " finding_created_at, finding_updated_at, finding_first_seen, finding_last_seen)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
      statement.setString(1, id);
      statement.setString(2, row.tenantId());
      statement.setString(3, row.type());
      statement.setString(4, row.value());
      statement.setString(5, row.valueDisplay());
      statement.setString(6, row.name());
      statement.setString(7, hash);
      statement.setString(8, locationId);
      statement.setTimestamp(9, row.createdAt());
      statement.setTimestamp(10, row.updatedAt());
      statement.setTimestamp(11, row.firstDate());
      statement.setTimestamp(12, row.lastDate());
      statement.executeUpdate();
    }
    return id;
  }

  // A row detected after what the finding last saw gives it its value and name; the dates widen
  private static void takeLatestValue(Connection connection, OldFinding row, String target)
      throws SQLException {
    try (PreparedStatement value =
            connection.prepareStatement(
                "UPDATE findings SET finding_value = ?, finding_value_display = ?,"
                    + " finding_name = ? WHERE finding_id = ?"
                    + " AND (finding_last_seen IS NULL OR finding_last_seen <= ?)");
        PreparedStatement seen =
            connection.prepareStatement(
                "UPDATE findings SET"
                    + " finding_first_seen = LEAST(COALESCE(finding_first_seen, ?), ?),"
                    + " finding_last_seen = GREATEST(COALESCE(finding_last_seen, ?), ?)"
                    + " WHERE finding_id = ?")) {
      value.setString(1, row.value());
      value.setString(2, row.valueDisplay());
      value.setString(3, row.name());
      value.setString(4, target);
      value.setTimestamp(5, row.lastDate());
      value.executeUpdate();
      seen.setTimestamp(1, row.firstDate());
      seen.setTimestamp(2, row.firstDate());
      seen.setTimestamp(3, row.lastDate());
      seen.setTimestamp(4, row.lastDate());
      seen.setString(5, target);
      seen.executeUpdate();
    }
  }

  private static void copyOccurrences(Connection connection, OldFinding row, String target)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
                + " finding_occurrence_inject_id, finding_occurrence_date,"
                + " finding_occurrence_field, finding_occurrence_created_at)"
                + " VALUES (?, ?, ?, ?, ?, now())"
                + " ON CONFLICT (finding_occurrence_finding_id, finding_occurrence_inject_id)"
                + " DO NOTHING")) {
      for (Occurrence occurrence : row.occurrences()) {
        statement.setString(1, UUID.randomUUID().toString());
        statement.setString(2, target);
        statement.setString(3, occurrence.injectId());
        statement.setTimestamp(4, occurrence.date());
        statement.setString(5, occurrence.field());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private static void copyLinks(
      Connection connection, String table, String column, String from, String to)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT DISTINCT ?, l."
                + column
                + " FROM "
                + table
                + " l WHERE l.finding_id = ? AND NOT EXISTS (SELECT 1 FROM "
                + table
                + " t WHERE t.finding_id = ? AND t."
                + column
                + " = l."
                + column
                + ")")) {
      statement.setString(1, to);
      statement.setString(2, from);
      statement.setString(3, to);
      statement.executeUpdate();
    }
  }

  // The join tables the readers use until chunk 7
  private static void linkLocation(Connection connection, LocationIdentity location, String target)
      throws SQLException {
    String table;
    String column;
    String id;
    switch (location) {
      case AssetLocation(String assetId) -> {
        table = "findings_assets";
        column = "asset_id";
        id = assetId;
      }
      case UserLocation(String userId) -> {
        table = "findings_users";
        column = "user_id";
        id = userId;
      }
      case UnknownLocation unknown -> {
        return;
      }
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM "
                + table
                + " WHERE finding_id = ? AND "
                + column
                + " = ?)")) {
      statement.setString(1, target);
      statement.setString(2, id);
      statement.setString(3, target);
      statement.setString(4, id);
      statement.executeUpdate();
    }
  }

  // The row converted in place keeps the links of its own location only
  private static void keepOnlyLocationLinks(
      Connection connection, String findingId, LocationIdentity location) throws SQLException {
    String keptAsset = location instanceof AssetLocation(String id) ? id : "";
    String keptUser = location instanceof UserLocation(String id) ? id : "";
    try (PreparedStatement assets =
            connection.prepareStatement(
                "DELETE FROM findings_assets WHERE finding_id = ? AND asset_id <> ?");
        PreparedStatement users =
            connection.prepareStatement(
                "DELETE FROM findings_users WHERE finding_id = ? AND user_id <> ?")) {
      assets.setString(1, findingId);
      assets.setString(2, keptAsset);
      assets.executeUpdate();
      users.setString(1, findingId);
      users.setString(2, keptUser);
      users.executeUpdate();
    }
  }

  // A phishing result follows the row to the finding it merged into, and to its occurrence there
  private static void moveAndDelete(Connection connection, String findingId, String target)
      throws SQLException {
    try (PreparedStatement results =
            connection.prepareStatement(
                "UPDATE phishing_results pr SET phishing_result_finding = ?,"
                    + " phishing_result_finding_occurrence = ("
                    + "SELECT moved.finding_occurrence_id FROM finding_occurrences previous"
                    + " JOIN finding_occurrences moved"
                    + " ON moved.finding_occurrence_inject_id = previous.finding_occurrence_inject_id"
                    + " AND moved.finding_occurrence_finding_id = ?"
                    + " WHERE previous.finding_occurrence_id"
                    + " = pr.phishing_result_finding_occurrence)"
                    + " WHERE pr.phishing_result_finding = ?");
        PreparedStatement delete =
            connection.prepareStatement("DELETE FROM findings WHERE finding_id = ?")) {
      results.setString(1, target);
      results.setString(2, target);
      results.setString(3, findingId);
      results.executeUpdate();
      delete.setString(1, findingId);
      delete.executeUpdate();
    }
  }

  // From the occurrences; a finding without any keeps the dates of its row
  private static void recomputeSeen(Connection connection, Set<String> findingIds)
      throws SQLException {
    if (findingIds.isEmpty()) {
      return;
    }
    Array ids = connection.createArrayOf("varchar", findingIds.toArray());
    try (PreparedStatement seen =
            connection.prepareStatement(
                "UPDATE findings f SET finding_first_seen = s.first_seen,"
                    + " finding_last_seen = s.last_seen, finding_updated_at = now()"
                    + " FROM (SELECT finding_occurrence_finding_id AS finding_id,"
                    + " min(finding_occurrence_date) AS first_seen,"
                    + " max(finding_occurrence_date) AS last_seen FROM finding_occurrences"
                    + " WHERE finding_occurrence_finding_id = ANY (?)"
                    + " GROUP BY finding_occurrence_finding_id) s"
                    + " WHERE f.finding_id = s.finding_id");
        PreparedStatement withoutOccurrence =
            connection.prepareStatement(
                "UPDATE findings SET finding_updated_at = now() WHERE finding_id = ANY (?)"
                    + " AND NOT EXISTS (SELECT 1 FROM finding_occurrences"
                    + " WHERE finding_occurrence_finding_id = finding_id)")) {
      seen.setArray(1, ids);
      seen.executeUpdate();
      withoutOccurrence.setArray(1, ids);
      withoutOccurrence.executeUpdate();
    }
  }

  // -- SEARCH INDEX --

  // A reset, not a re-feed: the merged rows' ids disappear, and a re-feed never deletes documents
  private static void requestReindex(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO indexing_status (indexing_status_type, indexing_status_indexing_date)"
              + " VALUES ('"
              + FINDING_MODEL
              + "', '"
              + EsIndexingUtils.REINDEX_REQUESTED_CURSOR
              + "')"
              + " ON CONFLICT (indexing_status_type)"
              + " DO UPDATE SET indexing_status_indexing_date = EXCLUDED.indexing_status_indexing_date");
    }
  }
}
