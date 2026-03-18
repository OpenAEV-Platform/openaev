package io.openaev.opencti.client.mutations;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import java.util.List;
import lombok.*;

@RequiredArgsConstructor
public class RegisterConnector implements Mutation {
  @Getter private final ConnectorBase connector;
  @Getter private Boolean withJwks = false;

  private final String mutationMask =
      """
    mutation RegisterConnector($input: RegisterConnectorInput) {
      registerConnector(input: $input) {
        id
        connector_state
        config {
          connection {
            host
            vhost
            use_ssl
            port
            user
            pass
          }
          listen
          listen_routing
          listen_exchange
          push
          push_routing
          push_exchange
        }
        connector_user_id
        %s
      }
    }
    """;

  public String getQueryText() {
    return mutationMask.formatted(withJwks ? "jwks" : "");
  }

  public RegisterConnector(ConnectorBase connector, Boolean withJwks) {
    this.connector = connector;
    this.withJwks = withJwks;
  }

  @Override
  public JsonNode getVariables() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.set("input", mapper.valueToTree(toInput(connector)));
    return node;
  }

  @Data
  private static class Input {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private ConnectorType type;

    @JsonProperty("scope")
    private List<String> scope;

    @JsonProperty("auto")
    private boolean auto;

    @JsonProperty("auto_update")
    private boolean autoUpdate;

    @JsonProperty("only_contextual")
    private boolean onlyContextual;

    @JsonProperty("playbook_compatible")
    private boolean playbookCompatible;

    @JsonProperty("listen_callback_uri")
    private String listenCallbackURI;
  }

  @Data
  public static class ResponsePayload {
    @JsonProperty("registerConnector")
    private RegisterConnectorContent registerConnectorContent;

    @Data
    public static class RegisterConnectorContent {
      @JsonProperty("id")
      private String id;

      @JsonProperty("connector_state")
      private ObjectNode connectorState;

      @JsonProperty("jwks")
      private String jwks;

      @JsonProperty("config")
      private ConfigNode config;

      @JsonProperty("connector_user_id")
      private String connectorUserId;

      @Data
      public static class ConfigNode {
        @JsonProperty("connection")
        private ConnectionNode connection;

        @JsonProperty("listen")
        private String listen;

        @JsonProperty("listen_routing")
        private String listenRouting;

        @JsonProperty("listen_exchange")
        private String listenExchange;

        @JsonProperty("push")
        private String push;

        @JsonProperty("push_routing")
        private String pushRouting;

        @JsonProperty("push_exchange")
        private String pushExchange;

        @Data
        public static class ConnectionNode {
          @JsonProperty("host")
          private String host;

          @JsonProperty("vhost")
          private String vhost;

          @JsonProperty("use_ssl")
          private boolean useSsl;

          @JsonProperty("port")
          private int port;

          @JsonProperty("user")
          private String user;

          @JsonProperty("pass")
          private String pass;
        }
      }
    }
  }

  private Input toInput(ConnectorBase connector) {
    Input input = new Input();
    input.setId(connector.getId());
    input.setName(connector.getName());
    input.setType(connector.getType());
    input.setScope(connector.getScope());
    input.setAuto(connector.isAuto());
    input.setAutoUpdate(connector.isAuto());
    input.setOnlyContextual(connector.isOnlyContextual());
    input.setPlaybookCompatible(connector.isPlaybookCompatible());
    input.setListenCallbackURI(connector.getListenCallbackURI());
    return input;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }
    return this.connector.equals(((RegisterConnector) obj).getConnector());
  }
}
