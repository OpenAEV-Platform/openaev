package io.openaev.api.catalog.dto.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.scenario.dto.ScenarioTargetsInput;
import io.openaev.service.catalog.scenario.WorkflowChoice;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Choices of a scenario install and launch")
class CatalogScenarioInstallInputTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  @DisplayName("Reading an install request")
  class ReadingAnInstall {

    @Test
    @DisplayName("of a time-based scenario takes the targets next to its type")
    void given_timeBasedBody_should_readItsTargets() throws Exception {
      // Act
      CatalogScenarioInstallInput input =
          mapper.readValue(
              "{\"scenario_type\":\"time-based\",\"team_ids\":[\"t1\"]}",
              CatalogScenarioInstallInput.class);

      // Assert
      assertThat(input.toChoice())
          .isEqualTo(new TargetChoice(List.of(), List.of(), List.of("t1"), List.of()));
    }

    @Test
    @DisplayName("of a time-based scenario with no target chooses nothing")
    void given_timeBasedBodyWithoutTargets_should_chooseNothing() throws Exception {
      // Act
      CatalogScenarioInstallInput input =
          mapper.readValue("{\"scenario_type\":\"time-based\"}", CatalogScenarioInstallInput.class);

      // Assert
      assertThat(input.toChoice()).isEqualTo(TargetChoice.NONE);
    }

    @Test
    @DisplayName("of a chained scenario takes its workflow")
    void given_chainedBody_should_readItsWorkflow() throws Exception {
      // Act
      CatalogScenarioInstallInput input =
          mapper.readValue("{\"scenario_type\":\"chained\"}", CatalogScenarioInstallInput.class);

      // Assert
      assertThat(input.toChoice()).isEqualTo(WorkflowChoice.NONE);
    }
  }

  @Test
  @DisplayName("A time-based install is written flat, its type next to its targets")
  void given_timeBasedInstall_when_written_should_keepTheTargetsFlat() throws Exception {
    // Arrange
    CatalogScenarioInstallInput input =
        new TimeBasedScenarioInstallInput(
            new ScenarioTargetsInput(List.of("a1"), null, null, null));

    // Act
    JsonNode json = mapper.readTree(mapper.writeValueAsString(input));

    // Assert
    assertThat(json.path("scenario_type").asText()).isEqualTo("time-based");
    assertThat(json.path("asset_ids").get(0).asText()).isEqualTo("a1");
    assertThat(json.has("targets")).isFalse();
  }

  @Nested
  @DisplayName("Reading launch targets")
  class ReadingLaunchTargets {

    @Test
    @DisplayName("takes the targets alone")
    void given_launchBody_should_readTargets() throws Exception {
      // Act
      ScenarioTargetsInput input =
          mapper.readValue("{\"asset_ids\":[\"a1\"]}", ScenarioTargetsInput.class);

      // Assert
      assertThat(ScenarioTargetsInput.choiceOf(input))
          .isEqualTo(new TargetChoice(List.of("a1"), List.of(), List.of(), List.of()));
    }

    @Test
    @DisplayName("chooses nothing without a body")
    void given_noBody_should_chooseNothing() {
      // Act
      TargetChoice choice = ScenarioTargetsInput.choiceOf(null);

      // Assert
      assertThat(choice).isEqualTo(TargetChoice.NONE);
      assertThat(choice.isEmpty()).isTrue();
    }
  }
}
