package io.openbas.executors.crowdstrike.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openbas.executors.crowdstrike.model.*;
import io.openbas.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Log
public class CrowdStrikeExecutorClient {

  private static final Integer AUTH_TIMEOUT = 300;
  private static final String OAUTH_URI = "/oauth2/token";
  private static final String HOST_GROUPS_URI = "/devices/entities/host-groups/v1";
  private static final String ENDPOINTS_URI = "/devices/combined/host-group-members/v1";
  private static final String SESSION_URI = "/real-time-response/entities/sessions/v1";
  private static final String REAL_TIME_RESPONSE_URI =
      "/real-time-response/entities/active-responder-command/v1";

  private final CrowdStrikeExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Instant lastAuthentication = Instant.now().minusSeconds(AUTH_TIMEOUT);
  private String token;

  // -- ENDPOINTS --

  public List<CrowdStrikeDevice> devices(String hostGroup) {
    try {
      int offset = 0;
      List<CrowdStrikeDevice> hosts = new ArrayList<>();
      ResourcesHosts partialResults = getResourcesHosts(offset, hostGroup);
      if (partialResults.getErrors() != null && !partialResults.getErrors().isEmpty()) {
        logErrors(partialResults.getErrors(), hostGroup);
        return hosts;
      } else if (partialResults.getResources() == null) {
        return hosts;
      } else {
        hosts.addAll(partialResults.getResources());
      }
      int numberOfExecution =
          Math.ceilDiv(
              partialResults.getMeta().getPagination().getTotal(),
              partialResults.getMeta().getPagination().getLimit());
      for (int callNumber = 1; callNumber < numberOfExecution; callNumber += 1) {
        offset += partialResults.getMeta().getPagination().getLimit();
        partialResults = getResourcesHosts(offset, hostGroup);
        if (partialResults.getResources() == null) {
          return hosts;
        } else {
          hosts.addAll(partialResults.getResources());
        }
      }
      return hosts;
    } catch (Exception e) {
      log.log(Level.SEVERE, "Unexpected error occurred. Error: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private void logErrors(List<CrowdstrikeError> errors, String hostGroup) {
    StringBuilder msg =
        new StringBuilder(
            "Error occurred while getting Crowdstrike devices API request for hostGroup id "
                + hostGroup
                + ".");
    for (CrowdstrikeError error : errors) {
      msg.append("\nCode: ")
          .append(error.getCode())
          .append(", message: ")
          .append(error.getMessage())
          .append(".");
    }
    log.log(Level.SEVERE, msg.toString());
  }

  private ResourcesHosts getResourcesHosts(int offset, String hostGroup) {
    final String formattedDateTime =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now().minusMillis(EndpointService.DELETE_TTL));
    String fqlFilter =
        URLEncoder.encode(
            "last_seen:>'" + formattedDateTime + "'+hostname:!null", StandardCharsets.UTF_8);
    String jsonResponse;
    try {
      jsonResponse =
          this.get(
              ENDPOINTS_URI
                  + "?id="
                  + hostGroup
                  + "&limit=5000"
                  + "&offset="
                  + offset
                  + "&filter="
                  + fqlFilter);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (Exception e) {
      log.log(
          Level.SEVERE,
          "Error occurred during Crowdstrike getResourcesHosts API request. Error: {}",
          e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public ResourcesGroups hostGroup(String hostGroup) {
    String jsonResponse;
    try {
      jsonResponse = this.get(HOST_GROUPS_URI + "?ids=" + hostGroup);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (Exception e) {
      log.log(
          Level.SEVERE,
          "Error occurred during Crowdstrike hostGroup API request. Error: {}",
          e.getMessage());
      throw new RuntimeException(e);
    }
  }

  public void executeAction(String deviceId, String scriptName, String command) {
    try {
      // Open remote session
      Map<String, Object> bodySession = new HashMap<>();
      bodySession.put("device_id", deviceId);
      bodySession.put("queue_offline", false);
      String jsonSessionResponse = this.post(SESSION_URI, bodySession);
      ResourcesSession sessions =
          this.objectMapper.readValue(jsonSessionResponse, new TypeReference<>() {});
      CrowdStrikeSession session = sessions.getResources().getFirst();
      if (session == null) {
        log.log(Level.SEVERE, "Cannot get the session on the selected device");
        throw new RuntimeException("Cannot get the session on the selected device");
      }
      // Execute the command
      Map<String, Object> bodyCommand = new HashMap<>();
      bodyCommand.put("session_id", session.getSession_id());
      bodyCommand.put("base_command", "runscript");
      bodyCommand.put(
          "command_string",
          "runscript -CloudFile=\""
              + scriptName
              + "\"  -CommandLine=```'{\"command\":\""
              + command
              + "\"}'```");
      this.post(REAL_TIME_RESPONSE_URI, bodyCommand);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // -- PRIVATE --

  private String get(@NotBlank final String uri) throws IOException {
    if (this.lastAuthentication.isBefore(Instant.now().minusSeconds(AUTH_TIMEOUT))) {
      this.authenticate();
    }
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpGet httpGet = new HttpGet(this.config.getApiUrl() + uri);
      // Headers
      httpGet.addHeader("Authorization", "Bearer " + this.token);
      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + uri, e);
    }
  }

  private String post(@NotBlank final String uri, @NotNull final Map<String, Object> body)
      throws IOException {
    if (this.lastAuthentication.isBefore(Instant.now().minusSeconds(AUTH_TIMEOUT))) {
      this.authenticate();
    }
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(this.config.getApiUrl() + uri);
      // Headers
      httpPost.addHeader("Authorization", "Bearer " + this.token);
      httpPost.addHeader("content-type", "application/json");
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);
      return httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response", e);
    }
  }

  private void authenticate() throws IOException {
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(this.config.getApiUrl() + OAUTH_URI);
      // Headers
      httpPost.addHeader("content-type", "application/x-www-form-urlencoded");
      // Body
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("client_id", this.config.getClientId()));
      params.add(new BasicNameValuePair("client_secret", this.config.getClientSecret()));
      params.add(new BasicNameValuePair("grant_type", "client_credentials"));
      httpPost.setEntity(new UrlEncodedFormEntity(params));
      String jsonResponse =
          httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
      Authentication auth = this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
      this.token = auth.getAccess_token();
      this.lastAuthentication = Instant.now();
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response", e);
    }
  }
}
