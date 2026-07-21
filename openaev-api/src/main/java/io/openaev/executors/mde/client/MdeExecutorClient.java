package io.openaev.executors.mde.client;

import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeAdvancedQueryResponse;
import io.openaev.executors.mde.model.MdeAuthentication;
import io.openaev.executors.mde.model.MdeDevice;
import io.openaev.executors.mde.model.MdeDeviceListResponse;
import io.openaev.executors.mde.model.MdeMachineAction;
import io.openaev.executors.mde.model.MdeMachineActionListResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class MdeExecutorClient {

  /** MDE token validity is 1 hour; refresh 5 minutes before expiry. */
  private static final int AUTH_REFRESH_BEFORE_EXPIRY_SECONDS = 300;

  private static final String MACHINES_URI = "/machines";
  private static final String MACHINE_ACTIONS_URI = "/machineactions";
  private static final String ADVANCED_QUERIES_URI = "/advancedqueries/run";

  private final MdeExecutorConfig config;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClientFactory httpClientFactory;

  private volatile Instant tokenExpiresAt = Instant.EPOCH;
  private volatile String token;

  // -- PUBLIC API --

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
   * Runs a Live Response script on a single MDE machine. MDE creates a machine action that executes
   * the script independently; OpenAEV then awaits the implant callback. Throttled dispatch across
   * agents is handled by {@code MdeExecutorContextService.executeActions}, which schedules these
   * calls in rate-limited batches.
   *
   * @param machineId MDE machine ID (40-char hex)
   * @param scriptName name of the script pre-uploaded to the MDE Live Response Library
   * @param encodedCommand Base64-encoded command string passed as script argument
   */
  public void executeAction(
      @NotBlank String machineId, @NotBlank String scriptName, @NotBlank String encodedCommand) {
    try {
      // MDE allows a single Live Response session per machine. Clear any stale Pending action
      // first (e.g. left by a previous dispatch when the machine was offline) so the machine is not
      // permanently blocked. Fresh sessions from concurrent injects are preserved (see threshold).
      cancelStalePendingLiveResponse(machineId);
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
    } catch (Exception e) {
      log.error(
          "Error executing MDE Live Response action on machine {}: {}",
          machineId,
          e.getMessage(),
          e);
    }
  }

  /**
   * Returns the freshest activity timestamp per device seen in the last {@code windowMinutes},
   * queried from MDE Advanced Hunting ({@code DeviceInfo} table). The machines inventory {@code
   * lastSeen} refreshes only on a slow (up to daily) cadence and badly lags real connectivity, so
   * it cannot be used to decide whether a device is currently reachable for Live Response. Advanced
   * Hunting reflects near real-time device activity instead.
   *
   * @return device id → last activity instant, or {@code null} when Advanced Hunting is unavailable
   *     (e.g. the app registration lacks the {@code AdvancedQuery.Read.All} permission), so callers
   *     can fall back to the inventory data.
   */
  public Map<String, Instant> getRecentDeviceActivity(int windowMinutes) {
    try {
      String query =
          "DeviceInfo | where Timestamp > ago("
              + windowMinutes
              + "m) | summarize LastSeen=max(Timestamp) by DeviceId";
      Map<String, Object> body = new HashMap<>();
      body.put("Query", query);
      String json = post(ADVANCED_QUERIES_URI, body);
      MdeAdvancedQueryResponse response = objectMapper.readValue(json, new TypeReference<>() {});
      if (response.getResults() == null) {
        // No "Results" field means the call did not succeed (error body): treat as unavailable.
        return null;
      }
      Map<String, Instant> activityByDeviceId = new HashMap<>();
      response
          .getResults()
          .forEach(
              row -> {
                if (row.getDeviceId() != null && row.getLastSeen() != null) {
                  try {
                    activityByDeviceId.put(row.getDeviceId(), Instant.parse(row.getLastSeen()));
                  } catch (Exception ignored) {
                    // Skip rows with an unparseable timestamp.
                  }
                }
              });
      return activityByDeviceId;
    } catch (Exception e) {
      log.warn(
          "MDE Advanced Hunting unavailable (add AdvancedQuery.Read.All to the app registration for"
              + " accurate device activity): {}",
          e.getMessage());
      return null;
    }
  }

  // -- PRIVATE --

  /**
   * Cancels stale Pending Live Response actions on a machine before a new dispatch. MDE permits a
   * single Live Response session per machine, so a Pending action left by an earlier dispatch (for
   * instance when the machine was offline and never picked it up) blocks every later inject on that
   * machine. Only actions older than {@code stalePendingThresholdMinutes} are cancelled, so a fresh
   * session from a concurrent inject is never touched. Best-effort: failures are logged and the
   * dispatch still proceeds.
   */
  private void cancelStalePendingLiveResponse(@NotBlank final String machineId) {
    try {
      String filter =
          URLEncoder.encode(
              "machineId eq '" + machineId + "' and type eq 'LiveResponse' and status eq 'Pending'",
              StandardCharsets.UTF_8);
      String json = get(MACHINE_ACTIONS_URI + "?$filter=" + filter + "&$top=50");
      MdeMachineActionListResponse response =
          objectMapper.readValue(json, new TypeReference<>() {});
      if (response.getValue() == null || response.getValue().isEmpty()) {
        return;
      }
      Integer thresholdMinutes = config.getStalePendingThresholdMinutes();
      long thresholdSeconds =
          (thresholdMinutes != null
                  ? thresholdMinutes
                  : MdeExecutorConfig.DEFAULT_STALE_PENDING_THRESHOLD_MINUTES)
              * 60L;
      Instant staleBefore = Instant.now().minusSeconds(thresholdSeconds);
      for (MdeMachineAction action : response.getValue()) {
        if (isStalePendingAction(action, staleBefore)) {
          cancelMachineAction(action.getId());
        }
      }
    } catch (Exception e) {
      // Best-effort cleanup: never let a failure here block the actual dispatch below.
      log.warn(
          "Could not clear stale pending Live Response on machine {}: {}",
          machineId,
          e.getMessage());
    }
  }

  private static boolean isStalePendingAction(MdeMachineAction action, Instant staleBefore) {
    if (action.getId() == null || action.getCreationDateTimeUtc() == null) {
      return false;
    }
    try {
      return Instant.parse(action.getCreationDateTimeUtc()).isBefore(staleBefore);
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  private void cancelMachineAction(@NotBlank final String actionId) throws IOException {
    Map<String, Object> body = new HashMap<>();
    body.put("Comment", "OpenAEV: cancel stale pending Live Response blocking new sessions");
    post(MACHINE_ACTIONS_URI + "/" + actionId + "/cancel", body);
    log.info("Cancelled stale pending MDE Live Response action {}", actionId);
  }

  private String get(@NotBlank final String uri) throws IOException {
    ensureValidToken();
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpGet httpGet = new HttpGet(config.getApiUrl() + uri);
      httpGet.addHeader("Authorization", "Bearer " + token);
      httpGet.addHeader("Accept", "application/json");
      return httpClient.execute(
          httpGet,
          response -> {
            int code = response.getCode();
            String respBody =
                response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            // Surface non-2xx GETs: an empty/error body used to blow up downstream as an opaque
            // Jackson "No content to map" error, hiding the real HTTP cause (see post() for the
            // same pattern). Values are inlined so the code/body survive log pipelines that drop
            // structured SLF4J arguments.
            if (code < 200 || code >= 300) {
              String safeBody =
                  respBody.length() > 500 ? respBody.substring(0, 500) + "…(truncated)" : respBody;
              log.error("MDE API GET " + uri + " failed: HTTP " + code + " body=" + safeBody);
            }
            return respBody;
          });
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
              // MDE error responses carry a short structured error (code/message), not the request
              // body, but truncate defensively so an unexpectedly large or reflective body is never
              // dumped wholesale into logs.
              String safeBody =
                  respBody.length() > 500 ? respBody.substring(0, 500) + "…(truncated)" : respBody;
              log.error("MDE API POST " + uri + " failed: HTTP " + code + " body=" + safeBody);
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
