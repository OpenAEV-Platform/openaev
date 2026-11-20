package io.openaev.service.catalog.scenario.update;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario content reader")
class ScenarioContentReaderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ScenarioContentReader reader = new ScenarioContentReader(MAPPER);

  @Nested
  @DisplayName("From a package")
  class FromPackage {

    @Test
    @DisplayName("refuses an inject running a payload its release does not name")
    void given_payloadMissingFromTheRelease_should_refuse() throws Exception {
      // Arrange
      ScenarioPackage scenarioPackage =
          new ScenarioPackage(
              MAPPER.readTree(
                  """
                  {
                    "scenario_information": {"scenario_name": "Recon"},
                    "scenario_injects": [{
                      "inject_id": "scan",
                      "inject_title": "Scan",
                      "inject_injector_contract": {
                        "injector_contract_payload": {"payload_external_id": "nmap"}
                      }
                    }]
                  }
                  """));

      // Act & Assert
      assertThatThrownBy(() -> reader.fromPackage(scenarioPackage, Map.of()))
          .isInstanceOf(BadRequestException.class)
          .hasMessageEndingWith("Scan");
    }
  }

  @Nested
  @DisplayName("From the entities")
  class FromEntities {

    @Test
    @DisplayName("reads an inject the client added under its own ID, with only the fields it has")
    void given_injectTheClientAdded_should_readItUnderItsOwnId() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setName("Recon");
      Inject inject = new Inject();
      inject.setId("my-check");
      inject.setTitle("My check");

      // Act
      ObjectNode content = reader.fromEntities(scenario, List.of(), List.of(inject));

      // Assert
      assertThatJson(content)
          .isEqualTo(
              """
              {
                "scenario": {"name": "Recon"},
                "variables": {},
                "inject:my-check": {"title": "My check", "depends_duration": 0, "depends_on": []}
              }
              """);
    }
  }
}
