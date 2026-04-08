package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.SessionHelper;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmone.XtmOneClient.IntentCatalogAgent;
import io.openaev.xtmone.XtmOneClient.IntentCatalogEntry;
import jakarta.annotation.Resource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Proxy endpoints between OpenAEV frontend and XTM One (filigran-copilot).
 *
 * <p>Equivalent to OpenCTI's httpChatbotProxy.ts. The frontend never calls XTM One directly —
 * everything goes through these proxy endpoints.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class XtmOneProxyApi extends RestBehavior {

  private final XtmOneConfig xtmOneConfig;
  private final XtmOneRegistrationManager registrationManager;
  private final HttpClientFactory httpClientFactory;
  private final UserRepository userRepository;
  private final io.openaev.rest.attack_pattern.service.AttackPatternService attackPatternService;
  @Resource private ObjectMapper mapper;

  /**
   * Issue a minimal unsigned JWT for the current user, trusted by copilot via
   * the "openaev" issuer in TRUSTED_PLATFORM_ISSUERS.
   */
  private String issuePlatformJwt() {
    var principal = SessionHelper.currentUser();
    User user = userRepository.findById(principal.getId()).orElse(null);
    String email = user != null ? user.getEmail() : "admin@openaev.io";
    String name = user != null ? user.getNameOrEmail() : "OpenAEV User";
    long now = Instant.now().getEpochSecond();
    // Build an unsigned JWT (alg: none) — copilot trusts "openaev" issuer without signature verification
    String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
        String.format("{\"iss\":\"openaev\",\"email\":\"%s\",\"name\":\"%s\",\"iat\":%d,\"exp\":%d}",
            email.replace("\"", ""), name.replace("\"", ""), now, now + 300)
        .getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".";
  }

  // ── GET /api/chatbot/config ────────────────────────────────────────────

  @GetMapping("/api/chatbot/config")
  public ResponseEntity<Map<String, Object>> getChatbotConfig() {
    return ResponseEntity.ok(
        Map.of(
            "xtm_one_url", xtmOneConfig.getUrl() != null ? xtmOneConfig.getUrl() : "",
            "xtm_one_configured", xtmOneConfig.isConfigured()));
  }

  // ── GET /api/chatbot/agents ────────────────────────────────────────────

  @GetMapping("/api/chatbot/agents")
  public ResponseEntity<List<Map<String, String>>> getChatbotAgents(
      @RequestParam(value = "intent", defaultValue = "global.assistant") String intent) {

    List<IntentCatalogEntry> catalog = registrationManager.getIntentCatalog();
    IntentCatalogEntry entry =
        catalog.stream().filter(e -> intent.equals(e.getIntent())).findFirst().orElse(null);

    if (entry == null || entry.getAgents() == null) {
      return ResponseEntity.ok(Collections.emptyList());
    }

    List<Map<String, String>> agents =
        entry.getAgents().stream()
            .filter(IntentCatalogAgent::isEnabled)
            .map(
                a ->
                    Map.of(
                        "id", a.getAgentId(),
                        "name", a.getAgentName(),
                        "slug", a.getAgentSlug() != null ? a.getAgentSlug() : "",
                        "description",
                            a.getAgentDescription() != null ? a.getAgentDescription() : ""))
            .toList();

    return ResponseEntity.ok(agents);
  }

  // ── POST /api/chatbot/sessions ─────────────────────────────────────────

  @PostMapping("/api/chatbot/sessions")
  public ResponseEntity<String> postChatbotSession(@RequestBody String body) {
    if (!xtmOneConfig.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\": \"XTM One is not configured\"}");
    }

    String url =
        xtmOneConfig.getUrl().replaceAll("/+$", "") + "/api/v1/platform/chat/sessions";
    return proxyPost(url, body, 15);
  }

  // ── POST /api/chatbot/messages (streaming SSE) ─────────────────────────

  @PostMapping("/api/chatbot/messages")
  public ResponseEntity<StreamingResponseBody> postChatbotMessage(@RequestBody String body) {
    if (!xtmOneConfig.isConfigured()) {
      StreamingResponseBody errorBody =
          out ->
              out.write(
                  "{\"error\": \"XTM One is not configured\"}"
                      .getBytes(StandardCharsets.UTF_8));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.APPLICATION_JSON)
          .body(errorBody);
    }

    String url =
        xtmOneConfig.getUrl().replaceAll("/+$", "") + "/api/v1/platform/chat/messages";

    StreamingResponseBody responseBody =
        outputStream -> {
          try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.addHeader("Authorization", "Bearer " + issuePlatformJwt());
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.addHeader("X-Platform-Product", "openaev");
            httpPost.setConfig(
                RequestConfig.custom()
                    .setResponseTimeout(Timeout.DISABLED)
                    .build());
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
            log.error("[XTM One] Streaming proxy error: {}", e.getMessage(), e);
            String errorEvent =
                "data: "
                    + "{\"type\":\"error\",\"content\":\"Proxy error: "
                    + e.getMessage().replace("\"", "'")
                    + "\"}\n\n";
            outputStream.write(errorEvent.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
          }
        };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_EVENT_STREAM);
    headers.setCacheControl("no-cache, no-transform");
    headers.set("Connection", "keep-alive");
    headers.set("X-Accel-Buffering", "no");
    headers.set("Transfer-Encoding", "chunked");

    return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
  }

  // ── POST /api/chatbot/agent (non-streaming) ────────────────────────────

  @PostMapping("/api/chatbot/agent")
  public ResponseEntity<String> postAgentMessage(@RequestBody JsonNode body) {
    if (!xtmOneConfig.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\": \"XTM One is not configured\"}");
    }

    String agentSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : null;

    if (agentSlug == null || content == null) {
      return ResponseEntity.badRequest()
          .body("{\"error\": \"agent_slug and content are required\"}");
    }

    String url =
        xtmOneConfig.getUrl().replaceAll("/+$", "") + "/api/v1/platform/chat/messages";

    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      httpPost.addHeader("Authorization", "Bearer " + issuePlatformJwt());
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.addHeader("X-Platform-Product", "openaev");
      httpPost.setConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(120)).build());

      ObjectNode requestBody = mapper.createObjectNode();
      requestBody.put("agent_slug", agentSlug);
      requestBody.put("content", content);
      requestBody.put("stream", false);

      // Forward file attachments if present
      if (body.has("files")) {
        requestBody.set("files", body.get("files"));
      }

      httpPost.setEntity(
          new StringEntity(
              mapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

      return httpClient.execute(
          httpPost,
          (ClassicHttpResponse response) -> {
            String responseBody = EntityUtils.toString(response.getEntity());
            int status = response.getCode();
            if (status >= 200 && status < 300) {
              try {
                JsonNode data = mapper.readTree(responseBody);
                String responseContent =
                    data.has("content") ? data.get("content").asText() : "";
                return ResponseEntity.ok(
                    mapper.writeValueAsString(
                        Map.of("content", responseContent, "status", "success")));
              } catch (Exception e) {
                return ResponseEntity.ok(
                    mapper.writeValueAsString(
                        Map.of("content", responseBody, "status", "success")));
              }
            } else {
              String detail = responseBody;
              try {
                JsonNode errorData = mapper.readTree(responseBody);
                if (errorData.has("detail")) {
                  detail = errorData.get("detail").asText();
                }
              } catch (Exception ignored) {
              }
              return ResponseEntity.ok(
                  mapper.writeValueAsString(
                      Map.of(
                          "content", "",
                          "status", "error",
                          "error", detail,
                          "code", status)));
            }
          });
    } catch (Exception e) {
      log.error("[XTM One] Agent proxy error: {}", e.getMessage(), e);
      try {
        return ResponseEntity.ok(
            mapper.writeValueAsString(
                Map.of(
                    "content", "",
                    "status", "error",
                    "error", e.getMessage(),
                    "code", 503)));
      } catch (Exception jsonErr) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("{\"error\": \"Internal error\"}");
      }
    }
  }

  // ── POST /api/chatbot/ttp-extract ──────────────────────────────────────
  // Calls the TTP extractor agent and resolves technique IDs to OpenAEV
  // internal attack pattern UUIDs.

  @PostMapping("/api/chatbot/ttp-extract")
  public ResponseEntity<List<String>> extractTTPs(@RequestBody JsonNode body) {
    if (!xtmOneConfig.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Collections.emptyList());
    }

    String agentSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : "Extract TTPs";

    if (agentSlug == null) {
      return ResponseEntity.badRequest().body(Collections.emptyList());
    }

    // Build the request to copilot
    String url =
        xtmOneConfig.getUrl().replaceAll("/+$", "") + "/api/v1/platform/chat/messages";
    try {
      ObjectNode requestBody = mapper.createObjectNode();
      requestBody.put("agent_slug", agentSlug);
      requestBody.put("content", content);
      requestBody.put("stream", false);
      if (body.has("files") && body.get("files").size() > 0) {
        requestBody.set("files", body.get("files"));
      }

      ResponseEntity<String> proxyResp =
          proxyPost(url, mapper.writeValueAsString(requestBody), 120);

      if (!proxyResp.getStatusCode().is2xxSuccessful() || proxyResp.getBody() == null) {
        return ResponseEntity.ok(Collections.emptyList());
      }

      // Parse copilot response → extract technique IDs from predictions
      JsonNode respJson = mapper.readTree(proxyResp.getBody());
      String agentContent = respJson.has("content") ? respJson.get("content").asText() : "";

      // The agent content is a JSON string with extraction results
      Set<String> techniqueIds = new HashSet<>();
      try {
        JsonNode parsed = mapper.readTree(agentContent);
        JsonNode filesNode = parsed.has("files") ? parsed.get("files") : null;
        if (filesNode != null && filesNode.isArray()) {
          for (JsonNode fileEntry : filesNode) {
            JsonNode extraction = fileEntry.get("extraction");
            if (extraction != null) {
              extraction.fields().forEachRemaining(field -> {
                for (JsonNode chunk : field.getValue()) {
                  JsonNode predictions = chunk.get("predictions");
                  if (predictions != null) {
                    predictions.fieldNames().forEachRemaining(techniqueIds::add);
                  }
                }
              });
            }
          }
        }
        // Also extract from top-level predictions (text-only extraction)
        if (parsed.isArray()) {
          for (JsonNode item : parsed) {
            for (JsonNode chunk : item) {
              JsonNode predictions = chunk.get("predictions");
              if (predictions != null) {
                predictions.fieldNames().forEachRemaining(techniqueIds::add);
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("[XTM One] Failed to parse TTP extraction response: {}", e.getMessage());
        return ResponseEntity.ok(Collections.emptyList());
      }

      if (techniqueIds.isEmpty()) {
        return ResponseEntity.ok(Collections.emptyList());
      }

      // Resolve external technique IDs to internal OpenAEV attack pattern UUIDs
      List<String> internalIds = attackPatternService
          .getAttackPatternsByExternalIds(techniqueIds).stream()
          .map(ap -> ap.getId())
          .toList();

      return ResponseEntity.ok(internalIds);
    } catch (Exception e) {
      log.error("[XTM One] TTP extraction error: {}", e.getMessage(), e);
      return ResponseEntity.ok(Collections.emptyList());
    }
  }

  // ── Private helper ─────────────────────────────────────────────────────

  private ResponseEntity<String> proxyPost(String url, String body, int timeoutSeconds) {
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      HttpPost httpPost = new HttpPost(url);
      httpPost.addHeader("Authorization", "Bearer " + issuePlatformJwt());
      httpPost.addHeader("Content-Type", "application/json");
      httpPost.setConfig(
          RequestConfig.custom()
              .setResponseTimeout(Timeout.ofSeconds(timeoutSeconds))
              .build());
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
      log.error("[XTM One] Proxy error to {}: {}", url, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
    }
  }
}
