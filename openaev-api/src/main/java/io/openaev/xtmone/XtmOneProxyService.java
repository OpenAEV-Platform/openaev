package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.PlatformSettingsService;
import io.openaev.xtmone.XtmOneClient.IntentCatalogAgent;
import io.openaev.xtmone.XtmOneClient.IntentCatalogEntry;
import io.openaev.xtmone.dto.ChatbotAgentOutput;
import io.openaev.xtmone.dto.ChatbotAgentResponse;
import io.openaev.xtmone.dto.ChatbotConfigOutput;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service layer for XTM One proxy operations.
 *
 * <p>Handles JWT issuance, HTTP proxying, agent invocations, and TTP extraction logic. The
 * controller ({@link XtmOneProxyApi}) delegates to this service for all business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XtmOneProxyService {

  private final XtmOneConfig xtmOneConfig;
  private final XtmOneRegistrationManager registrationManager;
  private final HttpClientFactory httpClientFactory;
  private final UserRepository userRepository;
  private final PlatformSettingsService platformSettingsService;
  private final ObjectMapper mapper;

  /**
   * Returns configuration info for the frontend (whether XTM One is configured, its URL).
   *
   * @return the chatbot configuration
   */
  public ChatbotConfigOutput getChatbotConfig() {
    return ChatbotConfigOutput.builder()
        .xtmOneUrl(xtmOneConfig.getUrl() != null ? xtmOneConfig.getUrl() : "")
        .xtmOneConfigured(xtmOneConfig.isConfigured())
        .build();
  }

  /**
   * Returns the list of enabled agents for a given intent from the cached intent catalog.
   *
   * @param intent the intent identifier (e.g. "ttp.extractor", "global.assistant")
   * @return list of agent descriptors
   */
  public List<ChatbotAgentOutput> getAgentsForIntent(String intent) {
    List<IntentCatalogEntry> catalog = registrationManager.getIntentCatalog();
    IntentCatalogEntry entry =
        catalog.stream().filter(e -> intent.equals(e.getIntent())).findFirst().orElse(null);

    if (entry == null || entry.getAgents() == null) {
      return Collections.emptyList();
    }

    return entry.getAgents().stream()
        .filter(IntentCatalogAgent::isEnabled)
        .map(
            a ->
                ChatbotAgentOutput.builder()
                    .id(a.getAgentId())
                    .name(a.getAgentName())
                    .slug(a.getAgentSlug() != null ? a.getAgentSlug() : "")
                    .description(a.getAgentDescription() != null ? a.getAgentDescription() : "")
                    .build())
        .toList();
  }

  /**
   * Checks connectivity to XTM One by verifying that the configuration is present and the intent
   * catalog has been successfully fetched at least once.
   *
   * @return a map with "status" ("up" or "down") and optional "detail"
   */
  public Map<String, String> healthCheck() {
    if (!xtmOneConfig.isConfigured()) {
      return Map.of("status", "down", "detail", "XTM One URL or token not configured");
    }
    List<IntentCatalogEntry> catalog = registrationManager.getIntentCatalog();
    if (catalog == null || catalog.isEmpty()) {
      return Map.of("status", "down", "detail", "Intent catalog not yet available");
    }
    return Map.of("status", "up");
  }

  /**
   * Proxies a POST request to XTM One's session creation endpoint.
   *
   * @param body the raw JSON body from the frontend
   * @return the proxied response
   */
  public ResponseEntity<String> createSession(String body) {
    String url = buildUrl("/api/v1/platform/chat/sessions");
    return proxyPost(url, body, 15);
  }

  /**
   * Streams a chat message response from XTM One as SSE events.
   *
   * @param body the raw JSON body from the frontend
   * @param outputStream the servlet output stream to write SSE data to
   */
  public void streamMessage(String body, OutputStream outputStream) {
    String url = buildUrl("/api/v1/platform/chat/messages");
    proxyStream(url, body, outputStream);
  }

  /**
   * Streams an agent response from XTM One as SSE events.
   *
   * @param agentSlug the agent slug to invoke
   * @param content the user content/prompt
   * @param outputStream the servlet output stream to write SSE data to
   */
  public void streamAgent(String agentSlug, String content, OutputStream outputStream) {
    String url = buildUrl("/api/v1/platform/chat/messages");
    try {
      ObjectNode requestBody = mapper.createObjectNode();
      requestBody.put("agent_slug", agentSlug);
      requestBody.put("content", content);
      requestBody.put("stream", true);
      proxyStream(url, mapper.writeValueAsString(requestBody), outputStream);
    } catch (Exception e) {
      log.error("[XTM One] Agent stream build error", e);
      writeSafeError(outputStream);
    }
  }

  /**
   * Calls an agent in non-streaming mode, returning the response synchronously.
   *
   * @param agentSlug the agent slug to invoke
   * @param content the user content/prompt
   * @param files optional file attachments (may be null)
   * @return the agent response DTO
   */
  public ChatbotAgentResponse callAgent(String agentSlug, String content, JsonNode files) {
    String url = buildUrl("/api/v1/platform/chat/messages");
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = buildPost(url, 120);

      ObjectNode requestBody = mapper.createObjectNode();
      requestBody.put("agent_slug", agentSlug);
      requestBody.put("content", content);
      requestBody.put("stream", false);
      if (files != null) {
        requestBody.set("files", files);
      }
      httpPost.setEntity(
          new StringEntity(mapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

      return httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            String responseBody = EntityUtils.toString(response.getEntity());
            int status = response.getCode();
            if (status >= 200 && status < 300) {
              try {
                JsonNode data = mapper.readTree(responseBody);
                String responseContent = data.has("content") ? data.get("content").asText() : "";
                return ChatbotAgentResponse.builder()
                    .content(responseContent)
                    .status("success")
                    .build();
              } catch (Exception e) {
                return ChatbotAgentResponse.builder()
                    .content(responseBody)
                    .status("success")
                    .build();
              }
            } else {
              String detail = "Upstream error";
              try {
                JsonNode errorData = mapper.readTree(responseBody);
                if (errorData.has("detail")) {
                  detail = errorData.get("detail").asText();
                }
              } catch (Exception ignored) {
              }
              return ChatbotAgentResponse.builder()
                  .content("")
                  .status("error")
                  .error(detail)
                  .code(status)
                  .build();
            }
          });
    } catch (Exception e) {
      log.error("[XTM One] Agent proxy error", e);
      return ChatbotAgentResponse.builder()
          .content("")
          .status("error")
          .error("Upstream service unavailable")
          .code(503)
          .build();
    }
  }

  /** Returns true if XTM One is configured. */
  public boolean isConfigured() {
    return xtmOneConfig.isConfigured();
  }

  // ── Private helpers ──────────────────────────────────────────────────

  /**
   * Issue an HMAC-SHA256 signed JWT for the current user, trusted by copilot via the "openaev"
   * issuer.
   */
  private String issuePlatformJwt() {
    var principal = SessionHelper.currentUser();
    User user = userRepository.findById(principal.getId()).orElse(null);
    String email = user != null ? user.getEmail() : "admin@openaev.io";
    String name = user != null ? user.getNameOrEmail() : "OpenAEV User";
    Instant now = Instant.now();
    byte[] keyBytes =
        xtmOneConfig.getToken().getBytes(StandardCharsets.UTF_8).length >= 32
            ? xtmOneConfig.getToken().getBytes(StandardCharsets.UTF_8)
            : java.util.Arrays.copyOf(
                xtmOneConfig.getToken().getBytes(StandardCharsets.UTF_8), 32);
    SecretKey signingKey = new SecretKeySpec(keyBytes, "HmacSHA256");
    String platformId = platformSettingsService.findSettings().getPlatformId();
    return Jwts.builder()
        .issuer("openaev")
        .claim("email", email)
        .claim("name", name)
        .claim("platform_id", platformId != null ? platformId : "")
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(300)))
        .signWith(signingKey)
        .compact();
  }

  private String buildUrl(String path) {
    return xtmOneConfig.getUrl().replaceAll("/+$", "") + path;
  }

  private HttpPost buildPost(String url, int timeoutSeconds) {
    HttpPost httpPost = new HttpPost(url);
    httpPost.addHeader("Authorization", "Bearer " + issuePlatformJwt());
    httpPost.addHeader("Content-Type", "application/json");
    httpPost.addHeader("X-Platform-Product", "openaev");
    String platformId = platformSettingsService.findSettings().getPlatformId();
    if (platformId != null) {
      httpPost.addHeader("X-Platform-Id", platformId);
    }
    httpPost.setConfig(
        RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(timeoutSeconds)).build());
    return httpPost;
  }

  private ResponseEntity<String> proxyPost(String url, String body, int timeoutSeconds) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = buildPost(url, timeoutSeconds);
      httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

      return httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            String responseBody = EntityUtils.toString(response.getEntity());
            int status = response.getCode();
            if (status >= 200 && status < 300) {
              return ResponseEntity.ok(responseBody);
            } else {
              return ResponseEntity.status(status).body(responseBody);
            }
          });
    } catch (Exception e) {
      log.error("[XTM One] Proxy error", e);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\": \"Upstream service unavailable\"}");
    }
  }

  private void proxyStream(String url, String body, OutputStream outputStream) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      httpPost.addHeader("Authorization", "Bearer " + issuePlatformJwt());
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.addHeader("X-Platform-Product", "openaev");
      String platformId = platformSettingsService.findSettings().getPlatformId();
      if (platformId != null) {
        httpPost.addHeader("X-Platform-Id", platformId);
      }
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.DISABLED).build());
      httpPost.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));

      httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            try (InputStream is = response.getEntity().getContent()) {
              byte[] buffer = new byte[4096];
              int bytesRead;
              while ((bytesRead = is.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                outputStream.flush();
              }
            }
            return null;
          });
    } catch (Exception e) {
      log.error("[XTM One] Streaming proxy error", e);
      writeSafeError(outputStream);
    }
  }

  private void writeSafeError(OutputStream outputStream) {
    try {
      outputStream.write(
          "data: {\"type\":\"error\",\"content\":\"Proxy error\"}\n\n"
              .getBytes(StandardCharsets.UTF_8));
      outputStream.flush();
    } catch (Exception ignored) {
      // Output stream already closed
    }
  }
}
