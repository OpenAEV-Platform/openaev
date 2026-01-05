package io.openaev.executors.sentinelone.client;

import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class SentinelOneExecutorClient {

  private static final String EXECUTE_SCRIPT_URI = "remote-scripts/execute";
  private static final String AGENTS_URI = "agents?isActive=true";
  private static final String SITE_FILTER = "&siteIds=";
  private static final String ACCOUNT_FILTER = "&accountIds=";
  private static final String GROUP_FILTER = "&groupIds=";
  private static final String CURSOR_PARAM = "&cursor=";

  private final SentinelOneExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  /**
   * Get SentinelOne agents for filters set in properties
   *
   * @return SentinelOne agents
   */
  public Set<SentinelOneAgent> agents() {
    Set<SentinelOneAgent> agents = new HashSet<>();
    if (this.config.getAccountId() != null && !this.config.getAccountId().isBlank()) {
      setAllAgentsFromFilter(ACCOUNT_FILTER + this.config.getAccountId(), agents);
    }
    if (this.config.getSiteId() != null && !this.config.getSiteId().isBlank()) {
      setAllAgentsFromFilter(SITE_FILTER + this.config.getSiteId(), agents);
    }
    if (this.config.getGroupId() != null && !this.config.getGroupId().isBlank()) {
      setAllAgentsFromFilter(GROUP_FILTER + this.config.getGroupId(), agents);
    }
    return agents;
  }

  private void setAllAgentsFromFilter(String filter, Set<SentinelOneAgent> agents) {
    ResponseAgent responseAgent = getSentinelOneAgents(filter);
    if (responseAgent.getErrors() != null && !responseAgent.getErrors().isEmpty()) {
      logErrors(responseAgent.getErrors(), "uri: " + AGENTS_URI + filter);
    } else {
      if (responseAgent.getData() != null) {
        agents.addAll(responseAgent.getData());
      }
      if (responseAgent.getPagination().getNextCursor() != null) {
        setAllAgentsFromFilter(
            filter + CURSOR_PARAM + responseAgent.getPagination().getNextCursor(), agents);
      }
    }
  }

  private ResponseAgent getSentinelOneAgents(String filter) {
    String jsonResponse;
    try {
      jsonResponse = this.get(AGENTS_URI + filter);
      return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
    } catch (Exception e) {
      log.error(
          String.format(
              "Error occurred during SentinelOne agents API request for filter %s. Error: %s",
              filter, e.getMessage()),
          e);
      return new ResponseAgent();
    }
  }

  private void logErrors(List<SentinelOneError> errors, String message) {
    StringBuilder msg =
        new StringBuilder(
            "Error occurred while targeting SentinelOne API request: " + message + ".");
    for (SentinelOneError error : errors) {
      msg.append("\nCode: ")
          .append(error.getCode())
          .append(", title: ")
          .append(error.getTitle())
          .append(", detail: ")
          .append(error.getDetail())
          .append(".");
    }
    log.error(msg.toString());
  }

  /**
   * Execute a payload through SentinelOne API executeScript
   *
   * @param agentsId to use for the payload
   * @param scriptId to use for the payload
   * @param command to use for the payload
   */
  public void executeScript(List<String> agentsId, String scriptId, String command) {
    try {
      SentinelOneFilter filter = new SentinelOneFilter();
      filter.setUuids(agentsId);
      SentinelOneData data = new SentinelOneData();
      data.setScriptId(scriptId);
      data.setInputParams(command);
      Map<String, Object> bodyCommand = new HashMap<>();
      bodyCommand.put("filter", filter);
      bodyCommand.put("data", data);
      String jsonResponse = this.post(EXECUTE_SCRIPT_URI, bodyCommand);
      ResponseScriptExecute response =
          this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
      if (response.getErrors() != null && !response.getErrors().isEmpty()) {
        logErrors(response.getErrors(), "uri: " + EXECUTE_SCRIPT_URI + " body: " + bodyCommand);
      }
    } catch (IOException e) {
      log.error(
          String.format(
              "Error occurred during SentinelOne executeScript API request. Error: %s",
              e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), SENTINELONE_EXECUTOR_NAME);
    }
  }

  private String get(@NotBlank final String uri) throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(this.config.getApiUrl() + uri);
      // Headers
      httpGet.addHeader("Authorization", "Bearer " + this.config.getApiKey());
      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException(
          "Unexpected response for HTTP GET SentinelOne on: " + uri, e);
    }
  }

  private String post(@NotBlank final String uri, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.config.getApiUrl() + uri);
      // Headers
      httpPost.addHeader("Authorization", "Bearer " + this.config.getApiKey());
      httpPost.addHeader("content-type", "application/json");
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);
      return httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for HTTP POST SentinelOne", e);
    }
  }
}
