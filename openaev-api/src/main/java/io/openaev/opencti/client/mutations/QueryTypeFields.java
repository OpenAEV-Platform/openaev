package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.connectors.ConnectorBase;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class QueryTypeFields implements Mutation {
  @Getter private final ConnectorBase connector;
  @Getter private final String typeName;

  @Override
  public String getQueryText() {
    return """
            query QueryTypeFields($typeName: String!) {
              __type(name: $typeName) {
                fields {
                  name
                }
              }
            }
            """;
  }

  @Override
  public JsonNode getVariables() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.set("typeName", mapper.valueToTree(typeName));
    return node;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("__type")
    private TypeContent typeContent;

    @Data
    public static class TypeContent {
      @JsonProperty("fields")
      private List<FieldContent> fields;

      @Data
      public static class FieldContent {
        @JsonProperty("name")
        private String name;
      }
    }

    public Boolean hasJwks() {
      return this.typeContent.fields.stream().anyMatch(fc -> "jwks".equals(fc.name));
    }
  }
}
