package io.openaev.executors.mde.client;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeAuthentication;
import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.mde.model.MdeDeviceGroup;
import io.openaev.executors.mde.model.MdeDeviceGroupListResponse;
import io.openaev.executors.mde.model.MdeDeviceListResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLEncoder;
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
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class MdeExecutorClient {

  /** MDE token validity is 1 hour; refresh 5 minutes before expiry. */
  private static final int AUTH_REFRESH_BEFORE_EXPIRY_SECONDS = 300;

  private static final String MACHINES_URI = "/machines";
  private static final String MACHINE_GROUPS_URI = "/machinegroups";

  private final MdeExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  private volatile Instant tokenExpiresAt = Instant.EPOCH;
  private volatile String token;

  // -- PUBLIC API --

  /**
   * Returns all MDE device groups (used to map IDs to names/descriptions for AssetGroup creation).
   */
  public List<MdeDeviceGroup> deviceGroups() {
    try {
      String json = get(MACHINE_GROUPS_URI);
      MdeDeviceGroupListResponse response = objectMapper.readValue(json, new TypeReference<>() {});
      return response.getValue() != null ? response.getValue() : List.of();
    } catch (Exception e) {
      log.error("Error fetching MDE machine groups: {}", e.getMessage(), e);
      throw new ExecutorException(e, e.getMessage(), MDE_EXECUTOR_NAME);
    }
  }

  /**
   * Returns all active MDE devices without group filter (for tenants with no RBAC device groups).
   */
  public List<MdeDevice> devicesAll() {
    try {
      String formattedDateTime =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now().minusMillis(io.openaev.service.EndpointService.DELETE_TTL));
      String filterValue =
          URLEncoder.encode("lastSeen gt " + formattedDateTime, StandardCharsets.UTF_8);
      String json = get(MACHINES_URI + "?$filter=" + filterValue + "&$top=10000");
      MdeDeviceListResponse response = objectMapper.readValue(json, new TypeReference<>() {});
      return response.getValue() != null ? response.getValue() : List.of();
    } catch (Exception e) {
      log.error("Error fetching all MDE devices: {}", e.getMessage(), e);
      throw new ExecutorException(e, e.getMessage(), MDE_EXECUTOR_NAME);
    }
  }

  /**
   * Returns all devices belonging to the given MDE device group (rbacGroupId).
   *
   * <p>The MDE API filters machines by {@code rbacGroupId}. For active machines only, we also
   * filter by {@code lastSeen} within the active threshold.
   */
  public List<MdeDevice> devices(String deviceGroupId) {
    try {
      String formattedDateTime =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
              .withZone(ZoneOffset.UTC)
              .format(Instant.now().minusMillis(io.openaev.service.EndpointService.DELETE_TTL));
      String filterValue =
          URLEncoder.encode(
              "rbacGroupId eq " + deviceGroupId + " and lastSeen gt " + formattedDateTime,
              StandardCharsets.UTF_8);
      String json = get(MACHINES_URI + "?$filter=" + filterValue + "&$top=10000");
      MdeDeviceListResponse response = objectMapper.readValue(json, new TypeReference<>() {});
      return response.getValue() != null ? response.getValue() : List.of();
    } catch (Exception e) {
      log.error("Error fetching MDE devices for group {}: {}", deviceGroupId, e.getMessage(), e);
      throw new ExecutorException(e, e.getMessage(), MDE_EXECUTOR_NAME);
    }
  }

  /**
   * Runs a Live Response script on a single MDE machine. The call is async (fire-and-forget via
   * {@code @Async}) — MDE creates a machine action that executes the script independently.
   *
   * @param machineId MDE machine ID (40-char hex)
   * @param scriptName name of the script pre-uploaded to the MDE Live Response Library
   * @param encodedCommand Base64-encoded command string passed as script argument
   */
  @Async
  public void executeAction(
      @NotBlank String machineId, @NotBlank String scriptName, @NotBlank String encodedCommand) {
    try {
      Map<String, Object> param1 = new HashMap<>();
      param1.put("key", "ScriptName");
      param1.put("value", scriptName);
      // encodedCommand is raw Base64 — the subprocessor script decodes and executes it directly.
      Map<String, Object> param2 = new HashMap<>();
      param2.put("key", "Args");
      param2.put("value", encodedCommand);

      Map<String, Object> command = new HashMap<>();
      command.put("type", "RunScript");
      command.put("params", List.of(param1, param2));

      Map<String, Object> body = new HashMap<>();
      body.put("Commands", List.of(command));
      body.put("Comment", "OpenAEV payload execution");

      log.debug(
          "Dispatching MDE Live Response RunScript: machineId={}, scriptName='{}'",
          machineId,
          scriptName);
      post(MACHINES_URI + "/" + machineId + "/runliveresponse", body);
    } catch (IOException e) {
      log.error(
          "Error executing MDE Live Response action on machine {}: {}",
          machineId,
          e.getMessage(),
          e);
    }
  }

  // -- PRIVATE --

  private String get(@NotBlank final String uri) throws IOException {
    ensureValidToken();
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(config.getApiUrl() + uri);
      httpGet.addHeader("Authorization", "Bearer " + token);
      httpGet.addHeader("Accept", "application/json");
      return httpClient.execute(httpGet, response -> EntityUtils.toString(response.getEntity()));
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + uri, e);
    }
  }

  private String post(@NotBlank final String uri, @NotNull final Map<String, Object> body)
      throws IOException {
    ensureValidToken();
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(config.getApiUrl() + uri);
      httpPost.addHeader("Authorization", "Bearer " + token);
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(body)));
      return httpClient.execute(
          httpPost,
          response -> {
            int code = response.getCode();
            String respBody =
                response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            // MDE returns non-2xx (e.g. 403 DisallowedOperation when a device has no automation
            // level, 400 OsPlatformNotSupported) with an error body. Surface it instead of
            // silently discarding the response, otherwise the inject stays PENDING with no clue.
            // The values are inlined in the message (not SLF4J placeholders) so the HTTP code and
            // MDE error body remain visible in log pipelines that drop structured arguments.
            if (code < 200 || code >= 300) {
              log.error(
                  "MDE API POST " + uri + " failed: HTTP " + code + " body=" + respBody);
            }
            return respBody;
          });
    } catch (IOException e) {
      throw new ClientProtocolException("Unexpected response for request on: " + uri, e);
    }
  }

  private void ensureValidToken() throws IOException {
    if (Instant.now().isAfter(tokenExpiresAt.minusSeconds(AUTH_REFRESH_BEFORE_EXPIRY_SECONDS))) {
      synchronized (this) {
        if (Instant.now()
            .isAfter(tokenExpiresAt.minusSeconds(AUTH_REFRESH_BEFORE_EXPIRY_SECONDS))) {
          authenticate();
        }
      }
    }
  }

  private void authenticate() throws IOException {
    String tokenUrl = config.getAuthUrl() + "/" + config.getAzureTenantId() + "/oauth2/v2.0/token";
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(tokenUrl);
      httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair("client_id", config.getClientId()));
      params.add(new BasicNameValuePair("client_secret", config.getClientSecret()));
      params.add(
          new BasicNameValuePair("scope", "https://api.securitycenter.microsoft.com/.default"));
      params.add(new BasicNameValuePair("grant_type", "client_credentials"));
      httpPost.setEntity(new UrlEncodedFormEntity(params));
      String jsonResponse =
          httpClient.execute(httpPost, response -> EntityUtils.toString(response.getEntity()));
      MdeAuthentication auth = objectMapper.readValue(jsonResponse, new TypeReference<>() {});
      if (auth.getAccess_token() == null || auth.getAccess_token().isBlank()) {
        throw new ClientProtocolException(
            "MDE authentication failed: empty access_token. "
                + "Check client_id, client_secret and azure_tenant_id configuration.");
      }
      if (auth.getExpires_in() <= 0) {
        throw new ClientProtocolException(
            "MDE authentication failed: invalid expires_in=" + auth.getExpires_in());
      }
      token = auth.getAccess_token();
      tokenExpiresAt = Instant.now().plusSeconds(auth.getExpires_in());
    } catch (IOException e) {
      throw new ClientProtocolException("MDE authentication failed", e);
    }
  }
}
