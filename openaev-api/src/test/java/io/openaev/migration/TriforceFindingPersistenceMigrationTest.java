package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Triforce finding persistence migration")
class TriforceFindingPersistenceMigrationTest extends IntegrationTest {

  private static final Instant FIRST_OBSERVED_AT = Instant.parse("2026-09-15T08:00:00Z");
  private static final Instant LAST_OBSERVED_AT = Instant.parse("2026-09-16T09:30:00Z");

  @Autowired private V6_20260917091900000__Add_triforce_finding_persistence migration;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TagComposer tagComposer;

  @BeforeEach
  void resetComposers() {
    injectComposer.reset();
    findingComposer.reset();
    endpointComposer.reset();
    tagComposer.reset();
  }

  @Nested
  @DisplayName("Legacy backfill")
  class LegacyBackfill {

    @Test
    @DisplayName("Fans one legacy multi-asset finding out to occurrences under one stable finding")
    void given_multi_asset_finding_should_fan_out_occurrences_per_location() {
      // Arrange
      Endpoint firstEndpoint = endpoint("first", "host-a");
      Endpoint secondEndpoint = endpoint("second", "host-b");
      Finding finding = FindingFixture.createDefaultTextFindingWithRandomValue();
      finding.setField("target_host");
      finding.setValue("host-a");
      persist(
          InjectFixture.getDefaultInject(),
          findingComposer
              .forFinding(finding)
              .withEndpoint(endpointComposer.forEndpoint(firstEndpoint))
              .withEndpoint(endpointComposer.forEndpoint(secondEndpoint)));

      // Act
      runMigration();

      // Assert
      List<Object[]> occurrences = occurrences(finding.getId());
      assertThat(occurrences).hasSize(2);
      assertThat(occurrences).extracting(row -> row[0]).containsOnly("ASSET");
      assertThat(occurrences)
          .extracting(row -> row[1])
          .containsExactlyInAnyOrder(firstEndpoint.getId(), secondEndpoint.getId());
      assertThat(occurrences)
          .extracting(row -> row[2])
          .containsExactlyInAnyOrder("TARGET", "EXECUTOR");
      assertThat(occurrences).extracting(row -> row[3]).containsOnly("GROUP");
      assertThat(occurrences).extracting(row -> row[4]).containsOnly(stableId(finding.getId()));
      assertThat(occurrences).extracting(row -> row[5]).containsOnly(finding.getId());
    }

    @Test
    @DisplayName("Fans OCSF resources out to occurrences under one stable finding")
    void given_ocsf_multi_resource_finding_should_fan_out_resource_occurrences() {
      // Arrange
      Finding finding =
          ocsfFinding(
              """
              {
                "metadata": {"product": {"uid": "PROWLER"}, "uid": "scan-1"},
                "status_code": "FAIL",
                "severity_id": 4,
                "finding_info": {"uid": "source-1", "title": "Public buckets"},
                "resources": [
                  {"uid": "bucket-a", "name": "Bucket A", "type": "S3 Bucket"},
                  {"uid": "bucket-b", "name": "Bucket B", "type": "S3 Bucket"}
                ]
              }
              """);
      persist(InjectFixture.getDefaultInject(), findingComposer.forFinding(finding));

      // Act
      runMigration();

      // Assert
      List<Object[]> occurrences = occurrences(finding.getId());
      assertThat(occurrences).hasSize(2);
      assertThat(occurrences).extracting(row -> row[0]).containsOnly("RESOURCE");
      assertThat(occurrences)
          .extracting(row -> row[1])
          .containsExactlyInAnyOrder("bucket-a", "bucket-b");
      assertThat(occurrences).extracting(row -> row[4]).containsOnly(stableId(finding.getId()));
      assertThat(occurrences)
          .extracting(row -> row[6])
          .containsExactlyInAnyOrder("bucket-a", "bucket-b");
      assertThat(occurrences).extracting(row -> row[7]).containsOnly(4);
      assertThat(stableAggregationCategory(finding.getId())).isEqualTo("CONFIGURATION_POSTURE");
    }

    @Test
    @DisplayName("Groups different locations under the same stable finding")
    void given_same_source_type_and_value_on_different_assets_should_groupLocations() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      Finding first = FindingFixture.createDefaultTextFindingWithRandomValue();
      first.setValue("same-value");
      Finding second = FindingFixture.createDefaultTextFindingWithRandomValue();
      second.setValue("same-value");
      Endpoint firstEndpoint = endpoint("first", "first-host");
      Endpoint secondEndpoint = endpoint("second", "second-host");
      InjectComposer.Composer composer =
          injectComposer
              .forInject(inject)
              .withFinding(
                  findingComposer
                      .forFinding(first)
                      .withEndpoint(endpointComposer.forEndpoint(firstEndpoint)))
              .withFinding(
                  findingComposer
                      .forFinding(second)
                      .withEndpoint(endpointComposer.forEndpoint(secondEndpoint)));
      composer.persist();
      entityManager.flush();

      // Act
      runMigration();

      // Assert
      assertThat(stableId(first.getId())).isEqualTo(stableId(second.getId()));
      assertThat(locationKeys(stableId(first.getId())))
          .containsExactlyInAnyOrder(firstEndpoint.getId(), secondEndpoint.getId());
    }

    @Test
    @DisplayName("Derives first, last, and lifecycle from ordered occurrence observation times")
    void given_repeated_observations_should_use_created_at_and_latest_observation_lifecycle() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      Finding oldManual =
          ocsfFinding(
              """
              {
                "metadata": {"product": {"name": "Prowler"}},
                "status_code": "MANUAL",
                "finding_info": {"title": "Public bucket"}
              }
              """);
      Finding latestFail =
          ocsfFinding(
              """
              {
                "metadata": {"product": {"uid": "prowler"}},
                "status_code": "FAIL",
                "finding_info": {"title": "Public bucket"}
              }
              """);
      injectComposer
          .forInject(inject)
          .withFinding(findingComposer.forFinding(oldManual))
          .withFinding(findingComposer.forFinding(latestFail))
          .persist();
      entityManager.flush();
      setDates(oldManual.getId(), FIRST_OBSERVED_AT, Instant.parse("2030-01-01T00:00:00Z"));
      setDates(latestFail.getId(), LAST_OBSERVED_AT, Instant.parse("2020-01-01T00:00:00Z"));

      // Act
      runMigration();

      // Assert
      Object[] stable = stableState(oldManual.getId());
      assertThat(toInstant(stable[0])).isEqualTo(FIRST_OBSERVED_AT);
      assertThat(toInstant(stable[1])).isEqualTo(LAST_OBSERVED_AT);
      assertThat(stable[2]).isEqualTo("ACTIVE");
      assertThat(observedAt(oldManual.getId())).containsOnly(FIRST_OBSERVED_AT);
      assertThat(observedAt(latestFail.getId())).containsOnly(LAST_OBSERVED_AT);
    }

    @Test
    @DisplayName("Consolidates legacy tags on the stable finding")
    void given_grouped_legacy_findings_should_unionTagsOnStableFinding() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      Finding first = FindingFixture.createDefaultTextFindingWithRandomValue();
      first.setValue("same-value");
      Finding second = FindingFixture.createDefaultTextFindingWithRandomValue();
      second.setValue("same-value");
      var firstTag = tagComposer.forTag(TagFixture.getTagWithText("first-tag"));
      var secondTag = tagComposer.forTag(TagFixture.getTagWithText("second-tag"));
      injectComposer
          .forInject(inject)
          .withFinding(findingComposer.forFinding(first).withTag(firstTag))
          .withFinding(findingComposer.forFinding(second).withTag(secondTag))
          .persist();
      entityManager.flush();

      // Act
      runMigration();

      // Assert
      assertThat(stableId(first.getId())).isEqualTo(stableId(second.getId()));
      assertThat(stableTags(stableId(first.getId())))
          .containsExactlyInAnyOrder(firstTag.get().getId(), secondTag.get().getId());
    }

    @Test
    @DisplayName("Can be re-run without duplicating location occurrences")
    void given_backfilled_locations_should_be_idempotent() {
      // Arrange
      Finding finding = FindingFixture.createDefaultTextFindingWithRandomValue();
      persist(InjectFixture.getDefaultInject(), findingComposer.forFinding(finding));

      // Act
      runMigration();
      assertThatCode(TriforceFindingPersistenceMigrationTest.this::runMigration)
          .doesNotThrowAnyException();

      // Assert
      assertThat(occurrences(finding.getId())).hasSize(1);
    }
  }

  private Endpoint endpoint(String name, String hostname) {
    Endpoint endpoint = EndpointFixture.createEndpoint(name);
    endpoint.setHostname(hostname);
    return endpoint;
  }

  private Finding ocsfFinding(String rawPayload) {
    Finding finding = FindingFixture.createDefaultTextFindingWithRandomValue();
    finding.setType(ContractOutputType.OCSF);
    finding.setField("ocsf");
    finding.setValue("Public bucket");
    finding.setRawData(rawPayload);
    finding.setSeverity("High");
    return finding;
  }

  private void persist(Inject inject, FindingComposer.Composer finding) {
    injectComposer.forInject(inject).withFinding(finding).persist();
    entityManager.flush();
  }

  private void setDates(String findingId, Instant createdAt, Instant updatedAt) {
    entityManager
        .createNativeQuery(
            """
            UPDATE findings
            SET finding_created_at = :createdAt, finding_updated_at = :updatedAt
            WHERE finding_id = :findingId
            """)
        .setParameter("createdAt", createdAt)
        .setParameter("updatedAt", updatedAt)
        .setParameter("findingId", findingId)
        .executeUpdate();
  }

  @SuppressWarnings("unchecked")
  private List<Object[]> occurrences(String legacyFindingId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT finding_occurrence_location_type, finding_occurrence_location_key,
                   finding_occurrence_target_role, finding_occurrence_evidence_scope,
                   finding_occurrence_stable_finding_id, finding_occurrence_migrated_from,
                   finding_occurrence_resource, finding_occurrence_observed_severity_id
            FROM finding_occurrences
            WHERE finding_occurrence_migrated_from = :legacyFindingId
            ORDER BY finding_occurrence_location_key
            """)
        .setParameter("legacyFindingId", legacyFindingId)
        .getResultList();
  }

  private String stableId(String legacyFindingId) {
    return (String)
        entityManager
            .createNativeQuery(
                """
                SELECT finding_occurrence_stable_finding_id
                FROM finding_occurrences
                WHERE finding_occurrence_migrated_from = :legacyFindingId
                LIMIT 1
                """)
            .setParameter("legacyFindingId", legacyFindingId)
            .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> locationKeys(String stableFindingId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT finding_occurrence_location_key
            FROM finding_occurrences
            WHERE finding_occurrence_stable_finding_id = :stableFindingId
            """,
            String.class)
        .setParameter("stableFindingId", stableFindingId)
        .getResultList();
  }

  private Object[] stableState(String legacyFindingId) {
    return (Object[])
        entityManager
            .createNativeQuery(
                """
                SELECT sf.stable_finding_first_seen, sf.stable_finding_last_seen,
                       sf.stable_finding_lifecycle
                FROM stable_findings sf
                JOIN finding_occurrences fo
                  ON fo.finding_occurrence_stable_finding_id = sf.stable_finding_id
                WHERE fo.finding_occurrence_migrated_from = :legacyFindingId
                LIMIT 1
                """)
            .setParameter("legacyFindingId", legacyFindingId)
            .getSingleResult();
  }

  private String stableAggregationCategory(String legacyFindingId) {
    return (String)
        entityManager
            .createNativeQuery(
                """
                SELECT sf.stable_finding_aggregation_category
                FROM stable_findings sf
                JOIN finding_occurrences fo
                  ON fo.finding_occurrence_stable_finding_id = sf.stable_finding_id
                WHERE fo.finding_occurrence_migrated_from = :legacyFindingId
                LIMIT 1
                """)
            .setParameter("legacyFindingId", legacyFindingId)
            .getSingleResult();
  }

  @SuppressWarnings("unchecked")
  private List<String> stableTags(String stableFindingId) {
    return entityManager
        .createNativeQuery(
            """
            SELECT tag_id
            FROM stable_findings_tags
            WHERE stable_finding_id = :stableFindingId
            """,
            String.class)
        .setParameter("stableFindingId", stableFindingId)
        .getResultList();
  }

  @SuppressWarnings("unchecked")
  private List<Instant> observedAt(String legacyFindingId) {
    List<Object> values =
        entityManager
            .createNativeQuery(
                """
                SELECT finding_occurrence_observed_at
                FROM finding_occurrences
                WHERE finding_occurrence_migrated_from = :legacyFindingId
                """)
            .setParameter("legacyFindingId", legacyFindingId)
            .getResultList();
    return values.stream().map(this::toInstant).toList();
  }

  private Instant toInstant(Object value) {
    if (value instanceof java.sql.Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (value instanceof java.time.OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    return (Instant) value;
  }

  private void runMigration() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }
}
