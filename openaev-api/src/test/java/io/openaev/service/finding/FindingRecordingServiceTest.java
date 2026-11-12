package io.openaev.service.finding;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.finding.identity.DetectedFinding;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.finding.identity.LocationIdentity.UserLocation;
import io.openaev.rest.finding.FindingService;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Not {@code @Transactional}: the writer opens its own {@code REQUIRES_NEW} transaction, so the
 * seeded rows must be committed for it to see them. They are removed after each test.
 */
@WithMockUser(isAdmin = true)
@RecordApplicationEvents
@DisplayName("FindingRecordingService.recordFindingsByInject")
class FindingRecordingServiceTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final Instant MONDAY = Instant.parse("2026-11-02T09:00:00Z");
  private static final Instant TUESDAY = Instant.parse("2026-11-03T09:00:00Z");
  private static final String CVE = "CVE-2024-1234";

  @Autowired private FindingRecordingService findingRecordingService;
  @Autowired private FindingService findingService;
  @Autowired private DataSource dataSource;
  @Autowired private ApplicationEvents applicationEvents;

  private JdbcTemplate jdbc;
  private String tenantId;
  private TxCtx ctx;
  private Inject mondayInject;
  private Inject tuesdayInject;
  private String assetA;
  private String assetB;
  private String userId;
  private String tagX;
  private String tagY;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "record-detections-" + tenantId);
    ctx = TxCtx.forTenant(tenantId);
    mondayInject = seedInject(MONDAY);
    tuesdayInject = seedInject(TUESDAY);
    assetA = seedAsset();
    assetB = seedAsset();
    userId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        userId,
        "record-detections-" + userId + "@openaev.io");
    tagX = seedTag();
    tagY = seedTag();
  }

  @AfterEach
  void sweep() {
    String ofTenant = " WHERE finding_id IN (SELECT finding_id FROM findings WHERE tenant_id = ?)";
    jdbc.update("DELETE FROM findings_assets" + ofTenant, tenantId);
    jdbc.update("DELETE FROM findings_users" + ofTenant, tenantId);
    jdbc.update("DELETE FROM findings_tags" + ofTenant, tenantId);
    jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tags WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @Nested
  @DisplayName("When a finding is detected for the first time")
  class WhenDetectedForTheFirstTime {

    @Test
    @DisplayName("should create one finding at its location and its occurrence on the inject")
    void given_aNewDetection_should_createOneFindingAndItsOccurrence() {
      // -- ACT --
      String findingId =
          record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)))
              .get(0)
              .getId();

      // -- ASSERT --
      assertThat(countFindings()).isEqualTo(1);
      assertThat(locationKeyOf(findingId)).isEqualTo("asset:" + assetA);
      assertThat(
              jdbc.queryForMap(
                  "SELECT finding_value_hash, finding_inject_id, finding_field FROM findings"
                      + " WHERE finding_id = ?",
                  findingId))
          .containsEntry("finding_inject_id", null)
          .containsEntry("finding_field", null)
          .extractingByKey("finding_value_hash")
          .isNotNull();
      assertThat(seenOf(findingId)).containsExactly(MONDAY, MONDAY);
      assertThat(countOccurrences(findingId)).isEqualTo(1);
      assertThat(occurrenceFieldOf(findingId, mondayInject)).isEqualTo("cve");
      assertThat(
              count(
                  "SELECT count(*) FROM findings_assets WHERE finding_id = ? AND asset_id = ?",
                  findingId,
                  assetA))
          .as("readers still go through findings_assets until chunk 7")
          .isEqualTo(1);
    }

    @Test
    @DisplayName("should resolve a user and an unknown location to their own keys")
    void given_userAndUnknownLocations_should_resolveTheirKeys() {
      // -- ACT --
      List<Finding> recorded =
          record(
              mondayInject,
              detection(
                  ContractOutputType.Credentials,
                  "jdoe:secret",
                  "credentials",
                  "jdoe",
                  new UserLocation(userId)),
              detection(
                  ContractOutputType.Text, "some output", "text", null, UnknownLocation.INSTANCE));

      // -- ASSERT --
      assertThat(recorded)
          .extracting(finding -> locationKeyOf(finding.getId()))
          .containsExactlyInAnyOrder("user:" + userId, "unknown");
      assertThat(
              count(
                  "SELECT count(*) FROM findings_users u JOIN findings f"
                      + " ON f.finding_id = u.finding_id WHERE f.tenant_id = ? AND u.user_id = ?",
                  tenantId,
                  userId))
          .isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("When the detected asset is not one of the tenant's")
  class WhenTheAssetIsNotTheTenants {

    @Test
    @DisplayName("given a missing or a foreign asset, should keep the finding at no location")
    void given_aMissingOrAForeignAsset_should_keepTheFindingAtNoLocation() {
      // -- ARRANGE --
      String otherTenantId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
              + " VALUES (?, ?, now(), now())",
          otherTenantId,
          "record-detections-" + otherTenantId);
      String foreignAsset = seedAsset(otherTenantId);

      try {
        // -- ACT --
        List<Finding> recorded =
            record(
                mondayInject,
                cve(CVE, "cve", "OpenSSL", new AssetLocation(UUID.randomUUID().toString())),
                cve(CVE, "cve", "OpenSSL", new AssetLocation(foreignAsset)));

        // -- ASSERT --
        assertThat(recorded).hasSize(1);
        assertThat(locationKeyOf(recorded.get(0).getId())).isEqualTo("unknown");
        assertThat(
                count(
                    "SELECT count(*) FROM findings_assets WHERE finding_id = ?",
                    recorded.get(0).getId()))
            .isZero();
      } finally {
        jdbc.update("DELETE FROM assets WHERE tenant_id = ?", otherTenantId);
        jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", otherTenantId);
      }
    }
  }

  @Nested
  @DisplayName("When a finding is detected again")
  class WhenDetectedAgain {

    @Test
    @DisplayName("by the same inject, should add nothing")
    void given_theSameInject_should_addNothing() {
      // -- ARRANGE --
      Finding first =
          record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))).get(0);

      // -- ACT --
      Finding second =
          record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))).get(0);

      // -- ASSERT --
      assertThat(second.getId()).isEqualTo(first.getId());
      assertThat(countOccurrences(first.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("by a later inject, should add an occurrence and move last seen")
    void given_aLaterInject_should_addAnOccurrenceAndMoveLastSeen() {
      // -- ARRANGE --
      Finding monday =
          record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))).get(0);

      // -- ACT --
      Finding tuesday =
          record(tuesdayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))).get(0);

      // -- ASSERT --
      assertThat(tuesday.getId()).isEqualTo(monday.getId());
      assertThat(countOccurrences(monday.getId())).isEqualTo(2);
      assertThat(seenOf(monday.getId())).containsExactly(MONDAY, TUESDAY);
    }

    @Test
    @DisplayName("by an earlier inject recorded late, should move first seen back")
    void given_anEarlierInjectRecordedLate_should_moveFirstSeenBack() {
      // -- ARRANGE --
      Finding tuesday =
          record(tuesdayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))).get(0);

      // -- ACT --
      record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)));

      // -- ASSERT --
      assertThat(seenOf(tuesday.getId())).containsExactly(MONDAY, TUESDAY);
    }

    @Test
    @DisplayName("with a value written differently, should keep one finding and the latest value")
    void given_aValueWrittenDifferently_should_keepOneFindingAndTheLatestValue() {
      // -- ARRANGE --
      Finding monday =
          record(mondayInject, cve("cve-2024-1234", "cve", "old name", new AssetLocation(assetA)))
              .get(0);

      // -- ACT --
      Finding tuesday =
          record(tuesdayInject, cve(CVE, "cve", "new name", new AssetLocation(assetA))).get(0);

      // -- ASSERT --
      assertThat(tuesday.getId()).isEqualTo(monday.getId());
      assertThat(
              jdbc.queryForMap(
                  "SELECT finding_value, finding_name FROM findings WHERE finding_id = ?",
                  monday.getId()))
          .containsEntry("finding_value", CVE)
          .containsEntry("finding_name", "new name");
    }

    @Test
    @DisplayName("on another location, should create another finding")
    void given_anotherLocation_should_createAnotherFinding() {
      // -- ACT --
      List<Finding> recorded =
          record(
              mondayInject,
              cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)),
              cve(CVE, "cve", "OpenSSL", new AssetLocation(assetB)));

      // -- ASSERT --
      assertThat(recorded).extracting(Finding::getId).doesNotHaveDuplicates().hasSize(2);
      assertThat(countFindings()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("When one batch repeats a finding")
  class WhenOneBatchRepeatsAFinding {

    @Test
    @DisplayName("should merge it: latest name, first field, tags added up")
    void given_aBatchRepeatingAFinding_should_mergeIt() {
      // -- ACT --
      List<Finding> recorded =
          record(
              mondayInject,
              cve(CVE, "cve", "first name", new AssetLocation(assetA), tagX),
              cve(CVE, "scan_cves", "latest name", new AssetLocation(assetA), tagY));

      // -- ASSERT --
      assertThat(recorded).hasSize(1);
      String findingId = recorded.get(0).getId();
      assertThat(countFindings()).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT finding_name FROM findings WHERE finding_id = ?",
                  String.class,
                  findingId))
          .isEqualTo("latest name");
      assertThat(occurrenceFieldOf(findingId, mondayInject)).isEqualTo("cve");
      assertThat(
              jdbc.queryForList(
                  "SELECT tag_id FROM findings_tags WHERE finding_id = ?", String.class, findingId))
          .containsExactlyInAnyOrder(tagX, tagY);
    }
  }

  @Nested
  @DisplayName("When the scope names no single tenant")
  class WhenTheScopeNamesNoSingleTenant {

    @Test
    @DisplayName("should refuse the write, naming the inject")
    void given_noSingleTenantScope_should_refuseTheWrite() {
      // -- ACT & ASSERT --
      assertThatThrownBy(
              () ->
                  findingRecordingService.recordFindingsByInject(
                      TxCtx.missing(),
                      mondayInject,
                      List.of(cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)))))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(mondayInject.getId());
      assertThat(countFindings()).isZero();
    }
  }

  @Nested
  @DisplayName("Lifecycle events")
  class LifecycleEvents {

    @Test
    @DisplayName("should publish a create, nothing on a repeat, then an update on a new occurrence")
    void given_aNewThenRepeatedThenLaterDetection_should_publishCreateNothingThenUpdate() {
      // -- ACT --
      String findingId =
          record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)))
              .get(0)
              .getId();
      record(mondayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)));
      record(tuesdayInject, cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA)));

      // -- ASSERT --
      List<BaseEvent> events =
          applicationEvents.stream(BaseEvent.class)
              .filter(event -> event.getInstance() instanceof Finding)
              .toList();
      assertThat(events).extracting(BaseEvent::getType).containsExactly(DATA_PERSIST, DATA_UPDATE);
      assertThat(events).allMatch(event -> findingId.equals(event.getInstance().getId()));
    }
  }

  @Nested
  @DisplayName("When two injects detect the same finding at once")
  class WhenTwoInjectsDetectAtOnce {

    @Test
    @DisplayName("should create one finding with two occurrences")
    void given_twoInjectsAtOnce_should_createOneFinding() throws Exception {
      // -- ARRANGE --
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool =
          new DelegatingSecurityContextExecutorService(
              Executors.newFixedThreadPool(2), SecurityContextHolder.getContext());
      List<Future<List<Finding>>> results = new ArrayList<>();

      // -- ACT --
      try {
        for (Inject inject : List.of(mondayInject, tuesdayInject)) {
          results.add(
              pool.submit(
                  () -> {
                    start.await();
                    // Through FindingService: it retries the batch that loses the race
                    return findingService.recordFindingsByInject(
                        ctx,
                        inject,
                        List.of(cve(CVE, "cve", "OpenSSL", new AssetLocation(assetA))));
                  }));
        }
        start.countDown();
        String first = results.get(0).get(30, TimeUnit.SECONDS).get(0).getId();
        String second = results.get(1).get(30, TimeUnit.SECONDS).get(0).getId();

        // -- ASSERT --
        assertThat(second).isEqualTo(first);
        assertThat(countFindings()).isEqualTo(1);
        assertThat(countOccurrences(first)).isEqualTo(2);
      } finally {
        pool.shutdownNow();
      }
    }
  }

  @Nested
  @DisplayName("Statement count")
  class StatementCount {

    @Test
    @DisplayName("should not grow with the size of the batch")
    void given_aLargerBatch_should_issueTheSameNumberOfStatements() {
      // -- ARRANGE --
      record(
          tuesdayInject,
          detection(
              ContractOutputType.Text, "warm-up", "text", null, new AssetLocation(assetA), tagX));

      // -- ACT --
      long oneDetection = statementsToRecord(1);
      long thirtyDetections = statementsToRecord(30);

      // -- ASSERT --
      assertThat(thirtyDetections).isEqualTo(oneDetection);
    }

    private long statementsToRecord(int size) {
      List<DetectedFinding> batch = new ArrayList<>();
      for (int i = 0; i < size; i++) {
        batch.add(
            detection(
                ContractOutputType.Text,
                "value-" + UUID.randomUUID(),
                "text",
                null,
                new AssetLocation(assetA),
                tagX));
      }
      Statistics stats =
          entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
      stats.setStatisticsEnabled(true);
      stats.clear();
      findingRecordingService.recordFindingsByInject(ctx, mondayInject, batch);
      return stats.getPrepareStatementCount();
    }
  }

  // -- HELPERS --

  private List<Finding> record(Inject inject, DetectedFinding... detections) {
    return findingRecordingService.recordFindingsByInject(ctx, inject, List.of(detections));
  }

  private static DetectedFinding cve(
      String value, String field, String name, LocationIdentity location, String... tagIds) {
    return detection(ContractOutputType.CVE, value, field, name, location, tagIds);
  }

  private static DetectedFinding detection(
      ContractOutputType type,
      String value,
      String field,
      String name,
      LocationIdentity location,
      String... tagIds) {
    return new DetectedFinding(type, value, field, name, location, Set.of(tagIds));
  }

  private Inject seedInject(Instant sentAt) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'record detections inject', now(), now(), 0, false, true, ?)",
        injectId,
        tenantId);
    Inject inject = new Inject();
    inject.setId(injectId);
    InjectStatus status = new InjectStatus();
    status.setTrackingSentDate(sentAt);
    inject.setStatus(status);
    return inject;
  }

  private String seedAsset() {
    return seedAsset(tenantId);
  }

  private String seedAsset(String assetTenantId) {
    String assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        assetId,
        "record-asset-" + assetId,
        assetTenantId,
        "record-asset-" + assetId);
    return assetId;
  }

  private String seedTag() {
    String tagId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        tagId,
        "record-tag-" + tagId,
        tenantId);
    return tagId;
  }

  private long countFindings() {
    return count("SELECT count(*) FROM findings WHERE tenant_id = ?", tenantId);
  }

  private long countOccurrences(String findingId) {
    return count(
        "SELECT count(*) FROM finding_occurrences WHERE finding_occurrence_finding_id = ?",
        findingId);
  }

  private String occurrenceFieldOf(String findingId, Inject inject) {
    return jdbc.queryForObject(
        "SELECT finding_occurrence_field FROM finding_occurrences"
            + " WHERE finding_occurrence_finding_id = ? AND finding_occurrence_inject_id = ?",
        String.class,
        findingId,
        inject.getId());
  }

  private long count(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private String locationKeyOf(String findingId) {
    return jdbc.queryForObject(
        "SELECT l.finding_location_key FROM findings f"
            + " JOIN finding_locations l ON l.finding_location_id = f.finding_location_id"
            + " WHERE f.finding_id = ?",
        String.class,
        findingId);
  }

  /** First seen, then last seen. */
  private List<Instant> seenOf(String findingId) {
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT finding_first_seen, finding_last_seen FROM findings WHERE finding_id = ?",
            findingId);
    return List.of(
        ((Timestamp) row.get("finding_first_seen")).toInstant(),
        ((Timestamp) row.get("finding_last_seen")).toInstant());
  }
}
