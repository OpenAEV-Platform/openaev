package io.openaev.service.stream;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static io.openaev.utils.fixtures.FindingOccurrenceFixture.createOccurrence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.MarkedTable;
import io.openaev.config.MarkedTables;
import io.openaev.config.MarkingDerivedTables;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Base;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * What an event shows each clearance, with the real rules ({@link StreamMarkingRules}) and the
 * database reads replaced by rows in memory. The reads themselves run against the database in
 * {@code StreamVisibilityServiceReadTest}.
 */
@DisplayName("What a stream event shows each clearance")
class StreamVisibilityServiceTest {

  private static final String TENANT_ID = "tenant-id";
  private static final MarkingCtx AMBER =
      MarkingCtx.forMarkings(List.of("tlp-clear", "tlp-green", "tlp-amber"));
  private static final MarkingCtx RED =
      MarkingCtx.forMarkings(List.of("tlp-clear", "tlp-green", "tlp-amber", "tlp-red"));
  private static final StreamMarkingRules RULES =
      new StreamMarkingRules(
          new MarkedTables(Map.of("assets", new MarkedTable("assets")))
              .withDerived(MarkingDerivedTables.ALL),
          entities(),
          ObjectMapperHelper.openAEVJsonMapper());

  private final ObjectMapper mapper = ObjectMapperHelper.openAEVJsonMapper();
  private final TenantScopedTransaction transaction = mock(TenantScopedTransaction.class);
  private final EntityManager entityManager = mock(EntityManager.class);

  /** The rows the reads find: the markings of each asset, the asset of each agent. */
  private final Map<String, String[]> assetMarkings = new HashMap<>();

  private final Map<String, String> agentAssets = new HashMap<>();

  private StreamVisibilityService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    service = new StreamVisibilityService(RULES, transaction, mapper);
    ReflectionTestUtils.setField(service, "entityManager", entityManager);
    when(transaction.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    when(entityManager.createQuery(anyString(), any(Class.class)))
        .thenAnswer(invocation -> query(invocation.getArgument(0)));
    assetMarkings.put("g", new String[] {"tlp-green"});
    assetMarkings.put("r", new String[] {"tlp-red"});
    agentAssets.put("agent-on-r", "r");
  }

  @SuppressWarnings("unchecked")
  private TypedQuery<Object[]> query(String jpql) {
    TypedQuery<Object[]> query = mock(TypedQuery.class);
    Map<String, Collection<String>> ids = new HashMap<>();
    when(query.setParameter(any(String.class), any()))
        .thenAnswer(
            invocation -> {
              ids.put(invocation.getArgument(0), invocation.getArgument(1));
              return query;
            });
    Matcher entity = Pattern.compile("from (\\w+) e").matcher(jpql);
    entity.find();
    when(query.getResultList())
        .thenAnswer(
            invocation -> {
              List<Object[]> rows = new ArrayList<>();
              for (String id : ids.get("ids")) {
                if ("Asset".equals(entity.group(1)) && assetMarkings.containsKey(id)) {
                  rows.add(new Object[] {id, assetMarkings.get(id)});
                } else if ("Agent".equals(entity.group(1)) && agentAssets.containsKey(id)) {
                  rows.add(new Object[] {id, agentAssets.get(id)});
                }
              }
              return rows;
            });
    return query;
  }

  private StreamVisibility visibilityOf(Base entity) {
    return service.visibilityOf(new BaseEvent(DATA_UPDATE, entity, mapper), TENANT_ID);
  }

  private static Endpoint endpoint(String id, String... markingIds) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    endpoint.setName(id);
    endpoint.setMarkingIds(markingIds.length == 0 ? null : markingIds);
    return endpoint;
  }

  private static Agent agentOn(String id, Endpoint asset) {
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setId(id);
    agent.setAsset(asset);
    return agent;
  }

  private static List<String> idsAt(JsonNode payload, String path, String idProperty) {
    JsonNode node = payload;
    for (String step : path.split("\\.")) {
      node = node.path(step);
    }
    return node.valueStream()
        .map(element -> idProperty == null ? element.asText() : element.path(idProperty).asText())
        .toList();
  }

  @Nested
  @DisplayName("The entity itself")
  class Row {

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("an asset is shown by its own markings, with no read")
    void given_asset_should_beShownByItsOwnMarkings(String eventType) {
      // Act
      StreamVisibility visibility =
          service.visibilityOf(
              new BaseEvent(eventType, endpoint("r", "tlp-red"), mapper), TENANT_ID);

      // Assert
      assertThat(visibility.visibleUnder(AMBER)).isFalse();
      assertThat(visibility.visibleUnder(RED)).isTrue();
      verify(entityManager, never()).createQuery(anyString(), any(Class.class));
    }

    @Test
    @DisplayName("an agent, an expectation and a trace are hidden with their asset")
    void given_rowOnAHiddenAsset_should_beHiddenWithIt() {
      // Arrange
      Endpoint asset = endpoint("r", "tlp-red");
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
      expectation.setId("expectation-id");
      expectation.setAsset(asset);
      ExecutionTrace trace = new ExecutionTrace();
      trace.setId("trace-id");
      trace.setAgent(agentOn("agent-on-r", asset));

      // Act & Assert: the trace goes through the chain trace -> agent -> asset
      for (Base row : List.of(agentOn("agent-on-r", asset), expectation, trace)) {
        StreamVisibility visibility = visibilityOf(row);
        assertThat(visibility.visibleUnder(AMBER)).as(row.getClass().getSimpleName()).isFalse();
        assertThat(visibility.visibleUnder(RED)).as(row.getClass().getSimpleName()).isTrue();
      }
    }

    @Test
    @DisplayName("a row whose asset is not found reaches only unrestricted readers")
    void given_assetNotFound_should_failClosed() {
      // Act
      StreamVisibility visibility = visibilityOf(agentOn("agent-id", endpoint("gone")));

      // Assert
      assertThat(visibility.visibleUnder(RED)).isFalse();
      assertThat(visibility.visibleUnder(MarkingCtx.all())).isTrue();
    }

    @Test
    @DisplayName("a row deleted with its parent is told apart from a hidden or unread one")
    void given_rowDeletedWithItsParent_should_beToldApart() {
      // Act
      StreamVisibility deletedWithItsAsset =
          service.visibilityOf(
              new BaseEvent(DATA_DELETE, agentOn("agent-id", endpoint("gone")), mapper), TENANT_ID);
      StreamVisibility onAHiddenAsset =
          service.visibilityOf(
              new BaseEvent(DATA_DELETE, agentOn("agent-on-r", endpoint("r", "tlp-red")), mapper),
              TENANT_ID);
      StreamVisibility unread =
          service.visibilityOf(
              new BaseEvent(DATA_DELETE, agentOn("agent-id", endpoint("gone")), mapper), null);

      // Assert
      assertThat(deletedWithItsAsset.parentDeleted()).isTrue();
      assertThat(onAHiddenAsset.parentDeleted()).isFalse();
      assertThat(unread.parentDeleted()).isFalse();
    }

    @Test
    @DisplayName("with no tenant to read in, a row reaches only unrestricted readers")
    void given_noTenant_should_failClosed() {
      // Act
      StreamVisibility visibility =
          service.visibilityOf(
              new BaseEvent(DATA_UPDATE, agentOn("agent-id", endpoint("g")), mapper), null);

      // Assert
      assertThat(visibility.visibleUnder(RED)).isFalse();
      assertThat(visibility.visibleUnder(MarkingCtx.all())).isTrue();
    }

    @Test
    @DisplayName("a finding is hidden when all its assets are, shown with the visible ones else")
    void given_finding_should_beAboutItsAssets() {
      // Arrange
      Finding onRedOnly = FindingFixture.createDefaultTextFinding();
      onRedOnly.setId("finding-red");
      onRedOnly.setAssets(List.of(endpoint("r")));
      Finding onBoth = FindingFixture.createDefaultTextFinding();
      onBoth.setId("finding-both");
      onBoth.setAssets(List.of(endpoint("g"), endpoint("r")));
      Finding onNone = FindingFixture.createDefaultTextFinding();
      onNone.setId("finding-none");

      // Act
      StreamVisibility red = visibilityOf(onRedOnly);
      StreamVisibility both = visibilityOf(onBoth);
      StreamVisibility none = visibilityOf(onNone);

      // Assert
      assertThat(red.visibleUnder(AMBER)).isFalse();
      assertThat(both.visibleUnder(AMBER)).isTrue();
      assertThat(idsAt(both.payloadFor(AMBER), "finding_assets", null)).containsExactly("g");
      assertThat(none.visibleUnder(MarkingCtx.none())).isTrue();
    }
  }

  @Nested
  @DisplayName("The rows the payload lists or embeds")
  class Payload {

    @Test
    @DisplayName("a finding's groups, serialized whole, keep only the visible assets")
    void given_findingOnAGroupWithAHiddenAsset_should_keepTheVisibleOnesInTheGroup() {
      // Arrange
      AssetGroup group = AssetGroupFixture.createDefaultAssetGroup("group");
      group.setId("group-id");
      group.setAssets(List.of(endpoint("g"), endpoint("r")));
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");
      inject.setAssetGroups(List.of(group));
      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setId("finding-id");
      finding.setOccurrences(
          List.of(createOccurrence(finding, inject, Instant.now(), finding.getField())));
      finding.setAssets(List.of(endpoint("g")));

      // Act
      StreamVisibility visibility = visibilityOf(finding);

      // Assert
      JsonNode amber = visibility.payloadFor(AMBER).path("finding_asset_groups").path(0);
      assertThat(idsAt(amber, "asset_group_assets", null)).containsExactly("g");
      JsonNode red = visibility.payloadFor(RED).path("finding_asset_groups").path(0);
      assertThat(idsAt(red, "asset_group_assets", null)).containsExactly("g", "r");
    }

    @Test
    @DisplayName("a single object embedded whole is removed when hidden")
    void given_collectorOfAHiddenSecurityPlatform_should_removeIt() {
      // Arrange
      SecurityPlatform platform = SecurityPlatformFixture.createDefault("platform", "EDR");
      platform.setId("platform-r");
      platform.setMarkingIds(new String[] {"tlp-red"});
      Collector collector = CollectorFixture.createDefaultCollector("collector");
      collector.setSecurityPlatform(platform);

      // Act
      StreamVisibility visibility = visibilityOf(collector);

      // Assert
      assertThat(visibility.visibleUnder(AMBER)).isTrue();
      assertThat(visibility.payloadFor(AMBER).path("collector_security_platform").isNull())
          .isTrue();
      assertThat(visibility.payloadFor(RED).path("collector_security_platform").path("asset_id"))
          .hasToString("\"platform-r\"");
    }

    @Test
    @DisplayName("an inject keeps the visible targets, expectations and traces")
    void given_injectRunOnAHiddenAsset_should_keepWhatTheClearanceAllows() {
      // Arrange
      Endpoint red = endpoint("r");
      PreventionInjectExpectation onAsset = new PreventionInjectExpectation();
      onAsset.setId("expectation-on-asset");
      onAsset.setAsset(red);
      ManualInjectExpectation onTeam = new ManualInjectExpectation();
      onTeam.setId("expectation-on-team");
      ExecutionTrace agentTrace = new ExecutionTrace();
      agentTrace.setId("trace-of-agent");
      agentTrace.setAgent(agentOn("agent-on-r", red));
      ExecutionTrace globalTrace = new ExecutionTrace();
      globalTrace.setId("trace-global");
      InjectStatus status = new InjectStatus();
      status.setId("status-id");
      status.setTraces(List.of(globalTrace, agentTrace));
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");
      inject.setAssets(List.of(endpoint("g"), red));
      inject.setExpectations(List.of(onAsset, onTeam));
      inject.setStatus(status);

      // Act
      StreamVisibility visibility = visibilityOf(inject);

      // Assert: the inject stays, without what the clearance does not allow
      assertThat(visibility.visibleUnder(AMBER)).isTrue();
      assertThat(visibility.filtersPayload()).isTrue();
      JsonNode amber = visibility.payloadFor(AMBER);
      assertThat(idsAt(amber, "inject_assets", null)).containsExactly("g");
      assertThat(idsAt(amber, "inject_expectations", "inject_expectation_id"))
          .containsExactly("expectation-on-team");
      assertThat(idsAt(amber, "inject_status.status_traces", "execution_trace_id"))
          .containsExactly("trace-global");
      JsonNode redPayload = visibility.payloadFor(RED);
      assertThat(idsAt(redPayload, "inject_assets", null)).containsExactly("g", "r");
      assertThat(idsAt(redPayload, "inject_status.status_traces", "execution_trace_id"))
          .containsExactly("trace-global", "trace-of-agent");
    }
  }

  @Test
  @DisplayName("an entity no rule applies to is sent as it is, with no read")
  void given_noRule_should_beUnfiltered() {
    // Arrange
    io.openaev.database.model.Scenario scenario = ScenarioFixture.getScenario();
    scenario.setId("scenario-id");

    // Act
    StreamVisibility ruleless = visibilityOf(scenario);

    // Assert
    assertThat(ruleless.isUnfiltered()).isTrue();
    verify(transaction, never()).execute(any(TxCtx.class), any(Supplier.class));
  }

  private static List<Class<?>> entities() {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
    return scanner.findCandidateComponents("io.openaev.database.model").stream()
        .<Class<?>>map(
            definition -> {
              try {
                return Class.forName(definition.getBeanClassName());
              } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
              }
            })
        .toList();
  }
}
