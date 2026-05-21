package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.jsonwebtoken.Jwts;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.User;
import io.openaev.service.UserService;
import io.openaev.service.xtm_auth.XtmAuthKeyService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class XtmOneClient {

  private static final String INTENTS_CATALOG_AGENTS_PATH = "/api/v1/intents/catalog";
  private static final int AGENT_LIST_TIMEOUT_SECONDS = 10;

  private final XtmOneConfig config;
  private final ObjectMapper objectMapper;
  private final XtmAuthKeyService keyService;
  private final OpenAEVConfig openAEVConfig;
  private final HttpClientFactory httpClientFactory;
  private final UserService userService;

  public String issueAuthenticationJwt(String userId, String userName, String userEmail) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(keyService.getKid())
        .and()
        .issuer(openAEVConfig.getBaseUrl())
        .subject(userId)
        .claim("name", userName)
        .claim("email", userEmail)
        .audience()
        .add(config.getUrl())
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofMinutes(10))))
        .id(UUID.randomUUID().toString())
        .signWith(keyService.getKeyPair().getPrivate(), Jwts.SIG.EdDSA)
        .compact();
  }

  String issueJwtForCurrentUser() {
    User user = userService.currentUser();
    return issueAuthenticationJwt(
        user.getId(), user.getName() != null ? user.getName() : user.getEmail(), user.getEmail());
  }

  private void addChatHeaders(HttpMessage request, String jwt) {
    request.addHeader("Authorization", "Bearer " + jwt);
    request.addHeader("X-Platform-Product", "openaev");
    request.addHeader(
        "X-Platform-URL", config.getPlatformUrl() != null ? config.getPlatformUrl() : "");
    var version = config.getPlatformVersion();
    if (version != null && !version.isBlank()) {
      request.addHeader("X-Platform-Version", version);
    }
  }

  private HttpPost chatPostBuilder(String path, String jwt, String json) {
    HttpPost httpPost = new HttpPost(config.getUrl() + path);
    addChatHeaders(httpPost, jwt);
    httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
    return httpPost;
  }

  private HttpGet chatGetBuilder(String path, String jwt) {
    HttpGet httpGet = new HttpGet(config.getUrl() + path);
    addChatHeaders(httpGet, jwt);
    return httpGet;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> register(
      String platformIdentifier,
      String platformUrl,
      String platformTitle,
      String platformVersion,
      String platformId,
      String enterpriseLicensePem,
      String licenseType,
      String businessVertical,
      List<Map<String, String>> intents) {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      Map<String, Object> body = new HashMap<>();
      body.put("platform_identifier", platformIdentifier);
      body.put("platform_url", platformUrl);
      body.put("platform_title", platformTitle);
      body.put("platform_version", platformVersion);
      body.put("platform_id", platformId != null ? platformId : "");
      body.put("enterprise_license_pem", enterpriseLicensePem != null ? enterpriseLicensePem : "");
      body.put("license_type", licenseType != null ? licenseType : "");
      if (businessVertical != null) body.put("business_vertical", businessVertical);
      body.put("intents", intents != null ? intents : List.of());
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = new HttpPost(config.getUrl() + "/api/v1/platform/register");
      httpPost.addHeader("Authorization", "Bearer " + config.getToken());
      httpPost.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(15)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            log.warn(
                "[XTM One] Registration failed: HTTP {} — {}",
                response.getCode(),
                EntityUtils.toString(response.getEntity()));
            return null;
          });
    } catch (Exception e) {
      log.warn("[XTM One] Registration error.", e);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public List<ChatbotAgentOutput> listChatAgents(String intentName) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      String jwt = issueJwtForCurrentUser();
      HttpGet httpGet =
          chatGetBuilder(INTENTS_CATALOG_AGENTS_PATH + "?vertical=aev&intent=" + intentName, jwt);
      httpGet.setConfig(
          RequestConfig.custom()
              .setResponseTimeout(Timeout.ofSeconds(AGENT_LIST_TIMEOUT_SECONDS))
              .build());
      return httpClient.execute(httpGet, this::handleAgentListResponse);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.error("[XTM One] List chat agents unexpected error.", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "[XTM One] Unexpected error while listing chat agents",
          e);
    }
  }

  private List<ChatbotAgentOutput> handleAgentListResponse(ClassicHttpResponse response)
      throws IOException, ParseException {
    int code = response.getCode();
    String body = EntityUtils.toString(response.getEntity());

    return switch (code) {
      case 200 -> {
        List<Map<String, Object>> catalog = objectMapper.readValue(body, List.class);
        List<ChatbotAgentOutput> agents =
            catalog == null
                ? List.of()
                : catalog.stream()
                    .filter(item -> item.get("agents") instanceof List<?>)
                    .flatMap(item -> ((List<?>) item.get("agents")).stream())
                    .map(agent -> objectMapper.convertValue(agent, ChatbotAgentOutput.class))
                    .toList();

        if (agents.isEmpty()) {
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "[XTM One] No chat agents available");
        }
        yield agents;
      }
      case 401 ->
          throw new ResponseStatusException(
              HttpStatus.UNAUTHORIZED, "[XTM One] Unauthorized access to chat agents");
      case 403 ->
          throw new ResponseStatusException(
              HttpStatus.FORBIDDEN, "[XTM One] Forbidden access to chat agents");
      case 404 ->
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "[XTM One] Chat agents endpoint not found");
      case 503 ->
          throw new ResponseStatusException(
              HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service unavailable");
      default ->
          throw new ResponseStatusException(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "[XTM One] Unexpected response from chat agents: HTTP " + code);
    };
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> createChatSession(String agentSlug, String conversationId) {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      if (conversationId != null) body.put("conversation_id", conversationId);
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/chat/sessions", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            log.warn("[XTM One] Create session failed: HTTP {}", response.getCode());
            return null;
          });
    } catch (Exception e) {
      log.warn("[XTM One] Create session error: ", e);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public String uploadChatFile(String conversationId, MultipartFile file) {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      String jwt = issueJwtForCurrentUser();
      String encodedConversationId = URLEncoder.encode(conversationId, StandardCharsets.UTF_8);
      HttpPost httpPost =
          new HttpPost(
              config.getUrl()
                  + "/api/v1/chat/conversations/"
                  + conversationId
                  + "/upload?create_message=false");
      addChatHeaders(httpPost, jwt);
      httpPost.setEntity(
          MultipartEntityBuilder.create()
              .addBinaryBody(
                  "file",
                  file.getInputStream(),
                  ContentType.APPLICATION_OCTET_STREAM,
                  file.getOriginalFilename())
              .build());
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMinutes(2)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              Map<String, Object> result =
                  objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
              Object fileId = result.get("file_id");
              return fileId != null ? fileId.toString() : null;
            }
            log.warn(
                "[XTM One] Upload file failed: HTTP {}, filename={}",
                response.getCode(),
                file.getOriginalFilename());
            return null;
          });
    } catch (Exception e) {
      log.warn("[XTM One] Upload file error, filename={}", file.getOriginalFilename(), e);
    }
    return null;
  }

  /**
   * Streams a chat message response from XTM One. The provided consumer receives the SSE input
   * stream and is responsible for reading it. The HTTP client and stream are automatically closed
   * when the consumer returns or throws.
   *
   * @param content message content
   * @param conversationId optional conversation ID
   * @param agentSlug optional agent slug
   * @param streamConsumer callback that receives the SSE {@link InputStream}
   */
  public void streamChatMessage(
      String content, String conversationId, String agentSlug, StreamConsumer streamConsumer) {
    if (!config.isConfigured()) {
      log.warn("[XTM One] Chat message skipped: not configured");
      return;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      if (conversationId != null) body.put("conversation_id", conversationId);
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/chat/messages", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMinutes(15)).build());

      httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              try (InputStream stream = response.getEntity().getContent()) {
                streamConsumer.accept(stream);
              }
            } else {
              throw mapUpstreamError(response);
            }
            return null;
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (java.net.SocketTimeoutException e) {
      log.warn("[XTM One] Chat message timed out, agent={}", agentSlug, e);
      throw new ResponseStatusException(
          HttpStatus.GATEWAY_TIMEOUT, "[XTM One] Chat message timed out", e);
    } catch (Exception e) {
      log.warn("[XTM One] Chat message error, agent={}.", agentSlug, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Chat message failed", e);
    }
  }

  /** Functional interface for consuming an SSE stream. */
  @FunctionalInterface
  public interface StreamConsumer {
    void accept(InputStream stream) throws IOException;
  }

  /**
   * Synchronous (non-streaming) agent call via the chat messages endpoint. Collects the full SSE
   * stream, extracts the final "done" or accumulated "stream" content, and returns it.
   *
   * <p>Callers should pass a per-user JWT (issued via {@link #issueAuthenticationJwt}) so the
   * upstream XTM One side can attribute the call to the real user. Use {@link
   *
   * @param agentSlug the agent slug to route the request to
   * @param content the user prompt / content
   * @param filesNode optional base64-encoded file attachments (may be {@code null})
   * @return the agent's final text content, or {@code null} on failure
   */
  public String callAgentSync(String agentSlug, String content, ArrayNode filesNode) {
    if (!config.isConfigured()) {
      log.warn("[XTM One] callAgentSync skipped: not configured");
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientCustom()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      body.put("agent_slug", agentSlug);
      if (filesNode != null) {
        body.put("files", objectMapper.treeToValue(filesNode, Object.class));
      }

      HttpPost httpPost =
          chatPostBuilder(
              "/api/v1/platform/chat/messages", jwt, objectMapper.writeValueAsString(body));
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMinutes(5)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() != 200) {
              throw mapUpstreamError(response);
            }
            // Read the SSE stream and collect content
            String raw = EntityUtils.toString(response.getEntity());
            return extractContentFromSse(raw);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[XTM One] callAgentSync error, agent={}.", agentSlug, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Agent call failed", e);
    }
  }

  /**
   * Parses an SSE response body and extracts the agent content. Returns the "done" event content if
   * present, otherwise the accumulated "stream" chunks.
   */
  private String extractContentFromSse(String sseBody) {
    StringBuilder accumulated = new StringBuilder();
    String doneContent = null;
    String errorContent = null;
    for (String line : sseBody.split("\n")) {
      String trimmed = line.trim();
      if (!trimmed.startsWith("data: ")) continue;
      try {
        JsonNode event = objectMapper.readTree(trimmed.substring(6));
        String type = event.has("type") ? event.get("type").asText() : "";
        String c = event.has("content") ? event.get("content").asText() : "";
        if ("stream".equals(type)) {
          accumulated.append(c);
        } else if ("done".equals(type)) {
          doneContent = c;
        } else if ("error".equals(type)) {
          errorContent = c;
        }
      } catch (Exception ignored) {
        // skip malformed SSE lines
      }
    }
    if (errorContent != null && !errorContent.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, errorContent);
    }
    return doneContent != null ? doneContent : accumulated.toString();
  }

  /**
   * Maps an upstream non-200 HTTP response to a {@link ResponseStatusException}, extracting the
   * server-provided {@code detail} when available. Only 429 is special-cased; everything else maps
   * to {@code SERVICE_UNAVAILABLE}.
   */
  private ResponseStatusException mapUpstreamError(ClassicHttpResponse response) {
    int code = response.getCode();
    String detail = readUpstreamDetail(response);
    HttpStatus status = code == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
    String reason = detail.isBlank() ? "[XTM One] HTTP " + code : detail;
    return new ResponseStatusException(status, reason);
  }

  private String readUpstreamDetail(ClassicHttpResponse response) {
    try {
      if (response.getEntity() == null) return "";
      String body = EntityUtils.toString(response.getEntity());
      JsonNode json = objectMapper.readTree(body);
      return json.hasNonNull("detail") ? json.get("detail").asText() : body;
    } catch (Exception ignored) {
      return "";
    }
  }
}
