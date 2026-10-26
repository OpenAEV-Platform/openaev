package io.openaev.service.engine;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Group;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.api.CountRuntime;
import io.openaev.engine.api.FlatConfiguration;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.api.StructuralHistogramRuntime;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.query.EsSeries;
import io.openaev.engine.query.EsSeriesData;
import io.openaev.engine.security.EngineReadScope;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The marking dimension of {@link EngineReadScopeResolver} against PostgreSQL: real marking
 * definitions, real asset {@code marking_ids}, a non-admin reader whose clearance comes from its
 * group's markings, with {@code MARKING} on and {@code assets} active (the test configuration). The
 * resolved scope is then applied by the engine, so the assertions cover both the hidden-assets
 * query (PostgreSQL arrays, the caller's clearance, per tenant) and what the reader gets back:
 * asset lists, derived finding lists and counts, and the security platforms that reported on an
 * expectation.
 *
 * <p>Order matters in the fixture: documents are indexed <b>before</b> the markings are written, so
 * every row is in the index and a hidden one is hidden by the reader scope, not by an indexing pass
 * that could not see it. Markings are written with raw JDBC on purpose (see {@code
 * AssetMarkingIsolationTest}): an ORM write of {@code TLP:RED} would itself be refused to a reader
 * not cleared for it.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("Engine read scope: markings resolved from the database")
// es8 only until the reader filter is ported to es9 and OpenSearch (implementation-plan-dashboard
// steps 11-12): those engines ignore the scope, so the nightly would fail on them.
@DisabledIfEnvironmentVariable(named = "ENGINE_ENGINESELECTOR", matches = "elk9|opensearch")
class EngineReadScopeResolverIntegrationTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final TxCtx DEFAULT_CTX = TxCtx.forTenant(DEFAULT_TENANT);
  private static final String EXPECTATION = "expectation-inject";

  @Autowired private EngineReadScopeResolver resolver;
  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GroupRepository groupRepository;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mvc;

  private JdbcTemplate jdbc;
  private String userId;
  private String tenantB;
  private Group markingGroup;

  private MarkingDefinition tlpGreen;
  private MarkingDefinition tlpRed;
  private MarkingDefinition tenantBTlpGreen;

  private String unmarked;
  private String green;
  private String red;
  private String tenantBGreen;
  private String findingOnGreen;
  private String findingOnRed;
  private String findingOnGreenAndRed;
  private String findingWithoutEndpoint;
  private String unmarkedPlatform;
  private String redPlatform;
  private String expectationOnGreen;

  @BeforeEach
  void seed() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantGroupComposer.reset();
    markingDefinitionComposer.reset();
    endpointComposer.reset();
    findingComposer.reset();
    injectComposer.reset();
    injectExpectationComposer.reset();
    securityPlatformComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }

    // A non-admin reader of assets, findings and every scenario in the default tenant, and of
    // assets only in tenant B.
    tenantHelper.grantCapabilitiesInTenant(
        DEFAULT_TENANT,
        Set.of(
            Capability.ACCESS_DASHBOARDS,
            Capability.ACCESS_ASSETS,
            Capability.ACCESS_FINDINGS,
            Capability.ACCESS_ASSESSMENT));
    tenantB =
        tenantHelper
            .createTenantWithCapabilities(
                "engine-scope-markings-b",
                Set.of(Capability.ACCESS_DASHBOARDS, Capability.ACCESS_ASSETS))
            .getId();
    User user = testUserHolder.get();
    userId = user.getId();

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    tlpGreen = marking(DEFAULT_TENANT, 60);
    tlpRed = marking(DEFAULT_TENANT, 70);
    tenantBTlpGreen = marking(tenantB, 60);

    // The reader's clearance in the default tenant comes from this group's markings.
    markingGroup =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup("engine-scope-markings-" + uniqueDefinition()))
            .persist()
            .get();
    markingGroup.setUsers(new ArrayList<>(List.of(user)));
    groupRepository.save(markingGroup);

    EndpointComposer.Composer unmarkedEndpoint = endpoint(DEFAULT_TENANT, "unmarked").persist();
    EndpointComposer.Composer tenantBGreenEndpoint = endpoint(tenantB, "tenant-b-green").persist();
    // Persisted with their finding (inject -> finding -> endpoint).
    EndpointComposer.Composer greenEndpoint = endpoint(DEFAULT_TENANT, "green");
    EndpointComposer.Composer redEndpoint = endpoint(DEFAULT_TENANT, "red");
    findingOnGreen = finding(greenEndpoint);
    findingOnRed = finding(redEndpoint);
    findingOnGreenAndRed = finding(greenEndpoint, redEndpoint);
    findingWithoutEndpoint = finding();
    unmarkedPlatform = securityPlatform("unmarked");
    redPlatform = securityPlatform("red");
    expectationOnGreen = expectation(greenEndpoint, unmarkedPlatform, redPlatform);
    unmarked = unmarkedEndpoint.get().getId();
    green = greenEndpoint.get().getId();
    red = redEndpoint.get().getId();
    tenantBGreen = tenantBGreenEndpoint.get().getId();

    // Index everything first, unmarked: see the class javadoc.
    entityManager.flush();
    entityManager.clear();
    indexingStatusRepository.deleteAll();
    engineService.bulkProcessing(engineContext.getModels().stream());
    // ES processes indexing asynchronously — give it time
    Thread.sleep(1_000);

    mark(green, tlpGreen);
    mark(red, tlpRed);
    mark(redPlatform, tlpRed);
    mark(tenantBGreen, tenantBTlpGreen);
    grant(tlpGreen);
  }

  // -- Tests --

  @Nested
  @DisplayName("Hidden assets, from real markings and the reader's clearance")
  class HiddenAssets {

    @Test
    @DisplayName("given a TLP:GREEN clearance, should hide only the TLP:RED asset")
    void given_greenClearance_should_hideOnlyRed() {
      // -- ACT --
      EngineReadScope scope = resolver.resolve(DEFAULT_CTX);

      // -- ASSERT --
      assertThat(scope.unrestricted()).isFalse();
      assertThat(scope.tenantRules().get(DEFAULT_TENANT).hiddenAssetIds())
          .contains(red)
          .doesNotContain(green, unmarked);
    }

    @Test
    @DisplayName("given a TLP:GREEN clearance, should list the unmarked and GREEN assets, not RED")
    void given_greenClearance_should_listAssetsWithoutRed() {
      // -- ACT --
      List<String> assets = ids(list(DEFAULT_CTX, "asset"));

      // -- ASSERT --
      assertThat(assets).contains(unmarked, green).doesNotContain(red);
    }

    @Test
    @DisplayName("given a TLP:GREEN clearance, should hide the finding derived from the RED asset")
    void given_greenClearance_should_hideDerivedFindingOfRed() {
      // -- ACT --
      List<String> findings = ids(list(DEFAULT_CTX, "finding"));
      long count = countFindings(DEFAULT_CTX, findingOnGreen, findingOnRed);

      // -- ASSERT --
      assertThat(findings).contains(findingOnGreen).doesNotContain(findingOnRed);
      assertThat(count).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("A marking or clearance change applies on the next request")
  class NextRequest {

    @Test
    @DisplayName("given the clearance raised to TLP:RED, should reveal the RED asset and finding")
    void given_clearanceRaisedToRed_should_revealRed() {
      // -- ARRANGE --
      assertThat(ids(list(DEFAULT_CTX, "asset"))).doesNotContain(red);
      grant(tlpRed);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(DEFAULT_CTX);
      List<String> assets = ids(list(DEFAULT_CTX, "asset"));

      // -- ASSERT --
      assertThat(scope.hiddenAssetIds()).doesNotContain(red);
      assertThat(assets).contains(red, green, unmarked);
      assertThat(countFindings(DEFAULT_CTX, findingOnGreen, findingOnRed)).isEqualTo(2);
    }

    @Test
    @DisplayName("given the GREEN asset re-marked TLP:RED, should hide it and its finding")
    void given_assetRemarkedRed_should_hideItAndItsFinding() {
      // -- ARRANGE --
      assertThat(ids(list(DEFAULT_CTX, "asset"))).contains(green);
      mark(green, tlpRed);

      // -- ACT --
      EngineReadScope scope = resolver.resolve(DEFAULT_CTX);

      // -- ASSERT --
      assertThat(scope.tenantRules().get(DEFAULT_TENANT).hiddenAssetIds()).contains(green, red);
      assertThat(ids(list(DEFAULT_CTX, "asset"))).doesNotContain(green, red).contains(unmarked);
      assertThat(ids(list(DEFAULT_CTX, "finding"))).doesNotContain(findingOnGreen);
      assertThat(countFindings(DEFAULT_CTX, findingOnGreen, findingOnRed)).isZero();
    }
  }

  @Nested
  @DisplayName("Findings linked to several endpoints: one visible endpoint is enough, as in SQL")
  class FindingsOnSeveralEndpoints {

    private EsFinding findingDocument(String findingId) {
      return list(DEFAULT_CTX, "finding").stream()
          .filter(document -> findingId.equals(document.getBase_id()))
          .map(EsFinding.class::cast)
          .findFirst()
          .orElse(null);
    }

    @Test
    @DisplayName("given a finding on GREEN and RED, should list it with its GREEN endpoint only")
    void given_findingOnGreenAndRed_should_listItWithGreenOnly() {
      // -- ACT --
      EsFinding shared = findingDocument(findingOnGreenAndRed);

      // -- ASSERT --
      assertThat(shared).isNotNull();
      assertThat(shared.getBase_endpoint_side()).containsExactly(green);
      assertThat(shared.getBase_dependencies()).contains(green);
      assertThat(asJsonString(shared)).doesNotContain(red);
    }

    @Test
    @DisplayName("given findings on GREEN + RED, RED only and no endpoint, should count two")
    void given_mixedFindings_should_countTheVisibleOnes() {
      // -- ACT --
      long count =
          countFindings(DEFAULT_CTX, findingOnGreenAndRed, findingOnRed, findingWithoutEndpoint);

      // -- ASSERT --
      assertThat(count).isEqualTo(2);
      assertThat(ids(list(DEFAULT_CTX, "finding")))
          .contains(findingOnGreenAndRed, findingWithoutEndpoint)
          .doesNotContain(findingOnRed);
    }

    @Test
    @DisplayName("given findings by endpoint, should give GREEN a bucket and RED none")
    void given_findingsByEndpoint_should_haveNoRedBucket() {
      // -- ACT --
      List<String> buckets = findingsByEndpoint(DEFAULT_CTX);

      // -- ASSERT --
      assertThat(buckets).contains(green).doesNotContain(red);
    }

    @Test
    @DisplayName("given the clearance raised to TLP:RED, should show both endpoints")
    void given_clearanceRaisedToRed_should_showBothEndpoints() {
      // -- ARRANGE --
      grant(tlpRed);

      // -- ACT --
      EsFinding shared = findingDocument(findingOnGreenAndRed);

      // -- ASSERT --
      assertThat(shared.getBase_endpoint_side()).containsExactlyInAnyOrder(green, red);
    }

    @Test
    @DisplayName("given GREEN re-marked TLP:RED, should hide the finding: no endpoint left visible")
    void given_everyEndpointHidden_should_hideTheFinding() {
      // -- ARRANGE --
      mark(green, tlpRed);

      // -- ACT & ASSERT --
      assertThat(ids(list(DEFAULT_CTX, "finding"))).doesNotContain(findingOnGreenAndRed);
      assertThat(countFindings(DEFAULT_CTX, findingOnGreenAndRed)).isZero();
    }
  }

  @Nested
  @DisplayName("Tenant isolation")
  class TenantIsolation {

    @Test
    @DisplayName(
        "given TLP:GREEN held in the default tenant only, should hide tenant B's TLP:GREEN asset")
    void given_clearanceInOneTenant_should_notApplyToTheOther() {
      // -- ARRANGE --
      // Tenant B has its own TLP:GREEN definition: holding the default tenant's one clears nothing
      // in tenant B.
      TxCtx bothTenants = TxCtx.forTenants(List.of(DEFAULT_TENANT, tenantB));

      // -- ACT --
      EngineReadScope scope = resolver.resolve(bothTenants);
      List<String> assets = ids(list(bothTenants, "asset"));

      // -- ASSERT --
      assertThat(scope.tenantRules().get(DEFAULT_TENANT).hiddenAssetIds())
          .contains(red)
          .doesNotContain(green, tenantBGreen);
      assertThat(scope.tenantRules().get(tenantB).hiddenAssetIds())
          .contains(tenantBGreen)
          .doesNotContain(red);
      assertThat(assets).contains(unmarked, green).doesNotContain(red, tenantBGreen);
    }

    @Test
    @DisplayName("given BYPASS in the default tenant only, should not clear tenant B's markings")
    void given_bypassInDefaultTenantOnly_should_notClearTenantB() throws Exception {
      // -- ARRANGE --
      // BYPASS in the default tenant clears its markings there; tenant B is only read with
      // ACCESS_ASSETS and no clearance, so its marked asset stays hidden.
      tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, Set.of(Capability.BYPASS));
      clearanceCache.evictForUser(userId);
      TxCtx bothTenants = TxCtx.forTenants(List.of(DEFAULT_TENANT, tenantB));

      // -- ACT --
      EngineReadScope scope = resolver.resolve(bothTenants);
      List<String> dashboardAssets = ids(list(bothTenants, "asset"));
      List<String> restAssets = searchEndpointIds(DEFAULT_TENANT + "," + tenantB);

      // -- ASSERT --
      assertThat(scope.tenantRules().get(tenantB).hiddenAssetIds()).contains(tenantBGreen);
      assertThat(dashboardAssets).contains(red, green).doesNotContain(tenantBGreen);
      assertThat(restAssets).contains(red, green).doesNotContain(tenantBGreen);
    }
  }

  @Nested
  @DisplayName("Security platforms reporting on a visible asset: only the hidden platform goes")
  class SecurityPlatformsOnAVisibleAsset {

    private EsInjectExpectation expectationDocument() {
      return list(DEFAULT_CTX, EXPECTATION).stream()
          .filter(document -> expectationOnGreen.equals(document.getBase_id()))
          .map(EsInjectExpectation.class::cast)
          .findFirst()
          .orElse(null);
    }

    @Test
    @DisplayName("given a RED platform reporting on GREEN, should list it without the RED platform")
    void given_redPlatformOnGreen_should_listTheExpectationWithoutIt() {
      // -- ACT --
      EsInjectExpectation expectation = expectationDocument();

      // -- ASSERT --
      assertThat(expectation).isNotNull();
      assertThat(expectation.getBase_security_platforms_side()).containsExactly(unmarkedPlatform);
    }

    @Test
    @DisplayName("given expectations by security platform, should give the RED platform no bucket")
    void given_expectationsBySecurityPlatform_should_haveNoRedBucket() {
      // -- ACT --
      List<String> buckets = expectationsBySecurityPlatform(DEFAULT_CTX);

      // -- ASSERT --
      assertThat(buckets).contains(unmarkedPlatform).doesNotContain(redPlatform);
    }

    @Test
    @DisplayName("given a filter on the RED platform, should match no expectation")
    void given_filterOnRedPlatform_should_matchNothing() {
      // -- ACT & ASSERT --
      assertThat(expectationsReportedBy(redPlatform)).isEmpty();
      assertThat(expectationsReportedBy(unmarkedPlatform)).containsExactly(expectationOnGreen);
    }

    @Test
    @DisplayName("given the clearance raised to TLP:RED, should show the RED platform again")
    void given_clearanceRaisedToRed_should_showTheRedPlatform() {
      // -- ARRANGE --
      grant(tlpRed);

      // -- ACT & ASSERT --
      assertThat(expectationDocument().getBase_security_platforms_side())
          .containsExactlyInAnyOrder(unmarkedPlatform, redPlatform);
      assertThat(expectationsBySecurityPlatform(DEFAULT_CTX))
          .contains(unmarkedPlatform, redPlatform);
      assertThat(expectationsReportedBy(redPlatform)).containsExactly(expectationOnGreen);
    }
  }

  // -- Helpers --

  /** The unprefixed REST endpoint search over the given tenants: the SQL rewrite's clearance. */
  private List<String> searchEndpointIds(String tenantIds) throws Exception {
    String response =
        mvc.perform(
                post("/api/endpoints/search")
                    .header("X-Tenant-Ids", tenantIds)
                    .content(asJsonString(PaginationFixture.getDefault().size(200).build()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.content[*].asset_id");
  }

  private List<EsBase> list(TxCtx ctx, String entity) {
    ListConfiguration config = engineService.createListConfiguration(entity, Map.of());
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 100));
    return engineService.entities(ctx, resolver.resolve(ctx), runtime).getEsDatas();
  }

  /** The fixture's expectation if a security platform filter on {@code platformId} matches it. */
  private List<String> expectationsReportedBy(String platformId) {
    ListConfiguration config =
        engineService.createListConfiguration(
            EXPECTATION, Map.of("base_id", List.of(expectationOnGreen)));
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    Filters.Filter reportedBy = new Filters.Filter();
    reportedBy.setKey("base_security_platforms_side");
    reportedBy.setMode(Filters.FilterMode.or);
    reportedBy.setOperator(Filters.FilterOperator.eq);
    reportedBy.setValues(List.of(platformId));
    List<Filters.Filter> filters =
        new ArrayList<>(config.getPerspective().getFilter().getFilters());
    filters.add(reportedBy);
    config.getPerspective().getFilter().setFilters(filters);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 100));
    return ids(
        engineService.entities(DEFAULT_CTX, resolver.resolve(DEFAULT_CTX), runtime).getEsDatas());
  }

  /**
   * The platform buckets of this fixture's expectation, as an "expectations by platform" widget.
   */
  private List<String> expectationsBySecurityPlatform(TxCtx ctx) {
    Filters.FilterGroup filter = Filters.FilterGroup.defaultFilterGroup();
    filter.setFilters(
        List.of(
            Filters.Filter.getNewDefaultEqualFilter("base_entity", List.of(EXPECTATION)),
            Filters.Filter.getNewDefaultEqualFilter("base_id", List.of(expectationOnGreen))));
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("expectations");
    series.setFilter(filter);
    StructuralHistogramWidget widget = new StructuralHistogramWidget();
    widget.setField("base_security_platforms_side");
    widget.setDateAttribute("base_created_at");
    widget.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setLimit(100);
    widget.setSeries(List.of(series));
    List<EsSeries> result =
        engineService.multiTermHistogram(
            ctx, resolver.resolve(ctx), new StructuralHistogramRuntime(widget, Map.of(), Map.of()));
    return result.getFirst().getData().stream().map(EsSeriesData::getKey).toList();
  }

  /** The endpoint buckets of this fixture's findings, as a "findings by endpoint" widget. */
  private List<String> findingsByEndpoint(TxCtx ctx) {
    Filters.FilterGroup filter = Filters.FilterGroup.defaultFilterGroup();
    filter.setFilters(
        List.of(
            Filters.Filter.getNewDefaultEqualFilter("base_entity", List.of("finding")),
            Filters.Filter.getNewDefaultEqualFilter(
                "base_id", List.of(findingOnGreen, findingOnRed, findingOnGreenAndRed))));
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("findings");
    series.setFilter(filter);
    StructuralHistogramWidget widget = new StructuralHistogramWidget();
    widget.setField("base_endpoint_side");
    widget.setDateAttribute("base_created_at");
    widget.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setLimit(100);
    widget.setSeries(List.of(series));
    List<EsSeries> result =
        engineService.multiTermHistogram(
            ctx, resolver.resolve(ctx), new StructuralHistogramRuntime(widget, Map.of(), Map.of()));
    return result.getFirst().getData().stream().map(EsSeriesData::getKey).toList();
  }

  /** Counts the given findings, as a count widget would, with the reader's scope. */
  private long countFindings(TxCtx ctx, String... findingIds) {
    Filters.FilterGroup filter = Filters.FilterGroup.defaultFilterGroup();
    filter.setFilters(
        List.of(
            Filters.Filter.getNewDefaultEqualFilter("base_entity", List.of("finding")),
            Filters.Filter.getNewDefaultEqualFilter("base_id", List.of(findingIds))));
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("findings");
    series.setFilter(filter);
    FlatConfiguration config = new FlatConfiguration();
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    config.setSeries(List.of(series));
    return engineService
        .count(ctx, resolver.resolve(ctx), new CountRuntime(config, Map.of(), Map.of()))
        .getIntervalCount();
  }

  private static List<String> ids(List<EsBase> documents) {
    return documents.stream().map(EsBase::getBase_id).toList();
  }

  private MarkingDefinition marking(String tenantId, int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get();
  }

  private EndpointComposer.Composer endpoint(String tenantId, String name) {
    Endpoint endpoint =
        EndpointFixture.createEndpoint("engine-scope-" + name + "-" + uniqueDefinition());
    endpoint.setTenant(new Tenant(tenantId));
    return endpointComposer.forEndpoint(endpoint);
  }

  /** A finding on {@code endpoint}, through a bare (atomic testing) inject. */
  private String finding(EndpointComposer.Composer... endpoints) {
    FindingComposer.Composer finding =
        findingComposer.forFinding(FindingFixture.createDefaultCveFindingWithRandomTitle());
    for (EndpointComposer.Composer endpoint : endpoints) {
      finding.withEndpoint(endpoint);
    }
    injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(finding).persist();
    return finding.get().getId();
  }

  /** A security platform of the default tenant; security platforms are assets, so markable. */
  private String securityPlatform(String name) {
    SecurityPlatform platform =
        SecurityPlatformFixture.createDefault(
            "engine-scope-" + name + "-" + uniqueDefinition(), "EDR");
    platform.setTenant(new Tenant(DEFAULT_TENANT));
    return securityPlatformComposer.forSecurityPlatform(platform).persist().get().getId();
  }

  /**
   * A detection expectation on {@code endpoint}, through a bare (atomic testing) inject, with one
   * result reported by each of {@code platformIds}.
   */
  private String expectation(EndpointComposer.Composer endpoint, String... platformIds) {
    InjectExpectationComposer.Composer expectation =
        injectExpectationComposer
            .forExpectation(InjectExpectationFixture.createDefaultDetectionInjectExpectation())
            .withEndpoint(endpoint);
    expectation
        .get()
        .setResults(
            Arrays.stream(platformIds)
                .map(
                    platformId ->
                        InjectExpectationResult.builder()
                            .sourceId(platformId)
                            .sourceType("security-platform")
                            .sourceName(platformId)
                            .result("detected")
                            .score(100.0)
                            .date(Instant.now())
                            .build())
                .collect(Collectors.toCollection(ArrayList::new)));
    injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withExpectation(expectation)
        .persist();
    return expectation.get().getId();
  }

  /** Grants markings to the reader's group, and drops the cached clearance. */
  private void grant(MarkingDefinition... markings) {
    for (MarkingDefinition marking : markings) {
      jdbc.update(
          "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)"
              + " ON CONFLICT DO NOTHING",
          markingGroup.getId(),
          marking.getId());
    }
    clearanceCache.evictForUser(userId);
  }

  /** Out-of-band write: see the class javadoc for why this is not an ORM save. */
  private void mark(String assetId, MarkingDefinition... markings) {
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) Arrays.stream(markings).map(MarkingDefinition::getId).toArray(String[]::new),
        assetId);
  }
}
