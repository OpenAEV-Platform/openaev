package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import lombok.Getter;

/**
 * OpenCTI {@code huntValidateFromEmulation}: asks OpenCTI to run the active hunts covering an
 * emulated technique on the connector bound to a security platform, over the emulation window.
 * OpenCTI is idempotent per inject, hunt and platform, and requires Enterprise Edition.
 */
@Getter
public class ValidateHuntFromEmulation implements Mutation {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final Input input;

  public ValidateHuntFromEmulation(Input input) {
    this.input = Objects.requireNonNull(input, "input");
  }

  @Override
  public String getQueryText() {
    return """
      mutation HuntValidateFromEmulation($input: HuntValidateFromEmulationInput!) {
        huntValidateFromEmulation(input: $input) {
          hunts_count
          runs {
            id
            hunt_id
            hunt_run_status
          }
        }
      }
      """;
  }

  @Override
  public JsonNode getVariables() {
    ObjectNode node = mapper.createObjectNode();
    node.set("input", mapper.valueToTree(input));
    return node;
  }

  /**
   * {@code HuntValidateFromEmulationInput}. Date-times are ISO-8601 strings, which is what the
   * OpenCTI {@code DateTime} scalar parses. Null fields are omitted rather than sent as null.
   *
   * @param techniqueId ATT&CK external id of the emulated technique
   * @param securityPlatformId STIX id OpenAEV gives the security platform in its coverage bundles
   * @param securityPlatformName exact name, resolved by OpenCTI when the id is unknown to it
   * @param injectId OpenAEV inject id, the OpenCTI idempotency key with the hunt and the platform
   * @param windowStart start of the emulation window
   * @param windowEnd end of the emulation window
   * @param securityCoverageId STIX id of the OpenCTI Security Coverage of the simulation
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Input(
      @JsonProperty("technique_id") String techniqueId,
      @JsonProperty("security_platform_id") String securityPlatformId,
      @JsonProperty("security_platform_name") String securityPlatformName,
      @JsonProperty("inject_id") String injectId,
      @JsonProperty("window_start") String windowStart,
      @JsonProperty("window_end") String windowEnd,
      @JsonProperty("security_coverage_id") String securityCoverageId) {}

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ResponsePayload {
    @JsonProperty("huntValidateFromEmulation")
    private HuntValidation huntValidation;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HuntValidation {
    @JsonProperty("hunts_count")
    private int huntsCount;

    @JsonProperty("runs")
    private List<HuntRun> runs = new ArrayList<>();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HuntRun {
    @JsonProperty("id")
    private String id;

    @JsonProperty("hunt_id")
    private String huntId;

    @JsonProperty("hunt_run_status")
    private String huntRunStatus;
  }
}
