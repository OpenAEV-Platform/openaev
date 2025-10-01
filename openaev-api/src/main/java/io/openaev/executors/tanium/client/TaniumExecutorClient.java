package io.openaev.executors.tanium.client;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.executors.tanium.model.DataEndpoints;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class TaniumExecutorClient {

  private static final String KEY_HEADER = "session";

  private final TaniumExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  // -- ENDPOINTS --

  public DataEndpoints endpoints() {
    try {
      final String formattedDateTime =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now().minusMillis(EndpointService.DELETE_TTL));
      // https://help.tanium.com/bundle/ug_gateway_cloud/page/gateway/filter_syntax.html
      String query =
          String.format(
              """
                  query {
                    endpoints(filter: {
                      any: false,
                      filters: [
                        {memberOf: {id: %d}},
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
                    }
                  }
                  """,
              config.getComputerGroupId(), formattedDateTime);

      Map<String, Object> body = new HashMap<>();
      body.put("query", query);
      String jsonResponse = this.post(body);

      GraphQLResponse<DataEndpoints> response =
          objectMapper.readValue(jsonResponse, new TypeReference<>() {});

      if (response == null || response.data == null) {
        throw new RuntimeException("API response malformed or empty");
      }

      return response.data;
    } catch (JsonProcessingException e) {
      log.error(String.format("Failed to parse JSON response. Error: %s", e.getMessage()), e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error("Error while querying endpoints", e);
      throw new RuntimeException(e);
    }
  }

  public void executeAction(String endpointId, Integer packageID, String command) {
    try {
      String escapedCommand = command.replace("\\", "\\\\").replace("\"", "\\\"");

      String mutation =
          String.format(
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
                  """,
              packageID, escapedCommand, config.getActionGroupId(), endpointId);

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("query", mutation);

      this.post(requestBody);
    } catch (IOException e) {
      log.error("Error while executing action", e);
      throw new RuntimeException(e);
    }
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
                StringBuilder errorMessage = new StringBuilder("GraphQL errors detected:\n");
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
                throw new RuntimeException(errorMessage.toString());
              }

              return result;
            } else {
              throw new ClientProtocolException(
                  "Unexpected response status: " + status + "\nBody: " + result);
            }
          });

    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response", e);
    }
  }

  private static class GraphQLResponse<T> {

    public T data;
  }
}
