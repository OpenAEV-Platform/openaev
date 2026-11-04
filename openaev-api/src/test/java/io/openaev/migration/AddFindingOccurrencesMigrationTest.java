package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.finding.identity.FindingLocation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the migration that creates {@code finding_occurrences} (#7848): every existing finding
 * gets one occurrence with its own id, its inject, the inject's sent date and its field, phishing
 * results move to that occurrence, a re-run adds nothing, and the location table and the new
 * {@code findings} columns accept the stable shape.
 *
 * <p>Rows are seeded and read over raw JDBC, like the migration itself runs, so the statement
 * inspector does not apply. {@code @Transactional} so the seeded rows roll back.
 */
@Transactional
@DisplayName("Add finding occurrences migration")
class AddFindingOccurrencesMigrationTest extends IntegrationTest {

  private static final String TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private V6_20261103100000000__Add_finding_occurrences migration;

  private String injectId;

  @BeforeEach
  void seedAnInject() {
    injectId = insertInject();
  }

  @Nested
  @DisplayName("When findings have no occurrence yet")
  class WhenFindingsHaveNoOccurrenceYet {

    @Test
    @DisplayName("Should give each finding one occurrence of its own, with its inject and field")
    void given_aFinding_should_createItsOccurrence() {
      // Arrange
      String findingId = insertFinding(injectId, "cve");

      // Act
      runMigration();

      // Assert
      List<String> occurrences = occurrencesOf(findingId);
      assertThat(occurrences).hasSize(1);
      String occurrenceId = occurrences.getFirst();
      assertThat(occurrenceId).isNotEqualTo(findingId);
      assertThat(occurrenceColumn(occurrenceId, "finding_occurrence_inject_id")).isEqualTo(injectId);
      assertThat(occurrenceColumn(occurrenceId, "finding_occurrence_field")).isEqualTo("cve");
    }

    @Test
    @DisplayName("Should date the occurrence with the sent date of its inject")
    void given_anInjectWithAStatus_should_dateTheOccurrenceWithItsSentDate() {
      // Arrange
      Instant sentDate = Instant.parse("2026-10-01T08:30:00Z");
      insertStatus(injectId, sentDate);
      String findingId = insertFinding(injectId, "cve");

      // Act
      runMigration();

      // Assert
      Timestamp date =
          withConnection(
              connection ->
                  queryTimestamp(
                      connection,
                      "SELECT finding_occurrence_date FROM finding_occurrences"
                          + " WHERE finding_occurrence_finding_id = ?",
                      findingId));
      assertThat(date.toInstant()).isEqualTo(sentDate);
    }

    @Test
    @DisplayName("Should date the occurrence at the finding creation without inject status")
    void given_anInjectWithoutStatus_should_dateTheOccurrenceWithTheFindingCreationDate() {
      // Arrange
      Instant createdAt = Instant.parse("2026-04-15T10:00:00Z");
      String findingId = insertFinding(injectId, "cve");
      update(
          "UPDATE findings SET finding_created_at = ? WHERE finding_id = ?",
          Timestamp.from(createdAt),
          findingId);

      // Act
      runMigration();

      // Assert
      Timestamp date =
          withConnection(
              connection ->
                  queryTimestamp(
                      connection,
                      "SELECT finding_occurrence_date FROM finding_occurrences"
                          + " WHERE finding_occurrence_finding_id = ?",
                      findingId));
      assertThat(date.toInstant()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Should point a phishing result at the occurrence of its finding")
    void given_aPhishingResultOnAFinding_should_linkItToTheOccurrence() {
      // Arrange
      String findingId = insertFinding(injectId, "phishing_credentials");
      String resultId = insertPhishingResult(injectId, findingId);

      // Act
      runMigration();

      // Assert
      String linked =
          withConnection(
              connection ->
                  queryString(
                      connection,
                      "SELECT phishing_result_finding_occurrence FROM phishing_results"
                          + " WHERE phishing_result_id = ?",
                      resultId));
      assertThat(linked).isEqualTo(occurrencesOf(findingId).getFirst());
    }

    @Test
    @DisplayName("Should give an occurrence to more findings than one batch holds")
    void given_moreFindingsThanOneBatch_should_createAllOccurrences() {
      // Arrange
      withConnection(
          connection -> {
            try (PreparedStatement statement =
                connection.prepareStatement(
                    "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                        + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
                        + " SELECT gen_random_uuid(), 'field', 'Text', 'bulk-' || n, ?, now(),"
                        + " now(), ? FROM generate_series(1, 2500) AS n")) {
              statement.setString(1, injectId);
              statement.setString(2, TENANT);
              return statement.executeUpdate();
            }
          });

      // Act
      runMigration();

      // Assert
      Long withoutOccurrence =
          withConnection(
              connection -> {
                try (PreparedStatement statement =
                    connection.prepareStatement(
                        "SELECT count(*) FROM findings f WHERE f.finding_inject_id = ? AND NOT"
                            + " EXISTS (SELECT 1 FROM finding_occurrences o"
                            + " WHERE o.finding_occurrence_finding_id = f.finding_id)")) {
                  statement.setString(1, injectId);
                  try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                  }
                }
              });
      assertThat(withoutOccurrence).isZero();
    }
  }

  @Nested
  @DisplayName("When the migration runs again")
  class WhenTheMigrationRunsAgain {

    @Test
    @DisplayName("Should keep a single occurrence per finding")
    void given_anAlreadyMigratedFinding_should_keepOneOccurrence() {
      // Arrange
      String findingId = insertFinding(injectId, "cve");
      runMigration();
      String occurrenceId = occurrencesOf(findingId).getFirst();

      // Act
      runMigration();

      // Assert
      assertThat(occurrencesOf(findingId)).containsExactly(occurrenceId);
    }
  }

  @Nested
  @DisplayName("When a finding is written in the stable shape")
  class WhenAFindingIsWrittenInTheStableShape {

    @Test
    @DisplayName("Should accept a finding without inject nor field, pointing at its location")
    void given_noInjectNorField_should_storeTheFindingOnItsLocation() {
      // Arrange
      runMigration();
      String locationId = insertLocation(insertAsset());

      // Act
      String findingId = insertStableFinding(locationId);

      // Assert
      assertThat(findingColumn(findingId, "finding_location_id")).isEqualTo(locationId);
    }

    @Test
    @DisplayName("Should compute the location key the identity code computes for an asset")
    void given_anAssetLocation_should_matchTheIdentityKey() {
      // Arrange
      runMigration();
      String assetId = insertAsset();

      // Act
      String locationId = insertLocation(assetId);

      // Assert
      assertThat(locationKey(locationId)).isEqualTo(FindingLocation.of(assetId, null).key());
    }

    @Test
    @DisplayName("Should refuse a second location for the same asset")
    void given_anAssetAlreadyLocated_should_refuseASecondLocation() {
      // Arrange
      runMigration();
      String assetId = insertAsset();
      insertLocation(assetId);

      // Act and Assert
      assertThatThrownBy(() -> insertLocation(assetId)).isInstanceOf(RuntimeException.class);
    }
  }

  // -- PRIVATE --

  private interface JdbcWork<T> {
    T run(Connection connection) throws Exception;
  }

  private <T> T withConnection(JdbcWork<T> work) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try {
                return work.run(connection);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private void runMigration() {
    withConnection(
        connection -> {
          migration.migrate(
              new Context() {
                @Override
                public Configuration getConfiguration() {
                  return null;
                }

                @Override
                public Connection getConnection() {
                  return connection;
                }
              });
          return null;
        });
  }

  private String insertInject() {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'occurrence inject', now(), now(), 0, false, true, ?)",
        id,
        TENANT);
    return id;
  }

  private void insertStatus(String injectId, Instant sentDate) {
    withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT INTO injects_statuses (status_id, status_inject, status_name,"
                      + " tracking_sent_date) VALUES (?, ?, 'SUCCESS', ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, injectId);
            statement.setTimestamp(3, Timestamp.from(sentDate));
            return statement.executeUpdate();
          }
        });
  }

  /** A finding as the current write paths store it: one inject, one field. */
  private String insertFinding(String injectId, String field) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, ?, 'Text', ?, ?, now(), now(), ?)",
        id,
        field,
        "value-" + id,
        injectId,
        TENANT);
    return id;
  }

  /** A finding as the future writer stores it: no inject, no field, a location. */
  private String insertStableFinding(String locationId) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO findings (finding_id, finding_type, finding_value, finding_location_id,"
            + " finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, 'Text', ?, ?, now(), now(), ?)",
        id,
        "stable-" + id,
        locationId,
        TENANT);
    return id;
  }

  private String insertLocation(String assetId) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO finding_locations (finding_location_id, finding_location_asset_id)"
            + " VALUES (?, ?)",
        id,
        assetId);
    return id;
  }

  private String locationKey(String locationId) {
    return withConnection(
        connection ->
            queryString(
                connection,
                "SELECT finding_location_key FROM finding_locations WHERE finding_location_id = ?",
                locationId));
  }

  private String insertAsset() {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, 'occurrence-asset', 'Endpoint', now(), now(), ?, 'occurrence-asset',"
            + " 'Linux', 'x86_64')",
        id,
        TENANT);
    return id;
  }

  private String insertPhishingResult(String injectId, String findingId) {
    String id = UUID.randomUUID().toString();
    update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_finding, phishing_result_created_at,"
            + " phishing_result_updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
        id,
        TENANT,
        "token-" + id,
        injectId,
        findingId);
    return id;
  }

  private List<String> occurrencesOf(String findingId) {
    return withConnection(
        connection -> {
          try (PreparedStatement statement =
              connection.prepareStatement(
                  "SELECT finding_occurrence_id FROM finding_occurrences"
                      + " WHERE finding_occurrence_finding_id = ?")) {
            statement.setString(1, findingId);
            List<String> ids = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
              while (resultSet.next()) {
                ids.add(resultSet.getString(1));
              }
            }
            return ids;
          }
        });
  }

  private String occurrenceColumn(String occurrenceId, String column) {
    return withConnection(
        connection ->
            queryString(
                connection,
                "SELECT " + column + " FROM finding_occurrences WHERE finding_occurrence_id = ?",
                occurrenceId));
  }

  private String findingColumn(String findingId, String column) {
    return withConnection(
        connection ->
            queryString(
                connection, "SELECT " + column + " FROM findings WHERE finding_id = ?", findingId));
  }

  private void update(String sql, Object... parameters) {
    withConnection(
        connection -> {
          try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
              statement.setObject(i + 1, parameters[i]);
            }
            return statement.executeUpdate();
          }
        });
  }

  private static String queryString(Connection connection, String sql, String parameter)
      throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, parameter);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getString(1);
      }
    }
  }

  private static Timestamp queryTimestamp(Connection connection, String sql, String parameter)
      throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, parameter);
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getTimestamp(1);
      }
    }
  }
}
