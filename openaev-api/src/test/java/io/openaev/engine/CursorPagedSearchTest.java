package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawGrant;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.api.CursorPageQuery;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link EngineService#searchCursorPaged}, exercised through the vulnerable-
 * observation snapshot stream (cheapest fixtures, per the story plan).
 *
 * <p>The bean only exists when the {@code BULK_SNAPSHOT_EXPORT} preview feature is enabled, hence
 * the class-level {@link TestPropertySource}.
 */
@SpringBootTest
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.enabled-dev-features=BULK_SNAPSHOT_EXPORT")
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("EngineService.searchCursorPaged")
class CursorPagedSearchTest extends IntegrationTest {

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private IndexingStatusRepository indexingStatusRepository;

  /** Comfortably in the future so no test data is ever excluded by the window bound. */
  private static final Instant WINDOW_END = Instant.now().plus(1, ChronoUnit.DAYS);

  private static final RawUserAuth ADMIN_USER = adminUser();

  /** A (assetId, findingId) pair identifying one vulnerability-observation grain. */
  private record Grain(String assetId, String findingId) {}

  @BeforeEach
  void resetIndex() throws IOException {
    endpointComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    scenarioComposer.reset();
    exerciseComposer.reset();
    tenantComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static RawUserAuth adminUser() {
    return new RawUserAuth() {
      @Override
      public String getUser_id() {
        return "test-admin-" + UUID.randomUUID();
      }

      @Override
      public boolean getUser_admin() {
        return true;
      }

      @Override
      public Set<RawGrant> getUser_grants() {
        return Set.of();
      }
    };
  }

  private static RawUserAuth nonAdminUser(String... grantedResourceIds) {
    Set<RawGrant> grants = new HashSet<>();
    for (String resourceId : grantedResourceIds) {
      grants.add(rawGrant(resourceId));
    }
    return new RawUserAuth() {
      @Override
      public String getUser_id() {
        return "test-user-" + UUID.randomUUID();
      }

      @Override
      public boolean getUser_admin() {
        return false;
      }

      @Override
      public Set<RawGrant> getUser_grants() {
        return grants;
      }
    };
  }

  private static RawGrant rawGrant(String resourceId) {
    return new RawGrant() {
      @Override
      public String getGrant_id() {
        return UUID.randomUUID().toString();
      }

      @Override
      public String getGrant_name() {
        return "observer";
      }

      @Override
      public String getUser_id() {
        return "n/a";
      }

      @Override
      public String getGrant_resource() {
        return resourceId;
      }

      @Override
      public Grant.GRANT_RESOURCE_TYPE getGrant_resource_type() {
        return Grant.GRANT_RESOURCE_TYPE.SCENARIO;
      }
    };
  }

  /** Persists an endpoint + CVE finding through a bare atomic-testing inject (no scenario). */
  private Grain newGrain() {
    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("ep-" + UUID.randomUUID()));
    FindingComposer.Composer findingWrapper =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(endpointWrapper);
    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withFinding(findingWrapper)
        .persist();
    return new Grain(endpointWrapper.get().getId(), findingWrapper.get().getId());
  }

  /** Persists an endpoint + CVE finding through an inject wired to a scenario-linked simulation. */
  private Grain newScenarioLinkedGrain(Scenario scenario) {
    EndpointComposer.Composer endpointWrapper =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("ep-" + UUID.randomUUID()));
    FindingComposer.Composer findingWrapper =
        findingComposer
            .forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle())
            .withEndpoint(endpointWrapper);
    InjectComposer.Composer injectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(findingWrapper);
    Exercise exercise = ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now());
    exercise.setScenario(scenario);
    exerciseComposer.forExercise(exercise).withInject(injectWrapper).persist();
    return new Grain(endpointWrapper.get().getId(), findingWrapper.get().getId());
  }

  private Scenario persistScenario() {
    return scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .persist()
        .get();
  }

  private void indexAndWait() throws InterruptedException {
    entityManager.flush();
    entityManager.clear();
    // Several tests rewind finding_updated_at into the past (window/since/millisecond-bucket
    // bounds) between two indexing passes. bulkProcessing fetches incrementally from the last
    // watermark, so a rewound timestamp older than that watermark would silently never be
    // re-picked-up; clearing the watermark forces a full re-fetch every time instead.
    indexingStatusRepository.deleteAll();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES/OpenSearch refreshes asynchronously; the bulk request sets no refresh policy.
    Thread.sleep(1_000);
  }

  private void bumpFindingTimestamp(String findingId, Instant ts) {
    entityManager
        .createNativeQuery("UPDATE findings SET finding_updated_at = :ts WHERE finding_id = :id")
        .setParameter("ts", ts)
        .setParameter("id", findingId)
        .executeUpdate();
  }

  private List<EsVulnerabilityObservation> page(
      RawUserAuth user, CursorPageQuery.Keyset after, int size) {
    return engineService.searchCursorPaged(
        user, EsVulnerabilityObservation.class, new CursorPageQuery(null, after, WINDOW_END, size));
  }

  private static CursorPageQuery.Keyset cursorAfter(List<EsVulnerabilityObservation> page) {
    EsVulnerabilityObservation last = page.getLast();
    return new CursorPageQuery.Keyset(last.getBase_updated_at(), last.getBase_id());
  }

  private static boolean containsAsset(List<EsVulnerabilityObservation> docs, String assetId) {
    return docs.stream().anyMatch(d -> assetId.equals(d.getBase_asset_side()));
  }

  /**
   * The engine sorts on the {@code date} field, which is millisecond-resolution, while {@code
   * _source} keeps the microseconds it got from PostgreSQL. Comparing the raw values would make
   * this assertion fail on two documents that share a millisecond but whose microseconds run
   * against the {@code base_id} order.
   */
  private static Instant sortedAt(EsVulnerabilityObservation doc) {
    return doc.getBase_updated_at().truncatedTo(ChronoUnit.MILLIS);
  }

  private static void assertOrdered(List<EsVulnerabilityObservation> docs) {
    for (int i = 1; i < docs.size(); i++) {
      EsVulnerabilityObservation prev = docs.get(i - 1);
      EsVulnerabilityObservation curr = docs.get(i);
      int cmp = sortedAt(prev).compareTo(sortedAt(curr));
      if (cmp == 0) {
        assertThat(prev.getBase_id().compareTo(curr.getBase_id())).isLessThanOrEqualTo(0);
      } else {
        assertThat(cmp).isLessThan(0);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Ordering
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("ordering")
  class Ordering {

    @Test
    @DisplayName("documents come back sorted by (base_updated_at, base_id), pages never repeat")
    void given_multipleDocuments_should_returnSortedNonOverlappingPages()
        throws InterruptedException {
      for (int i = 0; i < 10; i++) {
        newGrain();
      }
      indexAndWait();

      List<EsVulnerabilityObservation> page1 = page(ADMIN_USER, null, 4);
      assertThat(page1).hasSize(4);
      assertOrdered(page1);

      List<EsVulnerabilityObservation> page2 = page(ADMIN_USER, cursorAfter(page1), 4);
      assertThat(page2).hasSize(4);
      assertOrdered(page2);

      Set<String> page1Ids =
          page1.stream().map(EsVulnerabilityObservation::getBase_id).collect(Collectors.toSet());
      Set<String> page2Ids =
          page2.stream().map(EsVulnerabilityObservation::getBase_id).collect(Collectors.toSet());
      assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
    }
  }

  // ---------------------------------------------------------------------------
  // Tie-break (AC4)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("tie-break")
  class TieBreak {

    @Test
    @DisplayName("30 documents sharing one base_updated_at page without duplicate or omission")
    void given_30DocumentsSameTimestamp_should_pageWithoutDuplicateOrOmission()
        throws InterruptedException {
      Instant sharedTs = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
      List<Grain> grains = new ArrayList<>();
      for (int i = 0; i < 30; i++) {
        grains.add(newGrain());
      }
      indexAndWait();
      for (Grain grain : grains) {
        bumpFindingTimestamp(grain.findingId(), sharedTs);
      }
      indexAndWait();

      Set<String> seen = new LinkedHashSet<>();
      CursorPageQuery.Keyset cursor = null;
      int pageCount = 0;
      List<EsVulnerabilityObservation> lastPage;
      do {
        lastPage = page(ADMIN_USER, cursor, 7);
        pageCount++;
        for (EsVulnerabilityObservation doc : lastPage) {
          assertThat(seen.add(doc.getBase_id())).as("no duplicate id across pages").isTrue();
        }
        if (!lastPage.isEmpty()) {
          cursor = cursorAfter(lastPage);
        }
      } while (lastPage.size() == 7 && pageCount < 10);

      assertThat(pageCount).isEqualTo(5);
      assertThat(seen).hasSize(30);
    }
  }

  // ---------------------------------------------------------------------------
  // Millisecond bucket (§3.2)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("millisecond bucket")
  class MillisecondBucket {

    @Test
    @DisplayName(
        "two documents differing by microseconds within one millisecond are both returned, "
            + "ordered by base_id, and resuming from the first returns exactly the second")
    void given_subMillisecondDifference_should_returnBothOrderedByBaseId()
        throws InterruptedException {
      Instant sameMillis = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
      Grain a = newGrain();
      Grain b = newGrain();
      indexAndWait();
      bumpFindingTimestamp(a.findingId(), sameMillis.plusNanos(100_000));
      bumpFindingTimestamp(b.findingId(), sameMillis.plusNanos(900_000));
      indexAndWait();

      List<EsVulnerabilityObservation> both =
          page(ADMIN_USER, null, 100).stream()
              .filter(
                  d ->
                      d.getBase_asset_side().equals(a.assetId())
                          || d.getBase_asset_side().equals(b.assetId()))
              .toList();

      assertThat(both).hasSize(2);
      // _source still carries the microseconds; only the indexed `date` collapsed them, so the two
      // documents are a tie for the engine and come back ordered by base_id, not by microsecond.
      assertThat(both)
          .extracting(EsVulnerabilityObservation::getBase_updated_at)
          .containsExactlyInAnyOrder(sameMillis.plusNanos(100_000), sameMillis.plusNanos(900_000));
      assertThat(both).extracting(CursorPagedSearchTest::sortedAt).containsOnly(sameMillis);
      assertThat(both.get(0).getBase_id().compareTo(both.get(1).getBase_id())).isLessThan(0);

      List<EsVulnerabilityObservation> resumed =
          page(ADMIN_USER, cursorAfter(List.of(both.get(0))), 100).stream()
              .filter(
                  d ->
                      d.getBase_asset_side().equals(a.assetId())
                          || d.getBase_asset_side().equals(b.assetId()))
              .toList();

      assertThat(resumed)
          .extracting(EsVulnerabilityObservation::getBase_id)
          .containsExactly(both.get(1).getBase_id());
    }
  }

  // ---------------------------------------------------------------------------
  // Resume (AC4)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("resume")
  class Resume {

    @Test
    @DisplayName("resuming from the last document of page 2 continues exactly where paging stopped")
    void given_cursorFromEndOfPage2_should_resumeExactlyAtPage3() throws InterruptedException {
      for (int i = 0; i < 12; i++) {
        newGrain();
      }
      indexAndWait();

      List<EsVulnerabilityObservation> page1 = page(ADMIN_USER, null, 5);
      List<EsVulnerabilityObservation> page2 = page(ADMIN_USER, cursorAfter(page1), 5);
      List<EsVulnerabilityObservation> page3 = page(ADMIN_USER, cursorAfter(page2), 5);

      List<EsVulnerabilityObservation> continuation = page(ADMIN_USER, cursorAfter(page2), 100);

      List<String> expectedFromPage3 =
          page3.stream().map(EsVulnerabilityObservation::getBase_id).toList();
      List<String> continuationIds =
          continuation.stream().map(EsVulnerabilityObservation::getBase_id).toList();
      assertThat(continuationIds).startsWith(expectedFromPage3.toArray(new String[0]));
    }
  }

  // ---------------------------------------------------------------------------
  // Window (FR27)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("window")
  class Window {

    @Test
    @DisplayName("a document past windowEnd is never returned; the bound is inclusive")
    void given_documentPastWindowEnd_should_neverBeReturned() throws InterruptedException {
      Instant windowEnd = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
      Grain withinWindow = newGrain();
      Grain pastWindow = newGrain();
      indexAndWait();
      bumpFindingTimestamp(withinWindow.findingId(), windowEnd);
      bumpFindingTimestamp(pastWindow.findingId(), windowEnd.plusMillis(1));
      indexAndWait();

      List<EsVulnerabilityObservation> results =
          engineService.searchCursorPaged(
              ADMIN_USER,
              EsVulnerabilityObservation.class,
              new CursorPageQuery(null, null, windowEnd, 100));

      assertThat(containsAsset(results, withinWindow.assetId())).isTrue();
      assertThat(containsAsset(results, pastWindow.assetId())).isFalse();
    }
  }

  // ---------------------------------------------------------------------------
  // Since (FR26)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("since")
  class Since {

    @Test
    @DisplayName("since is inclusive; a document strictly before it is excluded")
    void given_sinceBound_should_excludeStrictlyOlderDocument() throws InterruptedException {
      Instant since = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
      Grain atSince = newGrain();
      Grain beforeSince = newGrain();
      indexAndWait();
      bumpFindingTimestamp(atSince.findingId(), since);
      bumpFindingTimestamp(beforeSince.findingId(), since.minusMillis(1));
      indexAndWait();

      List<EsVulnerabilityObservation> results =
          engineService.searchCursorPaged(
              ADMIN_USER,
              EsVulnerabilityObservation.class,
              new CursorPageQuery(since, null, WINDOW_END, 100));

      assertThat(containsAsset(results, atSince.assetId())).isTrue();
      assertThat(containsAsset(results, beforeSince.assetId())).isFalse();
    }

    @Test
    @DisplayName("both since and after null returns from the beginning")
    void given_noSinceNoAfter_should_returnFromTheBeginning() throws InterruptedException {
      Grain grain = newGrain();
      indexAndWait();

      List<EsVulnerabilityObservation> results = page(ADMIN_USER, null, 100);

      assertThat(containsAsset(results, grain.assetId())).isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // Bounds (FR28)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("bounds")
  class Bounds {

    @Test
    @DisplayName("size 0 throws IllegalArgumentException")
    void given_sizeZero_should_throw() {
      assertThatThrownBy(
              () ->
                  engineService.searchCursorPaged(
                      ADMIN_USER,
                      EsVulnerabilityObservation.class,
                      new CursorPageQuery(null, null, WINDOW_END, 0)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("negative size throws IllegalArgumentException")
    void given_negativeSize_should_throw() {
      assertThatThrownBy(
              () ->
                  engineService.searchCursorPaged(
                      ADMIN_USER,
                      EsVulnerabilityObservation.class,
                      new CursorPageQuery(null, null, WINDOW_END, -1)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size above CURSOR_PAGE_MAX_SIZE throws IllegalArgumentException")
    void given_sizeAboveMax_should_throw() {
      assertThatThrownBy(
              () ->
                  engineService.searchCursorPaged(
                      ADMIN_USER,
                      EsVulnerabilityObservation.class,
                      new CursorPageQuery(
                          null, null, WINDOW_END, EngineService.CURSOR_PAGE_MAX_SIZE + 1)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("size equal to CURSOR_PAGE_MAX_SIZE does not throw")
    void given_sizeAtMax_should_notThrow() {
      assertThatCode(
              () ->
                  engineService.searchCursorPaged(
                      ADMIN_USER,
                      EsVulnerabilityObservation.class,
                      new CursorPageQuery(
                          null, null, WINDOW_END, EngineService.CURSOR_PAGE_MAX_SIZE)))
          .doesNotThrowAnyException();
    }
  }

  // ---------------------------------------------------------------------------
  // Model resolution
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("model resolution")
  class ModelResolution {

    private static class UnregisteredEsBase extends EsBase {}

    @Test
    @DisplayName("a registered model returns documents")
    void given_registeredModel_should_returnDocuments() throws InterruptedException {
      Grain grain = newGrain();
      indexAndWait();

      List<EsVulnerabilityObservation> results = page(ADMIN_USER, null, 100);

      assertThat(containsAsset(results, grain.assetId())).isTrue();
    }

    @Test
    @DisplayName("an EsBase subclass with no registered handler throws IllegalArgumentException")
    void given_unregisteredModel_should_throw() {
      assertThatThrownBy(
              () ->
                  engineService.searchCursorPaged(
                      ADMIN_USER,
                      UnregisteredEsBase.class,
                      new CursorPageQuery(null, null, WINDOW_END, 10)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not registered");
    }
  }

  // ---------------------------------------------------------------------------
  // Tenancy
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("tenancy")
  class Tenancy {

    @Test
    @DisplayName("documents of another tenant are never returned")
    void given_otherTenantDocument_should_neverBeReturned() throws InterruptedException {
      Tenant otherTenant =
          tenantComposer
              .forTenant(TenantFixture.getTenant("cursor-search-other-" + UUID.randomUUID()))
              .persist()
              .get();

      Grain ownTenantGrain = newGrain();

      Asset otherAsset = EndpointFixture.createEndpoint("ep-other-tenant-" + UUID.randomUUID());
      otherAsset.setTenant(otherTenant);
      entityManager.persist(otherAsset);
      Inject otherInject = InjectFixture.getDefaultInject();
      otherInject.setTenant(otherTenant);
      entityManager.persist(otherInject);
      Finding otherFinding = FindingFixture.createDefaultCveFindingWithRandomTitle();
      otherFinding.setTenant(otherTenant);
      otherFinding.setInject(otherInject);
      otherFinding.setAssets(List.of(otherAsset));
      entityManager.persist(otherFinding);

      indexAndWait();

      List<EsVulnerabilityObservation> results = page(ADMIN_USER, null, 100);

      assertThat(containsAsset(results, otherAsset.getId())).isFalse();
      assertThat(containsAsset(results, ownTenantGrain.assetId())).isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // Restrictions (§3.3)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("restrictions")
  class Restrictions {

    @Test
    @DisplayName("admin sees every document regardless of restrictions")
    void given_scenarioLinkedAndUnrestrictedDocuments_should_returnBothForAdmin()
        throws InterruptedException {
      Scenario scenario = persistScenario();
      Grain restricted = newScenarioLinkedGrain(scenario);
      Grain unrestricted = newGrain();
      indexAndWait();

      List<EsVulnerabilityObservation> results = page(ADMIN_USER, null, 100);

      assertThat(containsAsset(results, restricted.assetId())).isTrue();
      assertThat(containsAsset(results, unrestricted.assetId())).isTrue();
    }

    @Test
    @DisplayName("a non-admin user with no grant only sees unrestricted documents")
    void given_noGrant_should_onlySeeUnrestrictedDocuments() throws InterruptedException {
      Scenario scenario = persistScenario();
      Grain restricted = newScenarioLinkedGrain(scenario);
      Grain unrestricted = newGrain();
      indexAndWait();

      List<EsVulnerabilityObservation> results = page(nonAdminUser(), null, 100);

      assertThat(containsAsset(results, restricted.assetId())).isFalse();
      assertThat(containsAsset(results, unrestricted.assetId())).isTrue();
    }

    @Test
    @DisplayName("granting the scenario reveals the restricted document")
    void given_grantOnScenario_should_revealRestrictedDocument() throws InterruptedException {
      Scenario scenario = persistScenario();
      Grain restricted = newScenarioLinkedGrain(scenario);
      indexAndWait();

      List<EsVulnerabilityObservation> results = page(nonAdminUser(scenario.getId()), null, 100);

      assertThat(containsAsset(results, restricted.assetId())).isTrue();
    }
  }

  // ---------------------------------------------------------------------------
  // Index scope (§3.4)
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("index scope")
  class IndexScope {

    @Test
    @DisplayName("a search on one snapshot stream never returns documents from the other stream")
    void given_vulnerabilityDocuments_should_notAppearInAttackObservationSearch()
        throws InterruptedException {
      newGrain();
      newGrain();
      indexAndWait();

      List<EsAttackObservation> results =
          engineService.searchCursorPaged(
              ADMIN_USER,
              EsAttackObservation.class,
              new CursorPageQuery(null, null, WINDOW_END, 100));

      assertThat(results).isEmpty();
    }
  }
}
