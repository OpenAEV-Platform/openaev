package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.connectors.ConnectorBase;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PushStixBundle implements Mutation {
  @Getter private final ConnectorBase connector;
  @Getter private final JsonNode bundle;
  @Getter private final String workId = null;

  @Override
  public String getQueryText() {
    return """
      mutation StixBundlePush($connectorId: String!, $bundle: String!, $work_id: String) {
          stixBundlePush(connectorId: $connectorId, bundle: $bundle, work_id: $work_id)
      }
      """;
  }

  @Override
  public JsonNode getVariables() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.set("connectorId", mapper.valueToTree(connector.getId()));
    node.set("bundle", mapper.valueToTree(bundle.toString()));
    node.set("work_id", mapper.valueToTree(workId));
    return node;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("stixBundlePush")
    private Boolean stixBundlePush;
  }
}
