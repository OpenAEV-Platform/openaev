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
public class WorkToProcessed implements Mutation {
  @Getter private final String workId;
  @Getter private final String message;
  @Getter private final Boolean inError;

  @Override
  public String getQueryText() {
    return """
          mutation workToProcessed($id: ID!, $message: String, $inError: Boolean) {
              workEdit(id: $id) {
                  toProcessed (message: $message, inError: $inError)
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
    node.set("inError", mapper.valueToTree(inError));
    return node;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("workEdit")
    private WorkToProcessedContent workEdit;

    @Data
    public static class WorkToProcessedContent {
      @JsonProperty("toProcessed")
      private String toProcessed;
    }
  }
}
