package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WorkToReceived implements Mutation {
  @Getter private final String workId;
  @Getter private final String message;

  @Override
  public String getQueryText() {
    return """
        mutation workToReceived($id: ID!, $message: String) {
            workEdit(id: $id) {
                toReceived (message: $message)
            }
        }
      """;
  }

  @Override
  public JsonNode getVariables() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.set("id", mapper.valueToTree(workId));
    node.set("message", mapper.valueToTree(message));
    return node;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("workEdit")
    private WorkToReceivedContent workEdit;

    @Data
    public static class WorkToReceivedContent {
      @JsonProperty("toReceived")
      private String toReceived;
    }
  }
}
