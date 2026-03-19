package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.connectors.ConnectorBase;
import java.time.Instant;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Ping implements Mutation {
  @Getter private final ConnectorBase connector;
  @Getter private Boolean withJwks = false;

  private final String mutationMask =
      """
    mutation PingConnector($id: ID!, $state: String, $connectorInfo: ConnectorInfoInput ) {
      pingConnector(id: $id, state: $state, connectorInfo: $connectorInfo) {
        id
        connector_state
        connector_info {
          run_and_terminate
          buffering
          queue_threshold
          queue_messages_size
          next_run_datetime
          last_run_datetime
        }
        %s
      }
    }
    """;

  public String getQueryText() {
    return mutationMask.formatted(withJwks ? "jwks" : "");
  }

  public Ping(ConnectorBase connector, Boolean withJwks) {
    this.connector = connector;
    this.withJwks = withJwks;
  }

  @Override
  public JsonNode getVariables() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.set("id", mapper.valueToTree(connector.getId()));
    node.set("state", null);
    node.set("connectorInfo", mapper.valueToTree(new ConnectorInfo()));
    return node;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("pingConnector")
    private PingConnectorContent pingConnectorContent;

    @Data
    public static class PingConnectorContent {
      @JsonProperty("id")
      private String id;

      @JsonProperty("connector_state")
      private ObjectNode connectorState;

      @JsonProperty("jwks")
      private String jwks;

      @JsonProperty("connector_info")
      private ConnectorInfo connectorInfo;

      @Data
      public static class ConnectorInfo {
        @JsonProperty("run_and_terminate")
        private Boolean runAndTerminate;

        @JsonProperty("buffering")
        private Boolean buffering;

        @JsonProperty("queue_threshold")
        private Double queueThreshold;

        @JsonProperty("queue_messages_size")
        private Double queueMessagesSize;

        @JsonProperty("next_run_datetime")
        private Instant nextRunDatetime;

        @JsonProperty("last_run_datetime")
        private Instant lastRunDatetime;
      }
    }
  }

  @Getter
  private static class ConnectorInfo {
    @JsonProperty("run_and_terminate")
    private Boolean runAndTerminate = false;

    @JsonProperty("buffering")
    private Boolean buffering = false;

    @JsonProperty("queue_threshold")
    private Double queueThreshold = 0.0;

    @JsonProperty("queue_messages_size")
    private Double queueMessagesSize = 0.0;

    @JsonProperty("next_run_datetime")
    private Instant nextRunDatetime = null;

    @JsonProperty("last_run_datetime")
    private Instant lastRunDatetime = null;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }
    return this.connector.equals(((Ping) obj).getConnector());
  }
}
