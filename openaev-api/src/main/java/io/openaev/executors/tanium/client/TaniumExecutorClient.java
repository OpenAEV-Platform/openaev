package io.openaev.executors.tanium.client;

import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.DataComputerGroup;
import io.openaev.executors.tanium.model.DataEndpoints;
import io.openaev.executors.tanium.model.EdgesEndpoints;
import io.openaev.executors.tanium.model.NodeEndpoint;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class TaniumExecutorClient {

  private static final String KEY_HEADER = "session";
  private static final String QUERY = "query";
  private static final String QUERY_ENDPOINTS =
      """
                              query {
                                endpoints(after: %s, first: 5000, filter: {
                                  any: false,
                                  filters: [
                                    {memberOf: {id: %s}},
                                    {path: "eidLastSeen", op: GT, value: "%s"}
                                  ]
                                }) {
                                  edges {
                                    node {
                                      id computerID name ipAddresses macAddresses eidLastSeen
                                      os { platform }
                                      processor { architecture }
                                    }
                                  }
                                  pageInfo {
                                    endCursor
                                    hasNextPage
                                  }
                                }
                              }
                              """;
  private static final String QUERY_COMPUTER_GROUP =
      """
                          query {
                            computerGroup(ref: {
                              id: %s
                            }) {
                            id
                            name
                            }
                          }
                          """;
  private static final String MUTATION_ACTION_CREATE =
      """
                  mutation {
                    actionCreate(
                      input: {
                        name: "OpenAEV Action",
                        package: {
                          id: %d,
                          params: ["%s"]
                        },
                        targets: {
                          actionGroup: { id: %d },
                          endpoints: ["%s"]
                        }
                      }
                    ) {
                      action { id }
                    }
                  }
                  """;

  private final TaniumExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  // -- ENDPOINTS --

  public List<NodeEndpoint> endpoints(String computerGroupId) {
    List<NodeEndpoint> endpoints = new ArrayList<>();
    return getAllEndpointsFromComputerGroup(null, computerGroupId, endpoints);
  }

  public List<NodeEndpoint> getAllEndpointsFromComputerGroup(
      String after, String computerGroupId, List<NodeEndpoint> endpoints) {
    EdgesEndpoints edgesEndpoints = getTaniumEndpoints(after, computerGroupId);
    endpoints.addAll(edgesEndpoints.getEdges().stream().toList());
    if (edgesEndpoints.getPageInfo().isHasNextPage()) {
      getAllEndpointsFromComputerGroup(
          edgesEndpoints.getPageInfo().getEndCursor(), computerGroupId, endpoints);
    }
    return endpoints;
  }

  private EdgesEndpoints getTaniumEndpoints(String after, String computerGroupId) {
    try {
      final String formattedDateTime =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now().minusMillis(EndpointService.DELETE_TTL));
      // https://help.tanium.com/bundle/ug_gateway_cloud/page/gateway/filter_syntax.html
      String query = String.format(QUERY_ENDPOINTS, after, computerGroupId, formattedDateTime);

      Map<String, Object> body = new HashMap<>();
      body.put(QUERY, query);
      String jsonResponse = this.postSync(body);

      GraphQLResponse<DataEndpoints> response =
          objectMapper.readValue(jsonResponse, new TypeReference<>() {});

      if (response == null || response.data == null) {
        throw new ExecutorException(
            "API response for Tanium endpoints is malformed or empty, computerGroupId: "
                + computerGroupId,
            TANIUM_EXECUTOR_NAME);
      }

      return response.data.getEndpoints();
    } catch (JsonProcessingException e) {
      log.error(
          String.format(
              "Failed to parse JSON response for Tanium API endpoints, computerGroupId: %s. Error: %s",
              computerGroupId, e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), TANIUM_EXECUTOR_NAME);
    } catch (IOException e) {
      log.error(
          String.format(
              "Error while querying Tanium API endpoints, computerGroupId %S. Error: %s",
              computerGroupId, e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), TANIUM_EXECUTOR_NAME);
    }
  }

  public DataComputerGroup computerGroup(String computerGroup) {
    try {
      String query = String.format(QUERY_COMPUTER_GROUP, computerGroup);

      Map<String, Object> body = new HashMap<>();
      body.put(QUERY, query);
      String jsonResponse = this.postSync(body);

      GraphQLResponse<DataComputerGroup> response =
          objectMapper.readValue(jsonResponse, new TypeReference<>() {});

      if (response == null || response.data == null) {
        throw new ExecutorException(
            "API response for Tanium computerGroup is malformed or empty", TANIUM_EXECUTOR_NAME);
      }

      return response.data;
    } catch (JsonProcessingException e) {
      log.error(
          String.format(
              "Failed to parse JSON response for Tanium API computerGroup. Error: %s",
              e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), TANIUM_EXECUTOR_NAME);
    } catch (IOException e) {
      log.error(
          String.format("Error while querying Tanium API computerGroup. Error: %s", e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), TANIUM_EXECUTOR_NAME);
    }
  }

  public void executeAction(String endpointId, Integer packageID, String command) {
    try {
      String escapedCommand = command.replace("\\", "\\\\").replace("\"", "\\\"");

      String mutation =
          String.format(
              MUTATION_ACTION_CREATE,
              packageID,
              escapedCommand,
              config.getActionGroupId(),
              endpointId);

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put(QUERY, mutation);

      this.postAsync(requestBody);
    } catch (IOException e) {
      log.error(
          String.format("Error while executing action with Tanium API. Error: %s", e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), TANIUM_EXECUTOR_NAME);
    }
  }

  private String postSync(@NotNull final Map<String, Object> body) throws IOException {
    return post(body);
  }

  @Async
  protected void postAsync(@NotNull final Map<String, Object> body) throws IOException {
    post(body);
  }

  // -- PRIVATE --
  private String post(@NotNull final Map<String, Object> body) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.config.getGatewayUrl());
      // Headers
      httpPost.addHeader(KEY_HEADER, this.config.getApiKey());
      httpPost.addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE);
      // Body
      String json = this.objectMapper.writeValueAsString(body);
      httpPost.setEntity(new StringEntity(json, StandardCharsets.UTF_8));

      return httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            int status = response.getCode();
            String result = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (HttpStatus.valueOf(response.getCode()).is2xxSuccessful()) {
              Map<String, Object> responseMap =
                  objectMapper.readValue(result, new TypeReference<>() {});
              if (responseMap.containsKey("errors")) {
                StringBuilder errorMessage =
                    new StringBuilder("GraphQL errors detected while targeting Tanium API:\n");
                for (Map<String, Object> error :
                    (Iterable<Map<String, Object>>) responseMap.get("errors")) {
                  errorMessage.append("- ").append(error.get("message")).append("\n");

                  Map<String, Object> extensions = (Map<String, Object>) error.get("extensions");
                  if (extensions != null && extensions.containsKey("argumentErrors")) {
                    for (Map<String, Object> argError :
                        (Iterable<Map<String, Object>>) extensions.get("argumentErrors")) {
                      errorMessage
                          .append("  • ")
                          .append(argError.get("message"))
                          .append(" (code: ")
                          .append(argError.get("code"))
                          .append(")\n");
                    }
                  }
                }
                throw new ExecutorException(errorMessage.toString(), TANIUM_EXECUTOR_NAME);
              }

              return result;
            } else if (status == 401) {
              throw new TokenExpiredException(
                  "Tanium token expired or invalid : " + +status + "\nBody: " + result);
            } else {
              throw new ClientProtocolException(
                  "Unexpected response for Tanium API, status: " + status + "\nBody: " + result);
            }
          });

    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for Tanium API: ", e);
    }
  }

  private static class GraphQLResponse<T> {

    public T data;
  }
}
