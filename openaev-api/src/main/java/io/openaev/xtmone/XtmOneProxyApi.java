package io.openaev.xtmone;

import static io.openaev.config.SessionHelper.currentUser;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
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
  private final UserRepository userRepository;

  private User resolveCurrentUser() {
    return userRepository
        .findById(currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Current user not found"));
  }

  private String issueJwt(User user) {
    return client.issueAuthenticationJwt(
        user.getId(), user.getName() != null ? user.getName() : user.getEmail(), user.getEmail());
  }

  /** Returns the list of enabled agents for the given intent from the discovered catalog. */
  @GetMapping(CHATBOT_URI + "/agents")
  @SuppressWarnings("unchecked")
  public ResponseEntity<List<Map<String, Object>>> getChatbotAgents(
      @RequestParam(value = "intent", defaultValue = "global.assistant") String intent) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of());
    }
    List<Map<String, Object>> catalog = xtmOneService.getIntentCatalog();
    List<Map<String, Object>> agents =
        catalog.stream()
            .filter(e -> intent.equals(e.get("intent")))
            .flatMap(
                e -> {
                  Object agentsObj = e.get("agents");
                  if (agentsObj instanceof List<?> agentList) {
                    return agentList.stream()
                        .filter(Map.class::isInstance)
                        .map(a -> (Map<String, Object>) a)
                        .filter(a -> Boolean.TRUE.equals(a.get("enabled")));
                  }
                  return java.util.stream.Stream.empty();
                })
            .map(
                a ->
                    Map.<String, Object>of(
                        "id", a.getOrDefault("agent_id", ""),
                        "name", a.getOrDefault("agent_name", ""),
                        "slug", a.getOrDefault("agent_slug", ""),
                        "description", a.getOrDefault("agent_description", "")))
            .toList();
    return ResponseEntity.ok(agents);
  }

  /** Non-streaming agent call. Returns the agent's full response synchronously. */
  @PostMapping(CHATBOT_URI + "/agent")
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

    String result = client.callAgentSync(agentSlug, content, null);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body(Map.of("content", "", "status", "error", "error", "Agent call failed"));
    }
    return ResponseEntity.ok(Map.of("content", result, "status", "success"));
  }

  /** Streaming agent call via SSE. */
  @PostMapping(value = CHATBOT_URI + "/agent/stream", produces = "text/event-stream")
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

    User user = resolveCurrentUser();
    String jwt = issueJwt(user);

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
                jwt,
                content,
                null,
                agentSlug,
                sseStream -> {
                  byte[] buf = new byte[4096];
                  int n;
                  while ((n = sseStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, n);
                    outputStream.flush();
                  }
                });
          } catch (Exception e) {
            log.warn("[XTM One Proxy] Stream error, agent={}.", agentSlug, e);
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
