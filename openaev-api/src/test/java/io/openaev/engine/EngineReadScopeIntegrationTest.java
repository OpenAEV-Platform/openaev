package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.api.ListConfiguration;
import io.openaev.engine.api.ListRuntime;
import io.openaev.engine.api.StructuralHistogramRuntime;
import io.openaev.engine.api.StructuralHistogramWidget;
import io.openaev.engine.api.WidgetConfigurationWithSeries;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.query.EsSeries;
import io.openaev.engine.query.EsSeriesData;
import io.openaev.engine.security.EngineReadScope;
import io.openaev.utils.CustomDashboardTimeRange;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.Pagination;
import io.openaev.utilstest.RabbitMQTestListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the engine does with an {@link EngineReadScope}: documents of unreadable types, of
 * non-granted scenarios and of hidden assets are left out, and a parent targeting a hidden asset
 * stays visible without it. The scopes are built directly here; how a scope is resolved for a user
 * is covered by {@code EngineReadScopeResolverTest}.
 *
 * <p>Fixture: scenario S1 has an inject targeting {@code GREEN} and {@code RED}; scenario S2 has an
 * inject targeting {@code OTHER}; scenario S3 has an inject targeting {@code RED} only.
 */
@SpringBootTest
@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@DisplayName("Engine reads filtered by the reader scope (capability, grant, marking)")
// es8 only until the reader filter is ported to es9 and OpenSearch (implementation-plan-dashboard
// steps 11-12): those engines ignore the scope, so the nightly would fail on them.
@DisabledIfEnvironmentVariable(named = "ENGINE_ENGINESELECTOR", matches = "elk9|opensearch")
class EngineReadScopeIntegrationTest extends IntegrationTest {

  private static final String ASSET = "asset";
  private static final String SCENARIO = "scenario";
  private static final String INJECT = "inject";

  @Autowired private EngineService engineService;
  @Autowired private EngineContext engineContext;
  @Autowired private IndexingStatusRepository indexingStatusRepository;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private String green;
  private String red;
  private String other;
  private String scenario1;
  private String scenario2;
  private String injectGreenRed;
  private String injectOther;
  private String injectRedOnly;

  @BeforeEach
  void indexFixture() throws IOException, InterruptedException {
    endpointComposer.reset();
    injectComposer.reset();
    scenarioComposer.reset();
    for (EsModel<?> model : engineContext.getModels()) {
      engineService.cleanUpIndex(model.getName());
    }
    EndpointComposer.Composer greenEndpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("scope-green"));
    EndpointComposer.Composer redEndpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("scope-red"));
    EndpointComposer.Composer otherEndpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint("scope-other"));
    InjectComposer.Composer greenRedInject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(greenEndpoint)
            .withEndpoint(redEndpoint);
    Scenario s1 =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(greenRedInject)
            .persist()
            .get();
    InjectComposer.Composer otherInject =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(otherEndpoint);
    Scenario s2 =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(otherInject)
            .persist()
            .get();
    InjectComposer.Composer redOnlyInject =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(redEndpoint);
    scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(redOnlyInject)
        .persist();
    injectGreenRed = greenRedInject.get().getId();
    injectOther = otherInject.get().getId();
    injectRedOnly = redOnlyInject.get().getId();
    green = greenEndpoint.get().getId();
    red = redEndpoint.get().getId();
    other = otherEndpoint.get().getId();
    scenario1 = s1.getId();
    scenario2 = s2.getId();

    entityManager.flush();
    entityManager.clear();
    indexingStatusRepository.deleteAll();
    engineService.bulkProcessing(engineContext.getModels().stream());
    Thread.sleep(1_000);
  }

  // -- Helpers --

  private List<EsBase> list(EngineReadScope scope, String entity) {
    ListConfiguration config = engineService.createListConfiguration(entity, Map.of());
    config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    ListRuntime runtime = new ListRuntime(config, Map.of(), Map.of(), new Pagination(0, 100));
    return engineService.entities(TxCtx.allTenants(), scope, runtime).getEsDatas();
  }

  private List<String> ids(List<EsBase> documents) {
    return documents.stream().map(EsBase::getBase_id).toList();
  }

  private List<String> injectsByAsset(EngineReadScope scope) {
    Filters.Filter injects =
        Filters.Filter.getNewDefaultEqualFilter("base_entity", List.of(INJECT));
    Filters.FilterGroup filter = Filters.FilterGroup.defaultFilterGroup();
    filter.setFilters(List.of(injects));
    WidgetConfigurationWithSeries.Series series = new WidgetConfigurationWithSeries.Series();
    series.setName("injects");
    series.setFilter(filter);
    StructuralHistogramWidget widget = new StructuralHistogramWidget();
    widget.setField("base_assets_side");
    widget.setDateAttribute("base_created_at");
    widget.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
    widget.setLimit(100);
    widget.setSeries(List.of(series));
    List<EsSeries> result =
        engineService.multiTermHistogram(
            TxCtx.allTenants(), scope, new StructuralHistogramRuntime(widget, Map.of(), Map.of()));
    return result.getFirst().getData().stream().map(EsSeriesData::getKey).toList();
  }

  // The fixtures land in the default tenant: the reader's rules are those of that tenant.
  private static EngineReadScope readable(String... entities) {
    return EngineReadScope.forTenant(
        Tenant.DEFAULT_TENANT_UUID, Set.of(entities), Map.of(), Set.of());
  }

  private static EngineReadScope hiding(Set<String> hiddenAssetIds, String... entities) {
    return EngineReadScope.forTenant(
        Tenant.DEFAULT_TENANT_UUID, Set.of(entities), Map.of(), hiddenAssetIds);
  }

  // -- Tests --

  @Nested
  @DisplayName("Capability")
  class Capability {

    @Test
    @DisplayName("given asset not readable, should return no asset document")
    void given_assetNotReadable_should_returnNoAsset() {
      // -- ACT --
      List<EsBase> assets = list(readable(SCENARIO, INJECT), ASSET);

      // -- ASSERT --
      assertThat(assets).isEmpty();
    }

    @Test
    @DisplayName("given asset readable, should return every asset")
    void given_assetReadable_should_returnEveryAsset() {
      // -- ACT --
      List<EsBase> assets = list(readable(ASSET), ASSET);

      // -- ASSERT --
      assertThat(ids(assets)).contains(green, red, other);
    }
  }

  @Nested
  @DisplayName("Marking")
  class Marking {

    @Test
    @DisplayName("given a hidden asset, should not return it")
    void given_hiddenAsset_should_notReturnIt() {
      // -- ACT --
      List<EsBase> assets = list(hiding(Set.of(red), ASSET), ASSET);

      // -- ASSERT --
      assertThat(ids(assets)).contains(green, other).doesNotContain(red);
    }

    @Test
    @DisplayName("given a hidden asset, should keep its scenario but strip it from the targets")
    void given_hiddenAsset_should_keepParentWithoutIt() {
      // -- ACT --
      List<EsBase> scenarios = list(hiding(Set.of(red), SCENARIO), SCENARIO);

      // -- ASSERT --
      EsScenario s1 =
          (EsScenario)
              scenarios.stream().filter(s -> s.getBase_id().equals(scenario1)).findFirst().get();
      assertThat(s1.getBase_assets_side()).contains(green).doesNotContain(red);
    }

    @Test
    @DisplayName("given a hidden asset, should drop its bucket from a by-asset breakdown")
    void given_hiddenAsset_should_dropItsBucket() {
      // -- ACT --
      List<String> buckets = injectsByAsset(hiding(Set.of(red), INJECT, ASSET));

      // -- ASSERT --
      assertThat(buckets).contains(green, other).doesNotContain(red);
    }
  }

  @Nested
  @DisplayName("Filters on dependency fields: visible references only")
  class DependencyFieldFilters {

    /**
     * The reader reads injects; RED is hidden. S1's inject targets GREEN and RED, S3's RED only.
     */
    private EngineReadScope hidingRed() {
      return hiding(Set.of(red), INJECT);
    }

    private List<String> injectsMatching(
        EngineReadScope scope, Map<String, String> parameters, Filters.Filter... filters) {
      ListConfiguration config =
          engineService.createListConfiguration(
              INJECT, Map.of("base_id", List.of(injectGreenRed, injectOther, injectRedOnly)));
      config.setTimeRange(CustomDashboardTimeRange.ALL_TIME);
      List<Filters.Filter> all = new ArrayList<>(config.getPerspective().getFilter().getFilters());
      all.addAll(List.of(filters));
      config.getPerspective().getFilter().setFilters(all);
      ListRuntime runtime = new ListRuntime(config, parameters, Map.of(), new Pagination(0, 100));
      return ids(engineService.entities(TxCtx.allTenants(), scope, runtime).getEsDatas());
    }

    private List<String> injectsMatching(Filters.Filter... filters) {
      return injectsMatching(hidingRed(), Map.of(), filters);
    }

    private Filters.Filter targets(Filters.FilterOperator operator, String... values) {
      Filters.Filter filter = new Filters.Filter();
      filter.setKey("base_assets_side");
      filter.setMode(Filters.FilterMode.or);
      filter.setOperator(operator);
      filter.setValues(List.of(values));
      return filter;
    }

    @Test
    @DisplayName("given eq RED, should match no inject")
    void given_eqHidden_should_matchNothing() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.eq, red))).isEmpty();
    }

    @Test
    @DisplayName("given eq GREEN, should match the inject targeting GREEN and RED")
    void given_eqVisible_should_matchItsInject() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.eq, green)))
          .containsExactly(injectGreenRed);
    }

    @Test
    @DisplayName("given eq GREEN and RED in and mode, should match no inject")
    void given_eqVisibleAndHiddenInAndMode_should_matchNothing() {
      // An admin finds the inject targeting both; the reader sees GREEN only on it, so "targets
      // GREEN and RED" holds for none of what it sees.
      Filters.Filter both = targets(Filters.FilterOperator.eq, green, red);
      both.setMode(Filters.FilterMode.and);

      assertThat(injectsMatching(EngineReadScope.unrestricted(null), Map.of(), both))
          .containsExactly(injectGreenRed);
      assertThat(injectsMatching(both)).isEmpty();
    }

    @Test
    @DisplayName("given not_eq RED, should match every inject")
    void given_notEqHidden_should_matchEverything() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.not_eq, red)))
          .containsExactlyInAnyOrder(injectGreenRed, injectOther, injectRedOnly);
    }

    @Test
    @DisplayName("given not_eq GREEN, should match the injects not targeting GREEN")
    void given_notEqVisible_should_excludeItsInject() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.not_eq, green)))
          .containsExactlyInAnyOrder(injectOther, injectRedOnly);
    }

    @Test
    @DisplayName("given empty, should match the inject whose only target is hidden")
    void given_empty_should_matchInjectWithOnlyHiddenTargets() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.empty)))
          .containsExactly(injectRedOnly);
    }

    @Test
    @DisplayName("given not_empty, should match the injects with a visible target")
    void given_notEmpty_should_matchInjectsWithVisibleTargets() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.not_empty)))
          .containsExactlyInAnyOrder(injectGreenRed, injectOther);
    }

    @Test
    @DisplayName("given contains a part of RED's id, should match no inject")
    void given_containsHiddenIdPart_should_matchNothing() {
      assertThat(injectsMatching(targets(Filters.FilterOperator.contains, red.substring(4, 16))))
          .isEmpty();
    }

    @Test
    @DisplayName("given a range around RED's id, should match no inject")
    void given_rangeAroundHiddenId_should_matchNothing() {
      assertThat(
              injectsMatching(
                  targets(Filters.FilterOperator.gte, red),
                  targets(Filters.FilterOperator.lte, red)))
          .isEmpty();
    }

    @Test
    @DisplayName("given RED through a dashboard parameter, should match no inject")
    void given_hiddenIdThroughParameter_should_matchNothing() {
      assertThat(
              injectsMatching(
                  hidingRed(),
                  Map.of("target-parameter", red),
                  targets(Filters.FilterOperator.eq, "target-parameter")))
          .isEmpty();
    }

    @Test
    @DisplayName("given an unrestricted reader, should match on every reference as before")
    void given_unrestrictedReader_should_matchHiddenReferences() {
      assertThat(
              injectsMatching(
                  EngineReadScope.unrestricted(null),
                  Map.of(),
                  targets(Filters.FilterOperator.eq, red)))
          .containsExactlyInAnyOrder(injectGreenRed, injectRedOnly);
    }
  }

  @Nested
  @DisplayName("Grant")
  class Grant {

    private EngineReadScope grantedOnScenario1() {
      return EngineReadScope.forTenant(
          Tenant.DEFAULT_TENANT_UUID,
          Set.of(SCENARIO, INJECT),
          Map.of(
              SCENARIO,
              List.of(new EngineReadScope.GrantKey("base_id", Set.of(scenario1))),
              INJECT,
              List.of(new EngineReadScope.GrantKey("base_scenario_side", Set.of(scenario1)))),
          Set.of());
    }

    @Test
    @DisplayName("given a grant on S1 only, should return S1 and not S2")
    void given_grantOnS1_should_returnOnlyS1() {
      // -- ACT --
      List<String> scenarios = ids(list(grantedOnScenario1(), SCENARIO));

      // -- ASSERT --
      assertThat(scenarios).contains(scenario1).doesNotContain(scenario2);
    }

    @Test
    @DisplayName("given a grant on S1 only, should only count S1's injects by asset")
    void given_grantOnS1_should_onlyCountS1Injects() {
      // -- ACT --
      List<String> buckets = injectsByAsset(grantedOnScenario1());

      // -- ASSERT --
      assertThat(buckets).contains(green, red).doesNotContain(other);
    }
  }

  @Nested
  @DisplayName("Fail closed")
  class FailClosed {

    @Test
    @DisplayName("given no scope, should refuse the read")
    void given_noScope_should_refuse() {
      // -- ACT / ASSERT --
      assertThatThrownBy(() -> list(null, ASSET)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("given an unrestricted scope, should return everything")
    void given_unrestricted_should_returnEverything() {
      // -- ACT --
      List<String> assets = ids(list(EngineReadScope.unrestricted(null), ASSET));

      // -- ASSERT --
      assertThat(assets).contains(green, red, other);
    }
  }
}
