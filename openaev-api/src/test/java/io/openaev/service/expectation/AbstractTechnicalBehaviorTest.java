package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exercise.form.ExpectationUpdateInput;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractTechnicalBehavior")
class AbstractTechnicalBehaviorTest {

  @Mock private CollectorService collectorService;

  @Nested
  @DisplayName("initializeResults")
  class InitializeResults {

    @Test
    @DisplayName("given_expectation_with_agent_should_set_default_collector_results")
    void given_expectation_with_agent_should_set_default_collector_results() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      Agent agent = AgentFixture.createDefaultAgentService();
      Collector collector = CollectorFixture.createDefaultCollector("collector-1");
      expectation.setAgent(agent);
      when(collectorService.securityPlatformCollectors()).thenReturn(List.of(collector));

      // Act
      behavior.initializeResults(expectation);

      // Assert
      assertThat(expectation.getResults()).hasSize(1);
      InjectExpectationResult result = expectation.getResults().getFirst();
      assertThat(result.getSourceId()).isEqualTo("collector-1");
      assertThat(result.getSourceName()).isEqualTo("collector-1");
      assertThat(result.getResult()).isNull();
      assertThat(result.getScore()).isNull();
    }

    @Test
    @DisplayName("given_expectation_without_agent_should_not_change_results")
    void given_expectation_without_agent_should_not_change_results() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      List<InjectExpectationResult> existingResults = new ArrayList<>();
      existingResults.add(
          InjectExpectationResult.builder().sourceId("existing").result("KO").build());
      expectation.setResults(existingResults);

      // Act
      behavior.initializeResults(expectation);

      // Assert
      assertThat(expectation.getResults()).isSameAs(existingResults);
      assertThat(expectation.getResults()).hasSize(1);
      assertThat(expectation.getResults().getFirst().getSourceId()).isEqualTo("existing");
      assertThat(expectation.getResults().getFirst().getResult()).isEqualTo("KO");
    }
  }

  @Nested
  @DisplayName("applyResultToLeaves")
  class ApplyResultToLeaves {

    @Test
    @DisplayName("given_asset_group_expectation_should_throw")
    void given_asset_group_expectation_should_throw() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setAssetGroup(new AssetGroup());
      ExpectationUpdateInput input =
          ExpectationUpdateInput.builder()
              .sourceId("source-1")
              .sourceType("manual")
              .sourceName("source")
              .sourcePlatform("N/A")
              .score(42.0)
              .build();

      // Act + Assert
      assertThatThrownBy(() -> behavior.applyResultToLeaves(expectation, input))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Not possible to update Asset Group directly");
    }

    @Test
    @DisplayName("given_asset_expectation_with_agents_should_delegate_to_agent_expectations")
    void given_asset_expectation_with_agents_should_delegate_to_agent_expectations() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      behavior.agentless = false;

      Endpoint sharedAsset = EndpointFixture.createEndpoint("asset-1");
      sharedAsset.setId("asset-1");

      DetectionInjectExpectation assetExpectation = new DetectionInjectExpectation();
      assetExpectation.setId("asset-expectation");
      assetExpectation.setAsset(sharedAsset);

      DetectionInjectExpectation agentExpectationOne = new DetectionInjectExpectation();
      agentExpectationOne.setId("agent-expectation-1");
      agentExpectationOne.setAsset(sharedAsset);
      agentExpectationOne.setAgent(AgentFixture.createDefaultAgentService());

      DetectionInjectExpectation agentExpectationTwo = new DetectionInjectExpectation();
      agentExpectationTwo.setId("agent-expectation-2");
      agentExpectationTwo.setAsset(sharedAsset);
      agentExpectationTwo.setAgent(AgentFixture.createDefaultAgentSession());

      Inject inject = new Inject();
      inject.setExpectations(
          new ArrayList<>(List.of(assetExpectation, agentExpectationOne, agentExpectationTwo)));
      assetExpectation.setInject(inject);
      agentExpectationOne.setInject(inject);
      agentExpectationTwo.setInject(inject);

      ExpectationUpdateInput input =
          ExpectationUpdateInput.builder()
              .sourceId("source-1")
              .sourceType("manual")
              .sourceName("source")
              .sourcePlatform("N/A")
              .score(84.0)
              .build();

      // Act
      behavior.applyResultToLeaves(assetExpectation, input);

      // Assert
      assertThat(behavior.computedExpectations)
          .containsExactlyInAnyOrder(agentExpectationOne, agentExpectationTwo);
      assertThat(behavior.computedExpectations).doesNotContain(assetExpectation);
    }
  }

  @Nested
  @DisplayName("propagate")
  class Propagate {

    @Test
    @DisplayName("given_non_agentless_expectation_should_propagate_to_asset_then_asset_group")
    void given_non_agentless_expectation_should_propagate_to_asset_then_asset_group() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      behavior.agentless = false;
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();

      // Act
      List<BaseInjectExpectation> propagated = behavior.propagate(expectation);

      // Assert
      assertThat(behavior.propagateToAssetCalled).isTrue();
      assertThat(behavior.propagateToAssetGroupCalled).isTrue();
      assertThat(propagated)
          .containsExactly(behavior.assetReturn.getFirst(), behavior.assetGroupReturn.getFirst());
    }

    @Test
    @DisplayName("given_agentless_expectation_should_skip_asset_and_propagate_only_to_asset_group")
    void given_agentless_expectation_should_skip_asset_and_propagate_only_to_asset_group() {
      // Arrange
      TestTechnicalBehavior behavior = new TestTechnicalBehavior(collectorService);
      behavior.agentless = true;
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();

      // Act
      List<BaseInjectExpectation> propagated = behavior.propagate(expectation);

      // Assert
      assertThat(behavior.propagateToAssetCalled).isFalse();
      assertThat(behavior.propagateToAssetGroupCalled).isTrue();
      assertThat(propagated).containsExactlyElementsOf(behavior.assetGroupReturn);
    }
  }

  private static final class TestTechnicalBehavior extends AbstractTechnicalBehavior {

    private boolean agentless;
    private boolean propagateToAssetCalled;
    private boolean propagateToAssetGroupCalled;
    private final List<BaseInjectExpectation> computedExpectations = new ArrayList<>();
    private final List<BaseInjectExpectation> assetReturn =
        List.of(buildExpectation("asset-return"));
    private final List<BaseInjectExpectation> assetGroupReturn =
        List.of(buildExpectation("asset-group-return"));

    private TestTechnicalBehavior(CollectorService collectorService) {
      super(collectorService);
    }

    @Override
    public boolean supports(BaseInjectExpectation expectation) {
      return true;
    }

    @Override
    protected boolean isAgentless(BaseInjectExpectation expectation) {
      return agentless;
    }

    @Override
    protected void computeInjectExpectationForAgentOrAssetAgentless(
        BaseInjectExpectation expectation, ExpectationUpdateInput input) {
      this.computedExpectations.add(expectation);
    }

    @Override
    protected List<BaseInjectExpectation> propagateToAsset(
        BaseInjectExpectation expectation,
        java.util.function.Function<Double, InjectExpectationResult> addResult) {
      this.propagateToAssetCalled = true;
      return assetReturn;
    }

    @Override
    protected List<BaseInjectExpectation> propagateToAssetGroup(
        BaseInjectExpectation expectation,
        java.util.function.Function<Double, InjectExpectationResult> addResult) {
      this.propagateToAssetGroupCalled = true;
      return assetGroupReturn;
    }

    private static DetectionInjectExpectation buildExpectation(String id) {
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();
      expectation.setId(id);
      return expectation;
    }
  }
}
