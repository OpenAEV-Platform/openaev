package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.User;
import io.openaev.database.model.autonomous.AutonomousScopeTarget;
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
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
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
  private static final String CHAT_SESSIONS_PATH = "/api/v1/platform/chat/sessions";
  private static final String CHAT_WORKSPACES_PATH = "/api/v1/platform/chat/workspaces";
  private static final String CHAT_CONVERSATION_REFERENCES_PATH =
      "/api/v1/platform/chat/conversation-references";
  private static final int AGENT_LIST_TIMEOUT_SECONDS = 10;

  /**
   * Clears XTM One's 30-minute abandonment bound, so a turn paused for tool approval is not cut off
   * by a deadline nobody chose. Finite rather than {@link Timeout#DISABLED} on purpose: a paused
   * turn produces no bytes, so this is the only thing that frees the reader thread if the
   * connection dies without a FIN. Paired with {@code MvcConfig#ASYNC_REQUEST_TIMEOUT_MS}, which
   * must stay above it.
   */
  private static final Timeout CHAT_STREAM_RESPONSE_TIMEOUT = Timeout.ofMinutes(40);

  /**
   * Intent binding the specialist agents the autonomous attack-path orchestrator may consult (see
   * XTM One {@code aev.attack_path_additional_agent}). Curated list the operator picks from.
   */
  private static final String ADDITIONAL_ATTACK_AGENT_INTENT = "aev.attack_path_additional_agent";

  private final XtmOneConfig config;
  private final ObjectMapper objectMapper;
  private final XtmAuthKeyService keyService;
  private final OpenAEVConfig openAEVConfig;
  private final HttpClientFactory httpClientFactory;
  private final UserService userService;
  private final XtmOneIdentity xtmOneIdentity;

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
        .add(xtmOneIdentity.audience())
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

  // URLEncoder targets query strings ('+' for spaces) - normalize to %20 for a path segment
  // (same approach as DocumentService.encodeFileName).
  private static String encodePathSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /**
   * Appends {@code name=value} to the query string of {@code url}, the value URL-encoded with a
   * space written {@code %20}, which reads as a space whether or not the server decodes the query
   * as a form. A {@code null} value adds nothing.
   */
  private static void appendQueryParameter(StringBuilder url, String name, Object value) {
    if (value == null) {
      return;
    }
    url.append(url.indexOf("?") < 0 ? '?' : '&')
        .append(name)
        .append('=')
        .append(encodePathSegment(value.toString()));
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
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      Map<String, Object> body = new HashMap<>();
      body.put("platform_identifier", platformIdentifier);
      body.put("platform_url", platformUrl);
      body.put("platform_title", platformTitle);
      body.put("platform_version", platformVersion);
      body.put("platform_id", platformId != null ? platformId : "");
      body.put("enterprise_license_pem", enterpriseLicensePem != null ? enterpriseLicensePem : "");
      body.put("license_type", licenseType != null ? licenseType : "");
      if (businessVertical != null) body.put("business_vertical", businessVertical);
      // Platform-level twin of the per-message supports_tool_approval: this one says the
      // integration can be gated at all, that one says a given client will answer a prompt.
      body.put("supports_approval_prompts", true);
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
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      String encodedIntentName =
          intentName != null ? URLEncoder.encode(intentName, StandardCharsets.UTF_8) : "";
      HttpGet httpGet =
          chatGetBuilder(
              INTENTS_CATALOG_AGENTS_PATH + "?vertical=aev&intent=" + encodedIntentName, jwt);
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

  /**
   * Lists the specialist agents the autonomous attack-path orchestrator may consult (the {@code
   * aev.attack_path_additional_agent} intent catalog). Unlike {@link #listChatAgents(String)}, this
   * treats "XTM One not configured" and "no agents bound" as an empty list rather than an error, so
   * the operator UI degrades gracefully to a CTA-only state.
   */
  public List<ChatbotAgentOutput> listAdditionalAttackAgents() {
    if (!config.isConfigured()) {
      return List.of();
    }
    try {
      return listChatAgents(ADDITIONAL_ATTACK_AGENT_INTENT);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return List.of();
      }
      throw e;
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
                    .filter(java.util.Objects::nonNull)
                    .filter(
                        a ->
                            a.id() != null
                                && !a.id().isBlank()
                                && a.slug() != null
                                && !a.slug().isBlank())
                    .toList();

        if (agents.isEmpty()) {
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "[XTM One] No chat agents available");
        }
        yield agents;
      }
      // XTM One rejecting our JWT is a config issue, not the caller's: a 401 would log them out
      case 401 ->
          throw new ResponseStatusException(
              HttpStatus.UNPROCESSABLE_ENTITY, "[XTM One] Unauthorized access to chat agents");
      case 403 ->
          throw new ResponseStatusException(
              HttpStatus.UNPROCESSABLE_ENTITY, "[XTM One] Forbidden access to chat agents");
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

  /**
   * Creates the current user's platform-chat conversation, or restores the one {@code
   * conversation_id} names. The chat panel's request body is forwarded as it came ({@code
   * agent_slug}, {@code conversation_id}, {@code workspace_id} to file a new conversation into a
   * workspace), so a field a newer panel sends is not dropped on the way. Returns the upstream
   * payload, or null on failure.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> createChatSession(Map<String, Object> body) {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      String json = objectMapper.writeValueAsString(body != null ? body : Map.of());

      HttpPost httpPost = chatPostBuilder(CHAT_SESSIONS_PATH, jwt, json);
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

  /**
   * Lists the current user's platform-chat conversations (chatbot history menu). Returns the raw
   * upstream payload ({@code {"conversations": [...]}}) or null on failure — the chatbot history
   * menu degrades to an empty state.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> listChatSessions() {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpGet httpGet = chatGetBuilder(CHAT_SESSIONS_PATH, jwt);
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            log.warn("[XTM One] List sessions failed: HTTP {}", response.getCode());
            return null;
          });
    } catch (Exception e) {
      log.warn("[XTM One] List sessions error: ", e);
    }
    return null;
  }

  /**
   * Removes a conversation from the chatbot history (archived upstream). Returns true when the
   * upstream accepted the deletion.
   */
  public boolean deleteChatSession(String conversationId) {
    if (!config.isConfigured()) {
      return false;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpDelete httpDelete =
          new HttpDelete(
              config.getUrl() + CHAT_SESSIONS_PATH + "/" + encodePathSegment(conversationId));
      addChatHeaders(httpDelete, jwt);
      httpDelete.setConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpDelete,
          response -> {
            if (response.getCode() == 204 || response.getCode() == 200) {
              return true;
            }
            log.warn("[XTM One] Delete session failed: HTTP {}", response.getCode());
            return false;
          });
    } catch (Exception e) {
      log.warn("[XTM One] Delete session error: ", e);
    }
    return false;
  }

  /**
   * An XTM One answer relayed to the chat panel as it came: its status, and its JSON body, which
   * carries XTM One's {@code detail} when it refuses a change. The body is {@code null} when there
   * is none (a 204).
   */
  public record RelayedResponse(int status, JsonNode body) {}

  /**
   * Renames one of the current user's platform-chat conversations, or files it into a workspace
   * ({@code workspace_id}; an explicit {@code null} takes it out of its workspace). XTM One answers
   * 404 for a conversation that is not one of the user's platform-chat conversations.
   *
   * @param changes {@code title} and / or {@code workspace_id}, forwarded as given
   */
  public RelayedResponse updateChatSession(String conversationId, Map<String, Object> changes) {
    return relayChatRequest(
        HttpPatch::new,
        CHAT_SESSIONS_PATH + "/" + encodePathSegment(conversationId),
        changes,
        "updating the conversation");
  }

  /**
   * Lists the current user's XTM One workspaces, which the chat panel groups conversations by
   * ({@code {"workspaces": [...]}}). XTM One answers 403 while it is not licensed.
   */
  public RelayedResponse listChatWorkspaces() {
    return relayChatRequest(HttpGet::new, CHAT_WORKSPACES_PATH, null, "listing the workspaces");
  }

  /**
   * Creates a personal workspace for the current user.
   *
   * @param fields {@code name} and, optionally, {@code description}
   */
  public RelayedResponse createChatWorkspace(Map<String, Object> fields) {
    return relayChatRequest(HttpPost::new, CHAT_WORKSPACES_PATH, fields, "creating a workspace");
  }

  /**
   * Renames a workspace, or changes its description, for a user who may manage it.
   *
   * @param fields {@code name} and / or {@code description}
   */
  public RelayedResponse updateChatWorkspace(String workspaceId, Map<String, Object> fields) {
    return relayChatRequest(
        HttpPatch::new,
        CHAT_WORKSPACES_PATH + "/" + encodePathSegment(workspaceId),
        fields,
        "updating the workspace");
  }

  /**
   * Deletes a workspace; XTM One moves its conversations to their owner's default workspace, and
   * refuses the default workspace itself or one that still holds work items.
   */
  public RelayedResponse deleteChatWorkspace(String workspaceId) {
    return relayChatRequest(
        HttpDelete::new,
        CHAT_WORKSPACES_PATH + "/" + encodePathSegment(workspaceId),
        null,
        "deleting the workspace");
  }

  /**
   * Searches the conversations the current user may reference with {@code @} from the chat panel
   * ({@code {"conversations": [{"id", "title", "key", "updated_at", "is_own"}]}}): XTM One offers
   * only the conversations that user can open. Each parameter is sent only when given.
   *
   * @param query the text typed after {@code @}, {@code null} for none
   * @param limit how many conversations to offer, {@code null} for XTM One's default
   * @param excludedConversationId a conversation not to offer (the one the panel is in), {@code
   *     null} for none
   */
  public RelayedResponse searchChatConversationReferences(
      String query, Integer limit, String excludedConversationId) {
    StringBuilder path = new StringBuilder(CHAT_CONVERSATION_REFERENCES_PATH);
    appendQueryParameter(path, "q", query);
    appendQueryParameter(path, "limit", limit);
    appendQueryParameter(path, "exclude", excludedConversationId);
    return relayChatRequest(
        HttpGet::new, path.toString(), null, "searching the conversation references");
  }

  /**
   * Sends one chat-panel request to XTM One as the current user and returns its answer, whatever
   * its status (see {@link #toRelayedResponse}). Only an unconfigured XTM One ({@code 503}) or one
   * that cannot be reached ({@code 500}) is an exception.
   *
   * @param method the request type ({@code HttpGet::new}, ...), given the full URL
   * @param body the JSON body, {@code null} for none
   * @param action what the request does, for the log and the error message
   */
  private RelayedResponse relayChatRequest(
      Function<String, HttpUriRequestBase> method, String path, Object body, String action) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpUriRequestBase request = method.apply(config.getUrl() + path);
      addChatHeaders(request, jwt);
      if (body != null) {
        request.setEntity(
            new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
      }
      request.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());
      return httpClient.execute(request, this::toRelayedResponse);
    } catch (Exception e) {
      log.warn("[XTM One] Error while {}: ", action, e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "[XTM One] Unexpected error while " + action, e);
    }
  }

  /**
   * A success keeps its status and its JSON body. A refusal keeps its status (a code that is not an
   * error becomes {@code 502}, a {@code 401} becomes {@code 422}) with a body holding only XTM
   * One's {@code detail}, or the upstream status when XTM One sent none, so nothing else of an
   * upstream error page reaches the browser.
   */
  private RelayedResponse toRelayedResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    JsonNode body = readJsonBody(response);
    if (code == HttpStatus.NO_CONTENT.value()) {
      return new RelayedResponse(code, null);
    }
    if (code >= 200 && code < 300) {
      if (body == null) {
        String unreadable = "[XTM One] Unreadable response (HTTP " + code + ")";
        return new RelayedResponse(HttpStatus.BAD_GATEWAY.value(), detailBody(unreadable));
      }
      return new RelayedResponse(code, body);
    }
    HttpStatus status = HttpStatus.resolve(code);
    int relayedStatus = status != null && status.isError() ? code : HttpStatus.BAD_GATEWAY.value();
    if (code == HttpStatus.UNAUTHORIZED.value()) {
      // XTM One rejecting our JWT is a config issue, not the caller's: a 401 would log them out
      relayedStatus = HttpStatus.UNPROCESSABLE_ENTITY.value();
    }
    JsonNode detail = body != null ? body.get("detail") : null;
    if (detail == null || detail.isNull()) {
      return new RelayedResponse(relayedStatus, detailBody("[XTM One] HTTP " + code));
    }
    ObjectNode error = JsonNodeFactory.instance.objectNode();
    error.set("detail", detail);
    return new RelayedResponse(relayedStatus, error);
  }

  private static ObjectNode detailBody(String detail) {
    return JsonNodeFactory.instance.objectNode().put("detail", detail);
  }

  /** The response's JSON body, or {@code null} when it has none or it is not JSON. */
  private JsonNode readJsonBody(ClassicHttpResponse response) {
    try {
      if (response.getEntity() == null) {
        return null;
      }
      String text = EntityUtils.toString(response.getEntity());
      return text == null || text.isBlank() ? null : objectMapper.readTree(text);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Injects a mid-run steering message into the conversation's running agent loop. Upstream status
   * codes are propagated as {@link ResponseStatusException} — the chatbot rolls back its optimistic
   * bubble on any non-2xx (e.g. 409 when no response is currently being generated).
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> steerChatMessage(String content, String conversationId) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      body.put("conversation_id", conversationId);
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/chat/messages/steer", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            // Preserve the upstream status code (the chatbot distinguishes 409 "no run
            // active" from other failures) — mapUpstreamError would collapse it to 503.
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      // httpClient.execute wraps the handler's RuntimeException — unwrap a
      // propagated upstream error before falling back to a generic 500.
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Steer message error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "[XTM One] Unexpected error while steering", e);
    }
  }

  /**
   * @param decisions one entry per proposed call — {@code {tool_call_id, decision,
   *     rejection_reason}}. Every proposal must be decided: resuming with an undecided call leaves
   *     a {@code tool_use} block without its {@code tool_result}, which the model providers reject.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> approveToolCalls(
      String conversationId, List<Map<String, Object>> decisions) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("conversation_id", conversationId);
      body.put("decisions", decisions);
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/chat/messages/approve", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Approve tool calls error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "[XTM One] Unexpected error while approving", e);
    }
  }

  /**
   * Calling this also refreshes upstream's client-seen marker, restarting the abandonment clock.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getPendingApprovals(String conversationId) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      String encodedConversationId = URLEncoder.encode(conversationId, StandardCharsets.UTF_8);
      HttpGet httpGet =
          chatGetBuilder(
              "/api/v1/platform/chat/conversations/" + encodedConversationId + "/pending-approvals",
              jwt);
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Pending approvals error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "[XTM One] Unexpected error while reading pending approvals",
          e);
    }
  }

  /**
   * Returns the prompts the current user's XTM One web chat picker offers, as the raw upstream
   * payload ({@code {"prompts": [...]}}). Upstream status codes are propagated as {@link
   * ResponseStatusException}.
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getChatPrompts() {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpGet httpGet = chatGetBuilder("/api/v1/platform/chat/prompts", jwt);
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Chat prompts error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "[XTM One] Unexpected error while reading prompts", e);
    }
  }

  /**
   * Returns the current user's agentic quota as the XTM One web chat shows it ({@code {"used",
   * "limit", "period", "scope"}}), or a JSON {@code null} node when there is nothing to show
   * (Community Edition, no enforceable limit). Upstream status codes are propagated as {@link
   * ResponseStatusException}.
   */
  public JsonNode getChatQuota() {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpGet httpGet = chatGetBuilder("/api/v1/platform/chat/quota", jwt);
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() == 200) {
              JsonNode quota = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
              return quota != null && quota.isObject() ? quota : NullNode.getInstance();
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Chat quota error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "[XTM One] Unexpected error while reading quota", e);
    }
  }

  /**
   * Rates an assistant message of one of the current user's conversations and returns the stored
   * rating ({@code {"rating", "comment"}}). Upstream status codes are propagated as {@link
   * ResponseStatusException}: XTM One answers 404 for a message the user cannot read.
   *
   * @param rating {@code positive} or {@code negative}
   * @param comment optional free text, {@code null} for none
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> submitMessageFeedback(
      String conversationId, String messageId, String rating, String comment) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("rating", rating);
      body.put("comment", comment);
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost =
          chatPostBuilder(messageFeedbackPath(conversationId, messageId), jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Message feedback error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "[XTM One] Unexpected error while rating the message",
          e);
    }
  }

  /**
   * Removes the current user's rating of an assistant message. Idempotent upstream (a message never
   * rated is answered 204 too). Upstream status codes are propagated as {@link
   * ResponseStatusException}.
   */
  public void retractMessageFeedback(String conversationId, String messageId) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpDelete httpDelete =
          new HttpDelete(config.getUrl() + messageFeedbackPath(conversationId, messageId));
      addChatHeaders(httpDelete, jwt);
      httpDelete.setConfig(
          RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build());

      httpClient.execute(
          httpDelete,
          response -> {
            if (response.getCode() == 204 || response.getCode() == 200) {
              return null;
            }
            throw mapUpstreamErrorPreservingStatus(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (e.getCause() instanceof ResponseStatusException rse) {
        throw rse;
      }
      log.warn("[XTM One] Message feedback retraction error: ", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "[XTM One] Unexpected error while removing the message rating",
          e);
    }
  }

  private static String messageFeedbackPath(String conversationId, String messageId) {
    return "/api/v1/platform/chat/conversations/"
        + encodePathSegment(conversationId)
        + "/messages/"
        + encodePathSegment(messageId)
        + "/feedback";
  }

  /**
   * Kicks off a durable, autonomous attack-path orchestration run in XTM One. The orchestrator (the
   * "brain") then drives OpenAEV back through the platform MCP tools and the run callback
   * endpoints, so this call is a short, fire-and-forget enqueue rather than a long stream.
   *
   * <p>Authenticated with a per-user JWT so every action XTM One takes is attributed to the real
   * operator. Returns the upstream handle ({@code {"session_id": ..., "agent_slug": ...}}) so the
   * caller can persist it for reconnection, or {@code null} when XTM One is not configured / the
   * enqueue failed.
   *
   * @param agentSlug the orchestrator agent slug (from the {@code aev.attack_path_orchestrator}
   *     intent catalog)
   * @param objective the resolved objective prompt
   * @param openaevRunId the OpenAEV autonomous run id to call back
   * @param simulationId the chained simulation id the run drives
   * @param scopeAssetGroupId optional in-scope asset group id (first-of-kind projection)
   * @param scopeTeamId optional in-scope team (audience) id (first-of-kind projection)
   * @param scope the authoritative mixed scope (assets, asset groups, teams, persons)
   * @param callbackBaseUrl the OpenAEV base URL XTM One should call back
   * @param agentIds specialist agent ids the orchestrator may consult during the run (sent as
   *     {@code handover_agent_ids})
   * @param agentModes per-agent discovery mode (agent id -> EXISTING_ONLY / SCOPED / EXPANSIVE),
   *     sent as {@code handover_agent_modes} so XTM One can funnel each agent's create tools
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> startAutonomousRun(
      String agentSlug,
      String objective,
      String openaevRunId,
      String simulationId,
      String scenarioId,
      boolean authorScenario,
      String scopeAssetGroupId,
      String scopeTeamId,
      List<AutonomousScopeTarget> scope,
      String scopeMode,
      boolean planMode,
      String priorPlan,
      String callbackBaseUrl,
      List<String> agentIds,
      Map<String, String> agentModes) {
    if (!config.isConfigured()) {
      return null;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      body.put("objective", objective);
      body.put("openaev_run_id", openaevRunId);
      // Author-scenario (AI planning) mode: no simulation exists; the orchestrator authors the
      // attack path onto the scenario workflow instead. XTM One targets the scenario for its
      // attack-path tools when author_scenario is set, otherwise it targets the simulation.
      // Omit optional targets when null (consistent with scenario_id / scope_*) so XTM One can
      // distinguish "no simulation" (author-scenario mode) from an explicit null.
      if (simulationId != null) body.put("simulation_id", simulationId);
      if (scenarioId != null) body.put("scenario_id", scenarioId);
      body.put("author_scenario", authorScenario);
      if (scopeAssetGroupId != null) body.put("scope_asset_group_id", scopeAssetGroupId);
      if (scopeTeamId != null) body.put("scope_team_id", scopeTeamId);
      if (scope != null && !scope.isEmpty()) body.put("scope", scope);
      if (scopeMode != null) body.put("scope_mode", scopeMode);
      body.put("plan_mode", planMode);
      if (priorPlan != null && !priorPlan.isBlank()) body.put("prior_plan", priorPlan);
      body.put("callback_base_url", callbackBaseUrl);
      // Specialist agents the orchestrator may CONSULT during the run (see the
      // aev.attack_path_additional_agent intent). Sent whenever OpenAEV has resolved a selection
      // (a non-null list, even empty): XTM One then treats it as the authoritative consult set,
      // which is what lets an operator disable even the built-in payload creator for a run.
      if (agentIds != null) body.put("handover_agent_ids", agentIds);
      // Per-agent discovery mode. XTM One uses it to decide which OpenAEV create tools each agent
      // (the orchestrator and each consulted specialist) is given during the run - the funnel that
      // keeps recon from silently expanding the perimeter. Sent whenever OpenAEV resolved a map.
      if (agentModes != null && !agentModes.isEmpty()) {
        body.put("handover_agent_modes", agentModes);
      }
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/autonomous/runs", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build());

      return httpClient.execute(
          httpPost,
          response -> {
            if (response.getCode() == 200 || response.getCode() == 201) {
              return objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
            }
            throw mapUpstreamError(response);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[XTM One] Start autonomous run error, agent={}.", agentSlug, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Failed to start autonomous run", e);
    }
  }

  /**
   * Best-effort wake for a parked autonomous run. When the operator queues a steering directive (or
   * answers a waiting-input question), the orchestrator may be parked between decision cycles -
   * this re-arms it so it resumes immediately instead of only at its scheduled re-check.
   * Fire-and-forget: a failure never breaks queuing the directive, because the deadline sweep is
   * the backstop.
   */
  public void wakeAutonomousRun(String openaevRunId, String reason) {
    if (!config.isConfigured() || openaevRunId == null) {
      return;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      if (reason != null) body.put("reason", reason);
      String json = objectMapper.writeValueAsString(body);
      String encodedRunId = URLEncoder.encode(openaevRunId, StandardCharsets.UTF_8);
      HttpPost httpPost =
          chatPostBuilder("/api/v1/platform/autonomous/runs/" + encodedRunId + "/wake", jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build());
      httpClient.execute(
          httpPost,
          response -> {
            if (response.getEntity() != null) {
              EntityUtils.consume(response.getEntity());
            }
            return null;
          });
    } catch (Exception e) {
      // Non-fatal: the scheduled resume / deadline sweep re-checks the run anyway.
      log.warn("[XTM One] Wake autonomous run error, run={}.", openaevRunId, e);
    }
  }

  /**
   * Terminally stops the XTM One orchestration behind an autonomous run. Called when the operator
   * stops, pauses, restarts, or deletes a run so the orchestrator loop halts on the XTM One side
   * too - otherwise the durable execution keeps self-resuming every few seconds and, on a deleted
   * run, keeps dispatching injects against a vanished simulation.
   *
   * <p>Authenticated with the acting operator's per-user JWT (same as start / wake), so the stop is
   * attributed to the real user. Fire-and-forget and idempotent: a missing / already-terminal run
   * is a no-op upstream, and a transport failure must never block the OpenAEV-side stop/delete (the
   * adapter also self-terminates a run whose OpenAEV row it can no longer reach).
   */
  public void cancelAutonomousRun(String openaevRunId, String reason, boolean purge) {
    if (!config.isConfigured() || openaevRunId == null) {
      return;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      if (reason != null) body.put("reason", reason);
      String json = objectMapper.writeValueAsString(body);
      String encodedRunId = URLEncoder.encode(openaevRunId, StandardCharsets.UTF_8);
      // purge=true also drops the run's XTM One coordination state (shared state + work items) so a
      // later restart starts clean; stop / restart / delete set it, pause does not (resume keeps
      // it).
      String path =
          "/api/v1/platform/autonomous/runs/"
              + encodedRunId
              + "/cancel"
              + (purge ? "?purge=true" : "");
      HttpPost httpPost = chatPostBuilder(path, jwt, json);
      httpPost.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(20)).build());
      httpClient.execute(
          httpPost,
          response -> {
            if (response.getEntity() != null) {
              EntityUtils.consume(response.getEntity());
            }
            return null;
          });
    } catch (Exception e) {
      // Non-fatal: the OpenAEV-side stop/delete still proceeds; the adapter's own
      // self-guard is the backstop for a lost cancel signal.
      log.warn("[XTM One] Cancel autonomous run error, run={}.", openaevRunId, e);
    }
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
                  + encodedConversationId
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
   * An agent-generated file fetched from XTM One, buffered in memory together with the upstream
   * content headers so the API layer can relay it to the browser.
   */
  public record DownloadedFile(byte[] content, String contentType, String contentDisposition) {}

  /**
   * Downloads an agent-generated file from XTM One on behalf of the current user.
   *
   * <p>The XTM One JWT is minted server-side from the current OpenAEV user, so the browser only
   * ever authenticates against OpenAEV — it never logs in to XTM One. The file is buffered in
   * memory (chat-generated files are small and capped upstream) and returned with the upstream
   * {@code Content-Type} / {@code Content-Disposition} headers for the API layer to relay.
   *
   * @param fileId the XTM One file attachment id (validated as a UUID by the caller)
   * @return the downloaded file bytes + content headers
   */
  public DownloadedFile downloadChatFile(String fileId) {
    if (!config.isConfigured()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] Service is not configured");
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      HttpGet httpGet = chatGetBuilder("/api/v1/chat/files/" + fileId + "/download", jwt);
      httpGet.setConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMinutes(2)).build());

      return httpClient.execute(
          httpGet,
          response -> {
            if (response.getCode() != 200) {
              throw mapUpstreamError(response);
            }
            String contentType =
                response.getEntity() != null ? response.getEntity().getContentType() : null;
            var cdHeader = response.getFirstHeader("Content-Disposition");
            String contentDisposition = cdHeader != null ? cdHeader.getValue() : null;
            byte[] content = EntityUtils.toByteArray(response.getEntity());
            return new DownloadedFile(content, contentType, contentDisposition);
          });
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      log.warn("[XTM One] Download chat file error, fileId={}.", fileId, e);
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "[XTM One] File download failed", e);
    }
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
    streamChatMessage(content, conversationId, agentSlug, null, false, streamConsumer);
  }

  /**
   * @deprecated use the overload carrying {@code supportsToolApproval}.
   */
  @Deprecated
  public void streamChatMessage(
      String content,
      String conversationId,
      String agentSlug,
      Map<String, Object> context,
      StreamConsumer streamConsumer) {
    streamChatMessage(content, conversationId, agentSlug, context, false, streamConsumer);
  }

  /**
   * Streams a chat message response from XTM One, forwarding an optional arbitrary page/application
   * {@code context} object so the agent is aware of where the user is (e.g. the current URL). The
   * context shape is decided by the caller (today the embedded chatbot sends {@code {"url": ...}});
   * it is omitted from the upstream body when {@code null} or empty.
   *
   * @param content message content
   * @param conversationId optional conversation ID
   * @param agentSlug optional agent slug
   * @param context optional host page/application context (forwarded verbatim)
   * @param supportsToolApproval whether the caller can answer an approval prompt. Declaring it is a
   *     promise to answer: a turn paused for a client that never answers waits indefinitely.
   * @param streamConsumer callback that receives the SSE {@link InputStream}
   */
  public void streamChatMessage(
      String content,
      String conversationId,
      String agentSlug,
      Map<String, Object> context,
      boolean supportsToolApproval,
      StreamConsumer streamConsumer) {
    streamChatMessage(
        content, conversationId, agentSlug, context, supportsToolApproval, null, streamConsumer);
  }

  /**
   * Streams a chat message response from XTM One, as {@link #streamChatMessage(String, String,
   * String, Map, boolean, StreamConsumer)} does, for a message that references other conversations
   * with {@code @}.
   *
   * @param referencedConversationIds the conversations the message references, forwarded as given
   *     ({@code referenced_conversation_ids}); omitted from the upstream body when {@code null} or
   *     empty. XTM One reads only those the user can open.
   */
  public void streamChatMessage(
      String content,
      String conversationId,
      String agentSlug,
      Map<String, Object> context,
      boolean supportsToolApproval,
      List<String> referencedConversationIds,
      StreamConsumer streamConsumer) {
    if (!config.isConfigured()) {
      log.warn("[XTM One] Chat message skipped: not configured");
      return;
    }
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
      String jwt = issueJwtForCurrentUser();
      Map<String, Object> body = new HashMap<>();
      body.put("content", content);
      if (conversationId != null) body.put("conversation_id", conversationId);
      if (agentSlug != null) body.put("agent_slug", agentSlug);
      if (context != null && !context.isEmpty()) body.put("context", context);
      if (supportsToolApproval) body.put("supports_tool_approval", true);
      if (referencedConversationIds != null && !referencedConversationIds.isEmpty()) {
        body.put("referenced_conversation_ids", referencedConversationIds);
      }
      String json = objectMapper.writeValueAsString(body);

      HttpPost httpPost = chatPostBuilder("/api/v1/platform/chat/messages", jwt, json);
      httpPost.setConfig(
          RequestConfig.custom().setResponseTimeout(CHAT_STREAM_RESPONSE_TIMEOUT).build());

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
    try (CloseableHttpClient httpClient = httpClientFactory.httpClientNoRetry()) {
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

  /**
   * Maps an upstream non-200 HTTP response to a {@link ResponseStatusException} carrying the
   * upstream status code as-is (unknown codes fall back to {@code BAD_GATEWAY}). Used where the
   * caller semantically relies on the exact code — e.g. mid-run steering, where 409 means "no
   * response is currently being generated" and triggers the chatbot's optimistic-bubble rollback.
   */
  private ResponseStatusException mapUpstreamErrorPreservingStatus(ClassicHttpResponse response) {
    int code = response.getCode();
    String detail = readUpstreamDetail(response);
    HttpStatus status = HttpStatus.resolve(code);
    if (status == null) {
      status = HttpStatus.BAD_GATEWAY;
    }
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
