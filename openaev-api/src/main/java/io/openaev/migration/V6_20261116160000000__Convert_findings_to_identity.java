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
import java.util.Collection;
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

@Slf4j
@Component
public class V6_20261116160000000__Convert_findings_to_identity extends BaseJavaMigration {

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
        statement.execute(
            "ALTER TABLE findings DROP CONSTRAINT IF EXISTS unique_finding_constraint");
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
   * Converts the next batch of old-shape rows after {@code lastId}, without committing. Each step
   * runs once for the whole batch: the identities are computed in Java, the writes are set-based.
   *
   * @return the last id of the batch, or {@code null} when no row is left
   */
  String convertNextBatch(Connection connection, String lastId) throws SQLException {
    List<OldFinding> batch = readBatch(connection, lastId);
    if (batch.isEmpty()) {
      return null;
    }
    List<Conversion> conversions = conversions(batch);
    if (!conversions.isEmpty()) {
      convert(connection, conversions);
    }
    return batch.getLast().id();
  }

  private static void convert(Connection connection, List<Conversion> conversions)
      throws SQLException {
    // 1 - the identity of each (row, location)
    Map<String, String> locationIdByKey = locationIds(connection, conversions);
    List<Identity> identities = new ArrayList<>();
    for (Conversion conversion : conversions) {
      OldFinding row = conversion.row();
      identities.add(
          new Identity(
              row.tenantId(),
              row.type(),
              conversion.hash(),
              locationIdByKey.get(conversion.key())));
    }
    // 2 - the stable finding of each identity: the one already there, else a new one
    Map<Identity, Target> targets = existingTargets(connection, new LinkedHashSet<>(identities));
    List<String> oldIds = new ArrayList<>();
    List<String> targetIds = new ArrayList<>();
    List<String[]> assetLinks = new ArrayList<>();
    List<String[]> userLinks = new ArrayList<>();
    Map<String, String> firstTargetByRow = new LinkedHashMap<>();
    for (int i = 0; i < conversions.size(); i++) {
      Conversion conversion = conversions.get(i);
      Target target =
          targets.computeIfAbsent(
              identities.get(i), identity -> new Target(UUID.randomUUID().toString(), false));
      target.take(conversion.row());
      oldIds.add(conversion.row().id());
      targetIds.add(target.id);
      firstTargetByRow.putIfAbsent(conversion.row().id(), target.id);
      switch (conversion.location()) {
        case AssetLocation(String assetId) -> assetLinks.add(new String[] {target.id, assetId});
        case UserLocation(String userId) -> userLinks.add(new String[] {target.id, userId});
        case UnknownLocation unknown -> {}
      }
    }
    // 3 - write the findings, pour the old rows into them, then delete the old rows
    writeTargets(connection, targets);
    Array oldIdArray = varchars(connection, oldIds);
    Array targetIdArray = varchars(connection, targetIds);
    copyOccurrences(connection, oldIdArray, targetIdArray);
    copyLinks(connection, "findings_tags", "tag_id", oldIdArray, targetIdArray);
    copyLinks(connection, "findings_teams", "team_id", oldIdArray, targetIdArray);
    linkLocations(connection, "findings_assets", "asset_id", assetLinks);
    linkLocations(connection, "findings_users", "user_id", userLinks);
    moveAndDelete(connection, firstTargetByRow);
    recomputeSeen(connection, new LinkedHashSet<>(targetIds));
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

  /** One location of an old row, and the identity hash of its value there. */
  private record Conversion(OldFinding row, LocationIdentity location, String hash, String key) {}

  private record Identity(String tenantId, String type, String hash, String locationId) {}

  /** The stable finding of one identity, and the value and dates the batch brings to it. */
  private static final class Target {
    private final String id;
    private final boolean existing;
    private String value;
    private String valueDisplay;
    private String name;
    private Timestamp firstSeen;
    private Timestamp lastSeen;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    private Target(String id, boolean existing) {
      this.id = id;
      this.existing = existing;
    }

    // A row detected after what the finding last saw gives it its value and name; the dates widen
    private void take(OldFinding row) {
      if (lastSeen == null || !row.lastDate().before(lastSeen)) {
        value = row.value();
        valueDisplay = row.valueDisplay();
        name = row.name();
      }
      firstSeen = min(firstSeen, row.firstDate());
      lastSeen = max(lastSeen, row.lastDate());
      createdAt = min(createdAt, row.createdAt());
      updatedAt = max(updatedAt, row.updatedAt());
    }
  }

  // A row whose type or value cannot be hashed is left as it is, before anything is written
  private static List<Conversion> conversions(List<OldFinding> batch) {
    List<Conversion> conversions = new ArrayList<>();
    for (OldFinding row : batch) {
      try {
        ContractOutputType type = ContractOutputType.valueOf(row.type());
        List<Conversion> ofRow = new ArrayList<>();
        for (LocationIdentity location : row.locations()) {
          ofRow.add(
              new Conversion(
                  row,
                  location,
                  FindingValueNormalizer.hash(type, row.value(), location),
                  FindingLocationKeys.keyOf(assetId(location), userId(location))));
        }
        conversions.addAll(ofRow);
      } catch (IllegalArgumentException e) {
        log.warn("Finding {} left unconverted: {}", row.id(), e.getMessage());
      }
    }
    return conversions;
  }

  private static Map<String, String> locationIds(
      Connection connection, List<Conversion> conversions) throws SQLException {
    Map<String, LocationIdentity> locationByKey = new LinkedHashMap<>();
    conversions.forEach(
        conversion -> locationByKey.putIfAbsent(conversion.key(), conversion.location()));
    try (PreparedStatement insert =
        connection.prepareStatement(
            "INSERT INTO finding_locations (finding_location_id, finding_location_asset_id,"
                + " finding_location_user_id, finding_location_key) VALUES (?, ?, ?, ?)"
                + " ON CONFLICT DO NOTHING")) {
      for (Map.Entry<String, LocationIdentity> entry : locationByKey.entrySet()) {
        insert.setString(1, UUID.randomUUID().toString());
        insert.setString(2, assetId(entry.getValue()));
        insert.setString(3, userId(entry.getValue()));
        insert.setString(4, entry.getKey());
        insert.addBatch();
      }
      insert.executeBatch();
    }
    Map<String, String> locationIdByKey = new HashMap<>();
    try (PreparedStatement select =
        connection.prepareStatement(
            "SELECT finding_location_key, finding_location_id FROM finding_locations"
                + " WHERE finding_location_key = ANY (?)")) {
      select.setArray(1, varchars(connection, locationByKey.keySet()));
      try (ResultSet resultSet = select.executeQuery()) {
        while (resultSet.next()) {
          locationIdByKey.put(resultSet.getString(1), resultSet.getString(2));
        }
      }
    }
    return locationIdByKey;
  }

  private static Map<Identity, Target> existingTargets(
      Connection connection, Set<Identity> identities) throws SQLException {
    List<String> tenantIds = new ArrayList<>();
    List<String> types = new ArrayList<>();
    List<String> hashes = new ArrayList<>();
    List<String> locationIds = new ArrayList<>();
    for (Identity identity : identities) {
      tenantIds.add(identity.tenantId());
      types.add(identity.type());
      hashes.add(identity.hash());
      locationIds.add(identity.locationId());
    }
    Map<Identity, Target> targets = new HashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT f.finding_id, f.tenant_id, f.finding_type, f.finding_value_hash,"
                + " f.finding_location_id, f.finding_value, f.finding_value_display,"
                + " f.finding_name, f.finding_first_seen, f.finding_last_seen"
                + " FROM findings f JOIN unnest(?, ?, ?, ?) AS i(tenant_id, finding_type,"
                + " finding_value_hash, finding_location_id)"
                + " ON f.tenant_id = i.tenant_id AND f.finding_type = i.finding_type"
                + " AND f.finding_value_hash = i.finding_value_hash"
                + " AND f.finding_location_id = i.finding_location_id")) {
      statement.setArray(1, varchars(connection, tenantIds));
      statement.setArray(2, varchars(connection, types));
      statement.setArray(3, varchars(connection, hashes));
      statement.setArray(4, varchars(connection, locationIds));
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          Target target = new Target(resultSet.getString(1), true);
          target.value = resultSet.getString(6);
          target.valueDisplay = resultSet.getString(7);
          target.name = resultSet.getString(8);
          target.firstSeen = resultSet.getTimestamp(9);
          target.lastSeen = resultSet.getTimestamp(10);
          targets.put(
              new Identity(
                  resultSet.getString(2),
                  resultSet.getString(3),
                  resultSet.getString(4),
                  resultSet.getString(5)),
              target);
        }
      }
    }
    return targets;
  }

  private static void writeTargets(Connection connection, Map<Identity, Target> targets)
      throws SQLException {
    try (PreparedStatement insert =
            connection.prepareStatement(
                "INSERT INTO findings (finding_id, tenant_id, finding_type, finding_value,"
                    + " finding_value_display, finding_name, finding_value_hash,"
                    + " finding_location_id, finding_created_at, finding_updated_at,"
                    + " finding_first_seen, finding_last_seen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        PreparedStatement update =
            connection.prepareStatement(
                "UPDATE findings SET finding_value = ?, finding_value_display = ?,"
                    + " finding_name = ?, finding_first_seen = ?, finding_last_seen = ?"
                    + " WHERE finding_id = ?")) {
      for (Map.Entry<Identity, Target> entry : targets.entrySet()) {
        Identity identity = entry.getKey();
        Target target = entry.getValue();
        if (target.existing) {
          update.setString(1, target.value);
          update.setString(2, target.valueDisplay);
          update.setString(3, target.name);
          update.setTimestamp(4, target.firstSeen);
          update.setTimestamp(5, target.lastSeen);
          update.setString(6, target.id);
          update.addBatch();
        } else {
          insert.setString(1, target.id);
          insert.setString(2, identity.tenantId());
          insert.setString(3, identity.type());
          insert.setString(4, target.value);
          insert.setString(5, target.valueDisplay);
          insert.setString(6, target.name);
          insert.setString(7, identity.hash());
          insert.setString(8, identity.locationId());
          insert.setTimestamp(9, target.createdAt);
          insert.setTimestamp(10, target.updatedAt);
          insert.setTimestamp(11, target.firstSeen);
          insert.setTimestamp(12, target.lastSeen);
          insert.addBatch();
        }
      }
      insert.executeBatch();
      update.executeBatch();
    }
  }

  // On a merge, the occurrence already on the target keeps its field; in the batch, the first row
  private static void copyOccurrences(Connection connection, Array oldIds, Array targetIds)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
                + " finding_occurrence_inject_id, finding_occurrence_date,"
                + " finding_occurrence_field, finding_occurrence_created_at)"
                + " SELECT gen_random_uuid()::text, m.target_id, o.finding_occurrence_inject_id,"
                + " o.finding_occurrence_date, o.finding_occurrence_field, now()"
                + " FROM unnest(?, ?) WITH ORDINALITY AS m(old_id, target_id, ord)"
                + " JOIN finding_occurrences o ON o.finding_occurrence_finding_id = m.old_id"
                + " ORDER BY m.ord"
                + " ON CONFLICT (finding_occurrence_finding_id, finding_occurrence_inject_id)"
                + " DO NOTHING")) {
      statement.setArray(1, oldIds);
      statement.setArray(2, targetIds);
      statement.executeUpdate();
    }
  }

  private static void copyLinks(
      Connection connection, String table, String column, Array oldIds, Array targetIds)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT DISTINCT m.target_id, l."
                + column
                + " FROM unnest(?, ?) AS m(old_id, target_id) JOIN "
                + table
                + " l ON l.finding_id = m.old_id ON CONFLICT DO NOTHING")) {
      statement.setArray(1, oldIds);
      statement.setArray(2, targetIds);
      statement.executeUpdate();
    }
  }

  private static void linkLocations(
      Connection connection, String table, String column, List<String[]> links)
      throws SQLException {
    if (links.isEmpty()) {
      return;
    }
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO "
                + table
                + " (finding_id, "
                + column
                + ") SELECT DISTINCT * FROM unnest(?, ?) ON CONFLICT DO NOTHING")) {
      statement.setArray(1, varchars(connection, links.stream().map(link -> link[0]).toList()));
      statement.setArray(2, varchars(connection, links.stream().map(link -> link[1]).toList()));
      statement.executeUpdate();
    }
  }

  // A phishing result follows the row to its first finding, and to its occurrence there
  private static void moveAndDelete(Connection connection, Map<String, String> firstTargetByRow)
      throws SQLException {
    Array oldIds = varchars(connection, firstTargetByRow.keySet());
    try (PreparedStatement results =
            connection.prepareStatement(
                "UPDATE phishing_results pr SET phishing_result_finding = m.target_id,"
                    + " phishing_result_finding_occurrence = ("
                    + "SELECT moved.finding_occurrence_id FROM finding_occurrences previous"
                    + " JOIN finding_occurrences moved"
                    + " ON moved.finding_occurrence_inject_id = previous.finding_occurrence_inject_id"
                    + " AND moved.finding_occurrence_finding_id = m.target_id"
                    + " WHERE previous.finding_occurrence_id"
                    + " = pr.phishing_result_finding_occurrence)"
                    + " FROM unnest(?, ?) AS m(old_id, target_id)"
                    + " WHERE pr.phishing_result_finding = m.old_id");
        PreparedStatement delete =
            connection.prepareStatement("DELETE FROM findings WHERE finding_id = ANY (?)")) {
      results.setArray(1, oldIds);
      results.setArray(2, varchars(connection, firstTargetByRow.values()));
      results.executeUpdate();
      delete.setArray(1, oldIds);
      delete.executeUpdate();
    }
  }

  private static String assetId(LocationIdentity location) {
    return location instanceof AssetLocation(String id) ? id : null;
  }

  private static String userId(LocationIdentity location) {
    return location instanceof UserLocation(String id) ? id : null;
  }

  private static Array varchars(Connection connection, Collection<String> values)
      throws SQLException {
    return connection.createArrayOf("varchar", values.toArray());
  }

  private static Timestamp min(Timestamp current, Timestamp candidate) {
    return current == null || candidate.before(current) ? candidate : current;
  }

  private static Timestamp max(Timestamp current, Timestamp candidate) {
    return current == null || candidate.after(current) ? candidate : current;
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
