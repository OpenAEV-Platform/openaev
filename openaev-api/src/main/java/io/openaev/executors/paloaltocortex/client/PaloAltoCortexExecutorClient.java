package io.openaev.executors.paloaltocortex.client;

import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.executors.paloaltocortex.model.*;
import io.openaev.service.EndpointService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
public class PaloAltoCortexExecutorClient {

  private static final String RUN_SCRIPT_URI = "scripts/run_script";
  private static final String ENDPOINTS_URI = "endpoints/get_endpoint";
  // Max by default
  private static final int MAX_RESULT_SIZE = 100;

  private final PaloAltoCortexExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  /**
   * Get Palo Alto Cortex endpoints for groups names set in properties
   *
   * @return Palo Alto Cortex endpoints
   */
  public List<PaloAltoCortexEndpoint> endpoints(String groupName) {
    try {
      int offset = 0;
      List<PaloAltoCortexEndpoint> endpoints = new ArrayList<>();
      PaloAltoCortexEndpointReply partialResults = getEndpoints(offset, groupName).getReply();
      if (partialResults.getErr_code() != null) {
        log.error(
            "Error occurred while getting Palo Alto Cortex endpoints API request for group name "
                + groupName
                + "\nError: "
                + partialResults.getErr_code()
                + " - "
                + partialResults.getErr_msg()
                + " - "
                + partialResults.getErr_extra());
        return endpoints;
      } else if (partialResults.getEndpoints() == null || partialResults.getEndpoints().isEmpty()) {
        return endpoints;
      } else {
        endpoints.addAll(partialResults.getEndpoints());
      }
      int numberOfExecution = Math.ceilDiv(partialResults.getTotal_count(), MAX_RESULT_SIZE);
      for (int callNumber = 1; callNumber < numberOfExecution; callNumber += 1) {
        offset += MAX_RESULT_SIZE;
        partialResults = getEndpoints(offset, groupName).getReply();
        if (partialResults.getEndpoints() == null || partialResults.getEndpoints().isEmpty()) {
          return endpoints;
        } else {
          endpoints.addAll(partialResults.getEndpoints());
        }
      }
      return endpoints;
    } catch (Exception e) {
      log.error(String.format("Unexpected error occurred. Error: %s", e.getMessage()), e);
      throw new ExecutorException(e, e.getMessage(), PALOALTOCORTEX_EXECUTOR_NAME);
    }
  }

  private ResponseEndpoint getEndpoints(int offset, String groupName) {
    try {
      BodyEndpoint bodyEndpoint = new BodyEndpoint();
      bodyEndpoint.setSearch_from(offset);
      bodyEndpoint.setSearch_to(offset + MAX_RESULT_SIZE);
      PaloAltoCortexFilter filterGroupName = new PaloAltoCortexFilter();
      filterGroupName.setField("group_name");
      filterGroupName.setOperator("in");
      filterGroupName.setValue(List.of(groupName));
      PaloAltoCortexFilter filterLastSeen = new PaloAltoCortexFilter();
      filterLastSeen.setField("last_seen");
      filterLastSeen.setOperator("gte");
      Instant dateLastSeen = Instant.now().minusMillis(EndpointService.DELETE_TTL);
      filterLastSeen.setValue(dateLastSeen.toEpochMilli());
      bodyEndpoint.setFilters(List.of(filterGroupName, filterLastSeen));
      Map<String, Object> bodyCommand = new HashMap<>();
      bodyCommand.put("request_data", bodyEndpoint);
      String jsonResponse = this.post(ENDPOINTS_URI, bodyCommand);
      if (jsonResponse.isBlank()) {
        ResponseEndpoint response = new ResponseEndpoint();
        response.setReply(new PaloAltoCortexEndpointReply());
        return response;
      } else {
        return this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
      }
    } catch (IOException e) {
      log.error(
          String.format(
              "Error occurred during Palo Alto Cortex endpoints API request. Error: %s",
              e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), PALOALTOCORTEX_EXECUTOR_NAME);
    }
  }

  /**
   * Execute a payload through Palo Alto Cortex API runScript
   *
   * @param agentExternalReference to use for the payload
   * @param scriptId to use for the payload
   * @param command to use for the payload
   */
  public void executeScript(String agentExternalReference, String scriptId, Object command) {
    try {
      BodyScriptRun bodyScriptRun = new BodyScriptRun();
      bodyScriptRun.setScript_uid(scriptId);
      bodyScriptRun.setParameters_values(command);
      PaloAltoCortexFilter filter = new PaloAltoCortexFilter();
      filter.setField("endpoint_id_list");
      filter.setOperator("in");
      filter.setValue(List.of(agentExternalReference));
      bodyScriptRun.setFilters(List.of(filter));
      Map<String, Object> bodyCommand = new HashMap<>();
      bodyCommand.put("request_data", bodyScriptRun);
      String jsonResponse = this.post(RUN_SCRIPT_URI, bodyCommand);
      ResponseScriptRun response =
          this.objectMapper.readValue(jsonResponse, new TypeReference<>() {});
      if (response.getReply().getErr_code() != null) {
        log.error(
            "Error occurred while executing Palo Alto Cortex API run script for script "
                + scriptId
                + " and agent "
                + agentExternalReference
                + "\nError: "
                + response.getReply().getErr_code()
                + " - "
                + response.getReply().getErr_msg()
                + " - "
                + response.getReply().getErr_extra());
      }
    } catch (IOException e) {
      log.error(
          String.format(
              "Error occurred during Palo Alto Cortex runScript API request. Error: %s",
              e.getMessage()),
          e);
      throw new ExecutorException(e, e.getMessage(), PALOALTOCORTEX_EXECUTOR_NAME);
    }
  }

  private String post(@NotBlank final String uri, @NotNull final Map<String, Object> body)
      throws IOException {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(this.config.getApiUrl() + uri);
      // Headers
      httpPost.addHeader("x-xdr-auth-id", this.config.getApiKeyId());
      httpPost.addHeader("Authorization", this.config.getApiKey());
      httpPost.addHeader("content-type", "application/json");
      // Body
      StringEntity entity = new StringEntity(this.objectMapper.writeValueAsString(body));
      httpPost.setEntity(entity);
      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() >= 400) {
              log.warn(
                  "Unexpected response for HTTP POST Palo Alto Cortex: {} {}",
                  response.getCode(),
                  response.getReasonPhrase());
            }
            return EntityUtils.toString(response.getEntity());
          });
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for HTTP POST Palo Alto Cortex", e);
    }
  }
}
