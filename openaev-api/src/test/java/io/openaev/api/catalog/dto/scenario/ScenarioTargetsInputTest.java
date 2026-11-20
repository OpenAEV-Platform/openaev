package io.openaev.api.catalog.dto.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.service.catalog.scenario.WorkflowChoice;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Targets chosen at install or launch")
class ScenarioTargetsInputTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  @DisplayName("Reading a request")
  class Reading {

    @Test
    @DisplayName("of a launch takes the targets alone")
    void given_launchBody_should_readTargets() throws Exception {
      // Act
      ScenarioTargetsInput input =
          mapper.readValue("{\"asset_ids\":[\"a1\"]}", ScenarioTargetsInput.class);

      // Assert
      assertThat(input.assetIds()).containsExactly("a1");
    }

    @Test
    @DisplayName("of an install takes the choice of the scenario type it names")
    void given_installBodies_should_readChoiceOfTheirType() throws Exception {
      // Act
      CatalogScenarioInstallInput timeBased =
          mapper.readValue(
              "{\"scenario_type\":\"time-based\",\"team_ids\":[\"t1\"]}",
              CatalogScenarioInstallInput.class);
      CatalogScenarioInstallInput chained =
          mapper.readValue("{\"scenario_type\":\"chained\"}", CatalogScenarioInstallInput.class);

      // Assert
      assertThat(timeBased.toChoice())
          .isEqualTo(new TargetChoice(List.of(), List.of(), List.of("t1"), List.of()));
      assertThat(chained.toChoice()).isEqualTo(WorkflowChoice.NONE);
    }

    @Test
    @DisplayName("written as an install names its scenario type")
    void given_targets_when_writtenAsInstall_should_nameTimeBased() throws Exception {
      // Act
      String json =
          mapper
              .writerFor(CatalogScenarioInstallInput.class)
              .writeValueAsString(new ScenarioTargetsInput(null, null, null, null));

      // Assert
      assertThat(mapper.readTree(json).path("scenario_type").asText()).isEqualTo("time-based");
    }
  }

  @Test
  @DisplayName("A list left out means nothing chosen of that kind")
  void given_missingLists_should_chooseNothing() {
    // Act
    TargetChoice choice = new ScenarioTargetsInput(null, null, null, null).toChoice();

    // Assert
    assertThat(choice).isEqualTo(TargetChoice.NONE);
    assertThat(choice.isEmpty()).isTrue();
  }
}
