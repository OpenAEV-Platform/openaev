package io.openaev.opencti.client.mutation;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.client.mutations.ValidateHuntFromEmulation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidateHuntFromEmulation mutation")
class ValidateHuntFromEmulationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static ValidateHuntFromEmulation.Input fullInput() {
    return new ValidateHuntFromEmulation.Input(
        "T1059.001",
        "identity--7f4c1a2e-0c55-4a8e-9c67-1f3a2b3c4d5e",
        "Splunk prod",
        "inject-1",
        "2026-10-03T10:00:00Z",
        "2026-10-03T10:20:00Z",
        "security-coverage--0d7a0a5e-2b8f-4b6f-9b6c-1b2c3d4e5f60");
  }

  @Nested
  @DisplayName("Operation")
  class Operation {

    @Test
    @DisplayName("given the mutation should target huntValidateFromEmulation with a typed input")
    void given_mutation_should_targetHuntValidateFromEmulation() {
      // Arrange
      ValidateHuntFromEmulation mutation = new ValidateHuntFromEmulation(fullInput());

      // Act
      String query = mutation.getQueryText().replaceAll("\\s+", " ");

      // Assert
      assertThat(query)
          .contains("mutation HuntValidateFromEmulation($input: HuntValidateFromEmulationInput!)")
          .contains("huntValidateFromEmulation(input: $input)")
          .contains("hunts_count")
          .contains("runs { id hunt_id hunt_run_status }");
    }

    @Test
    @DisplayName("given no input should fail fast")
    void given_noInput_should_failFast() {
      // Act + Assert
      assertThatThrownBy(() -> new ValidateHuntFromEmulation(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Variables")
  class Variables {

    @Test
    @DisplayName("given a full input should send every field under its contract name")
    void given_fullInput_should_sendEveryFieldUnderItsContractName() {
      // Arrange
      ValidateHuntFromEmulation mutation = new ValidateHuntFromEmulation(fullInput());

      // Act + Assert
      assertThatJson(mutation.getVariables())
          .isEqualTo(
              """
              {
                "input": {
                  "technique_id": "T1059.001",
                  "security_platform_id": "identity--7f4c1a2e-0c55-4a8e-9c67-1f3a2b3c4d5e",
                  "security_platform_name": "Splunk prod",
                  "inject_id": "inject-1",
                  "window_start": "2026-10-03T10:00:00Z",
                  "window_end": "2026-10-03T10:20:00Z",
                  "security_coverage_id": "security-coverage--0d7a0a5e-2b8f-4b6f-9b6c-1b2c3d4e5f60"
                }
              }
              """);
    }

    @Test
    @DisplayName("given optional fields left null should omit them instead of sending null")
    void given_nullOptionalFields_should_omitThem() {
      // Arrange
      ValidateHuntFromEmulation mutation =
          new ValidateHuntFromEmulation(
              new ValidateHuntFromEmulation.Input(
                  "T1003",
                  null,
                  "Elastic",
                  "inject-2",
                  "2026-10-03T10:00:00Z",
                  "2026-10-03T10:05:00Z",
                  null));

      // Act + Assert
      assertThatJson(mutation.getVariables())
          .isEqualTo(
              """
              {
                "input": {
                  "technique_id": "T1003",
                  "security_platform_name": "Elastic",
                  "inject_id": "inject-2",
                  "window_start": "2026-10-03T10:00:00Z",
                  "window_end": "2026-10-03T10:05:00Z"
                }
              }
              """);
    }
  }

  @Nested
  @DisplayName("Response")
  class ResponsePayload {

    @Test
    @DisplayName("given an OpenCTI answer should map the hunt validation and ignore unknown fields")
    void given_openCtiAnswer_should_mapHuntValidation() throws Exception {
      // Arrange
      String data =
          """
          {
            "huntValidateFromEmulation": {
              "hunts_count": 2,
              "runs": [
                { "id": "run-1", "hunt_id": "hunt-1", "hunt_run_status": "queued", "attempt": 1 },
                { "id": "run-2", "hunt_id": "hunt-2", "hunt_run_status": "running" }
              ],
              "added_later": true
            }
          }
          """;

      // Act
      ValidateHuntFromEmulation.ResponsePayload payload =
          mapper.readValue(data, ValidateHuntFromEmulation.ResponsePayload.class);

      // Assert
      assertThat(payload.getHuntValidation().getHuntsCount()).isEqualTo(2);
      assertThat(payload.getHuntValidation().getRuns())
          .extracting(
              ValidateHuntFromEmulation.HuntRun::getId,
              ValidateHuntFromEmulation.HuntRun::getHuntId,
              ValidateHuntFromEmulation.HuntRun::getHuntRunStatus)
          .containsExactly(tuple("run-1", "hunt-1", "queued"), tuple("run-2", "hunt-2", "running"));
    }

    @Test
    @DisplayName("given a null validation should map to a null hunt validation")
    void given_nullValidation_should_mapToNull() throws Exception {
      // Act
      ValidateHuntFromEmulation.ResponsePayload payload =
          mapper.readValue(
              "{\"huntValidateFromEmulation\": null}",
              ValidateHuntFromEmulation.ResponsePayload.class);

      // Assert
      assertThat(payload.getHuntValidation()).isNull();
    }
  }
}
