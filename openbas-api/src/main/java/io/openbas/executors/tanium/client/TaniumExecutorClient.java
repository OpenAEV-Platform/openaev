package io.openbas.executors.tanium.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.authorisation.HttpClientFactory;
import io.openbas.executors.tanium.config.TaniumExecutorConfig;
import io.openbas.executors.tanium.model.DataEndpoints;
import io.openbas.service.EndpointService;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
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
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
    String jsonResponse = null;
    try {
      final String formattedDateTime =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now().minusMillis(EndpointService.DELETE_TTL));
      // https://help.tanium.com/bundle/ug_gateway_cloud/page/gateway/filter_syntax.html
      String query =
          "{\n"
              + "\tendpoints(filter: {any: false, filters: [{memberOf: {id: "
              + this.config.getComputerGroupId()
              + "}}, {path: \"eidLastSeen\", op: GT, value: \""
              + formattedDateTime
              + "\"}]}) {\n"
              + "    edges {\n"
              + "      node {\n"
              + "        id\n"
              + "        computerID\n"
              + "        name\n"
              + "        ipAddresses\n"
              + "        macAddresses\n"
              + "        eidLastSeen\n"
              + "        os { platform }\n"
              + "        processor { architecture }\n"
              + "      }\n"
              + "    }\n"
              + "  }\n"
              + "}";
      Map<String, Object> body = new HashMap<>();
      body.put("query", query);
      jsonResponse = this.post(body);
      if (jsonResponse == null || jsonResponse.isEmpty()) {
        log.error("Received empty response from API for query: {}", query);
        throw new RuntimeException("API returned an empty response");
      }
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.error(
          String.format(
              "Failed to parse JSON response %s. Error: %s", jsonResponse, e.getMessage()),
          e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error(
          String.format("I/O error occurred during API request. Error: %s", e.getMessage()), e);
      throw new RuntimeException(e);
    } catch (Exception e) {
      log.error(String.format("Unexpected error occurred. Error: %s", e.getMessage()), e);
      throw new RuntimeException(e);
    }
  }

  public void executeAction(String endpointId, Integer packageID, String command) {
    try {
      String query =
          "mutation {\n"
              + "\tactionCreate(\n"
              + "  input: { name: \"OpenBAS Action\",  package: { id: "
              + packageID
              + ", params: [\""
              + command.replace("\\", "\\\\").replace("\"", "\\\"")
              + "\"] }, targets: { actionGroup: { id: "
              + this.config.getActionGroupId()
              + " }, endpoints: ["
              + endpointId
              + "] } }\n"
              + ") {\n "
              + "    action {\n"
              + "      id\n"
              + "    }\n"
              + "  }\n"
              + "}";
      Map<String, Object> body = new HashMap<>();
      body.put("query", query);
      this.post(body);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PRIVATE --

  private String post(@NotNull final Map<String, Object> body) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.config.getGatewayUrl());
      // Headers
      httpPost.addHeader(KEY_HEADER, this.config.getApiKey());
      httpPost.addHeader("content-type", "application/json");
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);
      return httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response", e);
    }
  }
}
