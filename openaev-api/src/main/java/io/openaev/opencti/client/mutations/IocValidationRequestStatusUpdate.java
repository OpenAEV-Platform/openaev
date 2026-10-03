package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * {@code iocValidationRequestStatusUpdate}: reports the lifecycle of an IOC validation request back
 * to OpenCTI. {@code status} is the snake_case {@code IocValidationRequestStatus} value; every other
 * field is optional and omitted when null.
 */
@Getter
@Builder
public class IocValidationRequestStatusUpdate implements Mutation {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final String requestId;
  private final String status;
  private final String scenarioId;
  private final String simulationId;
  private final String externalUri;
  private final String message;

  @Override
  public String getQueryText() {
    return """
      mutation IocValidationRequestStatusUpdate($id: ID!, $input: IocValidationRequestStatusInput!) {
          iocValidationRequestStatusUpdate(id: $id, input: $input) {
              id
          }
      }
      """;
  }

  @Override
  public JsonNode getVariables() {
    ObjectNode input = mapper.createObjectNode();
    input.put("status", status);
    putIfPresent(input, "openaev_scenario_id", scenarioId);
    putIfPresent(input, "openaev_simulation_id", simulationId);
    putIfPresent(input, "external_uri", externalUri);
    putIfPresent(input, "message", message);
    ObjectNode node = mapper.createObjectNode();
    node.put("id", requestId);
    node.set("input", input);
    return node;
  }

  private static void putIfPresent(ObjectNode node, String key, String value) {
    if (value != null) {
      node.put(key, value);
    }
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("iocValidationRequestStatusUpdate")
    private UpdatedRequest updatedRequest;

    @Data
    public static class UpdatedRequest {
      @JsonProperty("id")
      private String id;
    }
  }
}
