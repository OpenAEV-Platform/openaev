package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.UserService;
import io.openaev.xtmone.dto.ChatbotAgentDto;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Proxy endpoints for programmatic agent calls from the OpenAEV frontend. These complement the
 * chatbot panel endpoints in {@link io.openaev.rest.xtmone.XtmOneChatApi} by providing intent-based
 * agent resolution and non-streaming/streaming agent call support (used by TextFieldAskAI, TTP
 * extraction, detection/remediation generation).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class XtmOneProxyApi extends RestBehavior {

  private static final String CHATBOT_URI = "/api/chatbot";

  private final XtmOneConfig config;
  private final XtmOneClient client;
  private final XtmOneService xtmOneService;
  private final XtmOneFormattingService formattingService;
  private final UserService userService;

  private String issueJwtForCurrentUser() {
    User user = userService.currentUser();
    return client.issueAuthenticationJwt(
        user.getId(), user.getName() != null ? user.getName() : user.getEmail(), user.getEmail());
  }

  /** Returns the list of enabled agents for the given intent from the discovered catalog. */
  @GetMapping(CHATBOT_URI + "/agents")
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<List<ChatbotAgentDto>> getChatbotAgents(
      @RequestParam(value = "intent", defaultValue = "global.assistant") String intent) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of());
    }
    return ResponseEntity.ok(xtmOneService.listEnabledAgentsForIntent(intent));
  }

  /**
   * Non-streaming agent call. Returns the agent's full response synchronously. The supplied {@code
   * agent_slug} is validated against the discovered intent catalog (any enabled agent across all
   * intents) before the request is forwarded to XTM One.
   */
  @PostMapping(CHATBOT_URI + "/agent")
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> postAgentCall(@RequestBody JsonNode body) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("content", "", "status", "error", "error", "XTM One is not configured"));
    }

    String agentSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : null;

    if (agentSlug == null || content == null) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "content", "",
                  "status", "error",
                  "error", "agent_slug and content are required"));
    }

    String validatedSlug = xtmOneService.requireEnabledAgentSlug(agentSlug);

    String result = client.callAgentSync(issueJwtForCurrentUser(), validatedSlug, content, null);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("content", "", "status", "error", "error", "Agent call failed"));
    }
    return ResponseEntity.ok(Map.of("content", result, "status", "success"));
  }

  /**
   * Non-streaming detection-remediation agent call. Invokes the agent and applies the legacy
   * server-side formatter for the given collector type so the frontend receives editor-ready
   * content. The slug supplied by the client is always validated against the {@code
   * detection.generate} intent catalog before forwarding the request.
   */
  @PostMapping(CHATBOT_URI + "/agent/detection-remediation")
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> postDetectionRemediationCall(
      @RequestBody JsonNode body) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("content", "", "status", "error", "error", "XTM One is not configured"));
    }

    String requestedSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : null;
    String collectorType = body.has("collector_type") ? body.get("collector_type").asText() : null;

    if (content == null || collectorType == null) {
      return ResponseEntity.badRequest()
          .body(
              Map.of(
                  "content",
                  "",
                  "status",
                  "error",
                  "error",
                  "content and collector_type are required"));
    }

    String resolvedSlug =
        xtmOneService.resolveAgentSlugForIntent("detection.generate", requestedSlug);
    if (resolvedSlug == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              Map.of(
                  "content",
                  "",
                  "status",
                  "error",
                  "error",
                  "No detection.generate agent enabled"));
    }

    String raw = client.callAgentSync(issueJwtForCurrentUser(), resolvedSlug, content, null);
    if (raw == null) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("content", "", "status", "error", "error", "Agent call failed"));
    }
    String formatted = formattingService.formatRemediationRules(raw, collectorType);
    return ResponseEntity.ok(Map.of("content", formatted, "status", "success"));
  }

  /**
   * Streaming agent call via SSE. As with {@link #postAgentCall}, the supplied {@code agent_slug}
   * is validated against the discovered intent catalog before the request is forwarded.
   */
  @PostMapping(value = CHATBOT_URI + "/agent/stream", produces = "text/event-stream")
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<StreamingResponseBody> postAgentStream(@RequestBody JsonNode body) {
    if (!config.isConfigured()) {
      StreamingResponseBody errorBody =
          out ->
              out.write(
                  "data: {\"type\":\"error\",\"content\":\"XTM One is not configured\"}\n\n"
                      .getBytes(StandardCharsets.UTF_8));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.TEXT_EVENT_STREAM)
          .body(errorBody);
    }

    String agentSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : null;

    if (agentSlug == null || content == null) {
      StreamingResponseBody errorBody =
          out ->
              out.write(
                  "data: {\"type\":\"error\",\"content\":\"agent_slug and content are required\"}\n\n"
                      .getBytes(StandardCharsets.UTF_8));
      return ResponseEntity.badRequest().contentType(MediaType.TEXT_EVENT_STREAM).body(errorBody);
    }

    final String validatedSlug = xtmOneService.requireEnabledAgentSlug(agentSlug);

    // Resolve the user and mint the JWT inside the request thread (Spring Security context is not
    // propagated automatically into the streaming callback below).
    final String jwt = issueJwtForCurrentUser();

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
                jwt,
                content,
                null,
                validatedSlug,
                sseStream -> {
                  byte[] buf = new byte[4096];
                  int n;
                  while ((n = sseStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, n);
                    outputStream.flush();
                  }
                });
          } catch (Exception e) {
            log.warn("[XTM One Proxy] Stream error, agent={}.", validatedSlug, e);
            outputStream.write(
                ("data: {\"type\":\"error\",\"content\":\"Unable to connect to the AI assistant.\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
          }
        };

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_EVENT_STREAM);
    headers.setCacheControl("no-cache, no-transform");
    headers.set("X-Accel-Buffering", "no");
    return new ResponseEntity<>(responseBody, headers, HttpStatus.OK);
  }
}
