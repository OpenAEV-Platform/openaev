package io.openaev.service.expectation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.CollectorFixture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PreventionBehavior")
class PreventionBehaviorTest {

  @Mock private CollectorService collectorService;

  @Nested
  @DisplayName("supports")
  class Supports {

    @Test
    @DisplayName("given_prevention_expectation_should_return_true")
    void given_prevention_expectation_should_return_true() {
      // Arrange
      PreventionBehavior behavior = new PreventionBehavior(collectorService);
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isTrue();
    }

    @Test
    @DisplayName("given_non_prevention_expectation_should_return_false")
    void given_non_prevention_expectation_should_return_false() {
      // Arrange
      PreventionBehavior behavior = new PreventionBehavior(collectorService);
      DetectionInjectExpectation expectation = new DetectionInjectExpectation();

      // Act
      boolean supported = behavior.supports(expectation);

      // Assert
      assertThat(supported).isFalse();
    }
  }

  @Nested
  @DisplayName("initializeResults")
  class InitializeResults {

    @Test
    @DisplayName("given_expectation_with_agent_should_set_default_collector_results")
    void given_expectation_with_agent_should_set_default_collector_results() {
      // Arrange
      PreventionBehavior behavior = new PreventionBehavior(collectorService);
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
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
      PreventionBehavior behavior = new PreventionBehavior(collectorService);
      PreventionInjectExpectation expectation = new PreventionInjectExpectation();
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
}
