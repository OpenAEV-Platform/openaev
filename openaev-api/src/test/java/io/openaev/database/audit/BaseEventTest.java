package io.openaev.database.audit;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Base;
import io.openaev.database.model.Command;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// The frontend stores each streamed entity under the event's attribute_schema, and reads assets
// from one bucket per subtype (actions/assets/asset-schema.ts): an event sent under any other
// schema lands in a bucket no screen reads.
class BaseEventTest {

  private final ObjectMapper mapper = ObjectMapperHelper.openAEVJsonMapper();

  private BaseEvent eventFor(String eventType, Base entity) {
    entity.setId("entity-id");
    return new BaseEvent(eventType, entity, mapper);
  }

  @Nested
  @DisplayName("Asset events use the frontend bucket of their subtype")
  class AssetSubtypes {

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An endpoint is streamed under endpoints")
    void given_endpoint_should_streamUnderEndpoints(String eventType) {
      // Arrange
      Base endpoint = EndpointFixture.createEndpoint();

      // Act
      BaseEvent event = eventFor(eventType, endpoint);

      // Assert
      assertThat(event.getSchema()).isEqualTo("endpoints");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("A security platform is streamed under securityplatforms")
    void given_securityPlatform_should_streamUnderSecurityPlatforms(String eventType) {
      // Arrange
      Base securityPlatform = SecurityPlatformFixture.createDefault("EDR", "EDR");

      // Act
      BaseEvent event = eventFor(eventType, securityPlatform);

      // Assert
      assertThat(event.getSchema()).isEqualTo("securityplatforms");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An AI target is streamed under aitargets")
    void given_aiTarget_should_streamUnderAiTargets(String eventType) {
      // Arrange
      Asset aiTarget = AssetFixture.createDefaultAsset("LLM");
      aiTarget.setCategory(AssetCategory.AI_TARGET);

      // Act
      BaseEvent event = eventFor(eventType, aiTarget);

      // Assert
      assertThat(event.getSchema()).isEqualTo("aitargets");
      assertThat(event.getAttributeId()).isEqualTo("asset_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("Any other asset keeps the assets schema")
    void given_otherAsset_should_streamUnderAssets(String eventType) {
      // Arrange
      Asset cloudResource = AssetFixture.createDefaultAsset("Bucket");
      cloudResource.setCategory(AssetCategory.CLOUD_RESOURCE);

      // Act
      BaseEvent event = eventFor(eventType, cloudResource);

      // Assert
      assertThat(event.getSchema()).isEqualTo("assets");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("The stream schema is not serialized in the payload")
    void given_endpoint_should_notSerializeTheStreamSchema(String eventType) {
      // Arrange
      Base endpoint = EndpointFixture.createEndpoint();

      // Act
      BaseEvent event = eventFor(eventType, endpoint);

      // Assert
      assertThat(event.getInstanceData().toString()).doesNotContain("endpoints");
    }
  }

  @Nested
  @DisplayName("Other hierarchies keep one bucket for every subtype")
  class OtherHierarchies {

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("An expectation subtype is streamed under injectexpectations")
    void given_expectationSubtype_should_streamUnderInjectExpectations(String eventType) {
      // Act
      BaseEvent event = eventFor(eventType, new PreventionInjectExpectation());

      // Assert
      assertThat(event.getSchema()).isEqualTo("injectexpectations");
    }

    @ParameterizedTest
    @ValueSource(strings = {DATA_UPDATE, DATA_DELETE})
    @DisplayName("A payload subtype is streamed under payloads")
    void given_payloadSubtype_should_streamUnderPayloads(String eventType) {
      // Act
      BaseEvent event = eventFor(eventType, new Command());

      // Assert
      assertThat(event.getSchema()).isEqualTo("payloads");
    }
  }

  // The stream checks each event against the reader's clearance after commit: the markings are
  // captured with the event, while the entity is still loaded.
  @Nested
  @DisplayName("Events carry the markings of their entity")
  class StreamMarkings {

    @ParameterizedTest
    @ValueSource(strings = {DATA_PERSIST, DATA_UPDATE, DATA_DELETE})
    @DisplayName("A marked asset event carries the asset's markings")
    void given_markedAsset_should_carryItsMarkings(String eventType) {
      // Arrange
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setMarkingIds(new String[] {"tlp-red", "pap-amber"});

      // Act
      BaseEvent event = eventFor(eventType, endpoint);

      // Assert
      assertThat(event.getStreamMarkings()).containsExactlyInAnyOrder("tlp-red", "pap-amber");
    }

    @Test
    @DisplayName("The markings are those of the entity when the event was built")
    void given_markingsChangedAfterTheEvent_should_keepThoseOfTheEvent() {
      // Arrange
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setMarkingIds(new String[] {"tlp-red"});
      BaseEvent event = eventFor(DATA_UPDATE, endpoint);

      // Act
      endpoint.setMarkingIds(new String[] {"tlp-clear"});

      // Assert
      assertThat(event.getStreamMarkings()).containsExactly("tlp-red");
    }

    @Test
    @DisplayName("An unmarked asset event carries no marking")
    void given_unmarkedAsset_should_carryNoMarking() {
      // Arrange
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setMarkingIds(null);

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, endpoint);

      // Assert
      assertThat(event.getStreamMarkings()).isEmpty();
    }

    @Test
    @DisplayName("An entity with no marking of its own carries no marking")
    void given_entityWithoutMarkings_should_carryNoMarking() {
      // Act
      BaseEvent event = eventFor(DATA_UPDATE, new Command());

      // Assert
      assertThat(event.getStreamMarkings()).isEmpty();
    }

    @Test
    @DisplayName("The markings are not serialized in the event")
    void given_markedAsset_should_notSerializeTheStreamMarkings() {
      // Arrange
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setMarkingIds(new String[] {"tlp-red"});

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, endpoint);

      // Assert
      assertThat(mapper.valueToTree(event).has("stream_markings")).isFalse();
      assertThat(mapper.valueToTree(event).has("streamMarkings")).isFalse();
    }
  }

  // Phase 2: the objects derived from an asset (MarkingDerivedTables) are hidden with it. The event
  // carries the markings of the assets it is about, read while the entity is still loaded.
  @Nested
  @DisplayName("Stream asset links")
  class StreamAssetLinks {

    private static final MarkingCtx AMBER =
        MarkingCtx.forMarkings(List.of("tlp-clear", "tlp-green", "tlp-amber"));

    private static Endpoint endpoint(String id, String... markingIds) {
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setId(id);
      endpoint.setMarkingIds(markingIds.length == 0 ? null : markingIds);
      return endpoint;
    }

    private static Finding findingOn(Asset... assets) {
      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setId("finding-id");
      finding.setAssets(List.of(assets));
      return finding;
    }

    @Test
    @DisplayName("An agent carries the markings of its asset")
    void given_agentOfMarkedAsset_should_carryTheAssetsMarkings() {
      // Arrange
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpoint("r", "tlp-red"));

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, agent);

      // Assert
      assertThat(event.getStreamMarkings()).containsExactly("tlp-red");
    }

    @Test
    @DisplayName("An expectation on an asset carries the markings of the asset")
    void given_expectationOnMarkedAsset_should_carryTheAssetsMarkings() {
      // Arrange
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
      expectation.setAsset(endpoint("r", "tlp-red"));

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, expectation);

      // Assert
      assertThat(event.getStreamMarkings()).containsExactly("tlp-red");
    }

    @Test
    @DisplayName("An expectation with no asset carries no marking")
    void given_expectationWithoutAsset_should_carryNoMarking() {
      // Act
      BaseEvent event = eventFor(DATA_UPDATE, new PreventionInjectExpectation());

      // Assert
      assertThat(event.getStreamMarkings()).isEmpty();
      assertThat(event.isVisibleUnder(MarkingCtx.none())).isTrue();
    }

    @Test
    @DisplayName("When the markings cannot be read, only a reader seeing everything receives it")
    void given_unreadableMarkings_should_beHiddenFromEveryRestrictedReader() {
      // Arrange
      Endpoint unreadable =
          new Endpoint() {
            @Override
            public String[] getMarkingIds() {
              throw new IllegalStateException("could not initialize proxy - no Session");
            }
          };
      unreadable.setId("r");
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
      expectation.setAsset(unreadable);

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, expectation);

      // Assert
      assertThat(event.isVisibleUnder(AMBER)).isFalse();
      assertThat(event.isVisibleUnder(MarkingCtx.all())).isTrue();
    }

    @Test
    @DisplayName("A finding carries its assets, each with its markings")
    void given_finding_should_carryItsAssetsWithTheirMarkings() {
      // Act
      BaseEvent event =
          eventFor(DATA_UPDATE, findingOn(endpoint("a", "tlp-green"), endpoint("r", "tlp-red")));

      // Assert
      assertThat(event.getStreamAssetReferences())
          .containsExactly(
              new StreamAssetReference(
                  "finding_assets",
                  Map.of("a", Set.of("tlp-green"), "r", Set.of("tlp-red")),
                  true));
    }

    @Test
    @DisplayName("An inject carries its targets, each with its markings")
    void given_inject_should_carryItsTargetsWithTheirMarkings() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");
      inject.setAssets(List.of(endpoint("a", "tlp-green"), endpoint("u")));

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, inject);

      // Assert
      assertThat(event.getStreamAssetReferences())
          .containsExactly(
              new StreamAssetReference(
                  "inject_assets", Map.of("a", Set.of("tlp-green"), "u", Set.of()), false));
    }

    @Test
    @DisplayName("An asset group carries its static and dynamic assets, each with its markings")
    void given_assetGroup_should_carryItsAssetsWithTheirMarkings() {
      // Arrange
      AssetGroup group = AssetGroupFixture.createDefaultAssetGroup("group");
      group.setId("group-id");
      group.setAssets(List.of(endpoint("r", "tlp-red")));
      group.setDynamicAssets(List.of(endpoint("a", "tlp-green")));

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, group);

      // Assert
      assertThat(event.getStreamAssetReferences())
          .containsExactlyInAnyOrder(
              new StreamAssetReference("asset_group_assets", Map.of("r", Set.of("tlp-red")), false),
              new StreamAssetReference(
                  "asset_group_dynamic_assets", Map.of("a", Set.of("tlp-green")), false));
    }

    @Test
    @DisplayName("An entity referencing no asset carries no reference")
    void given_findingWithoutAsset_should_carryNoReference() {
      // Act
      BaseEvent event = eventFor(DATA_UPDATE, findingOn());

      // Assert
      assertThat(event.getStreamAssetReferences()).isEmpty();
      assertThat(event.hasMarkingLinks()).isFalse();
    }

    @Test
    @DisplayName("A finding with one visible asset is visible, listing only that asset")
    void given_findingOnAVisibleAndAHiddenAsset_should_beVisibleWithTheVisibleOne() {
      // Arrange
      BaseEvent event =
          eventFor(DATA_UPDATE, findingOn(endpoint("a", "tlp-green"), endpoint("r", "tlp-red")));

      // Act & Assert
      assertThat(event.isVisibleUnder(AMBER)).isTrue();
      assertThat(event.getStreamAssetReferences().getFirst().visibleAssetIds(AMBER))
          .containsExactly("a");
    }

    @Test
    @DisplayName("A finding whose assets are all hidden is hidden")
    void given_findingOnHiddenAssetsOnly_should_beHidden() {
      // Arrange
      BaseEvent event = eventFor(DATA_DELETE, findingOn(endpoint("r", "tlp-red")));

      // Act & Assert
      assertThat(event.isVisibleUnder(AMBER)).isFalse();
      assertThat(event.isVisibleUnder(MarkingCtx.all())).isTrue();
    }

    private static Inject injectRunOn(Endpoint asset) {
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setId("agent-id");
      agent.setAsset(asset);
      PreventionInjectExpectation onAsset = new PreventionInjectExpectation();
      onAsset.setId("expectation-on-asset");
      onAsset.setAsset(asset);
      ManualInjectExpectation onTeam = new ManualInjectExpectation();
      onTeam.setId("expectation-on-team");
      ExecutionTrace agentTrace = new ExecutionTrace();
      agentTrace.setId("trace-of-agent");
      agentTrace.setAgent(agent);
      ExecutionTrace globalTrace = new ExecutionTrace();
      globalTrace.setId("trace-global");
      InjectStatus status = new InjectStatus();
      status.setId("status-id");
      status.setTraces(List.of(globalTrace, agentTrace));
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");
      inject.setExpectations(List.of(onAsset, onTeam));
      inject.setStatus(status);
      return inject;
    }

    @Test
    @DisplayName(
        "An inject carries its embedded expectations and traces, each with its asset's markings")
    void given_injectRunOnAMarkedAsset_should_carryItsEmbeddedObjectsWithTheirMarkings() {
      // Act
      BaseEvent event = eventFor(DATA_UPDATE, injectRunOn(endpoint("r", "tlp-red")));

      // Assert
      assertThat(event.getStreamEmbeddedElements())
          .containsExactlyInAnyOrder(
              new StreamEmbeddedElements(
                  "inject_expectations",
                  "inject_expectation_id",
                  Map.of(
                      "expectation-on-asset", Set.of("tlp-red"), "expectation-on-team", Set.of())),
              new StreamEmbeddedElements(
                  "inject_status.status_traces",
                  "execution_trace_id",
                  Map.of("trace-of-agent", Set.of("tlp-red"), "trace-global", Set.of())));
      assertThat(event.hasMarkingLinks()).isTrue();
      assertThat(event.isVisibleUnder(AMBER)).isTrue();
    }

    @Test
    @DisplayName("An embedded object hidden from the clearance is not visible, the others are")
    void given_embeddedObjects_should_keepOnlyThoseTheClearanceAllows() {
      // Arrange
      BaseEvent event = eventFor(DATA_UPDATE, injectRunOn(endpoint("r", "tlp-red")));

      // Act & Assert
      assertThat(event.getStreamEmbeddedElements())
          .hasSize(2)
          .allSatisfy(
              elements ->
                  assertThat(elements.visibleIds(AMBER))
                      .containsExactlyInAnyOrder(
                          "inject_expectations".equals(elements.path())
                              ? "expectation-on-team"
                              : "trace-global"));
    }

    @Test
    @DisplayName("An inject not run yet carries no embedded object")
    void given_injectNotRun_should_carryNoEmbeddedObject() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");

      // Act
      BaseEvent event = eventFor(DATA_UPDATE, inject);

      // Assert
      assertThat(event.getStreamEmbeddedElements()).isEmpty();
    }

    @Test
    @DisplayName("An inject whose targets are all hidden stays visible, with no target")
    void given_injectOnHiddenAssetsOnly_should_stayVisible() {
      // Arrange
      Inject inject = InjectFixture.getDefaultInject();
      inject.setId("inject-id");
      inject.setAssets(List.of(endpoint("r", "tlp-red")));
      BaseEvent event = eventFor(DATA_UPDATE, inject);

      // Act & Assert
      assertThat(event.isVisibleUnder(AMBER)).isTrue();
      assertThat(event.getStreamAssetReferences().getFirst().visibleAssetIds(AMBER)).isEmpty();
    }
  }
}
