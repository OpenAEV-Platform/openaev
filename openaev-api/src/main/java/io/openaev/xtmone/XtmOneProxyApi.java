package io.openaev.xtmone;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtmone.dto.ChatbotAgentOutput;
import io.openaev.xtmone.dto.ChatbotAgentResponse;
import io.openaev.xtmone.dto.ChatbotConfigOutput;
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
 * Proxy endpoints between OpenAEV frontend and XTM One (filigran-copilot).
 *
 * <p>Equivalent to OpenCTI's httpChatbotProxy.ts. The frontend never calls XTM One directly —
 * everything goes through these proxy endpoints. All business logic is delegated to {@link
 * XtmOneProxyService}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class XtmOneProxyApi extends RestBehavior {

  public static final String CHATBOT_URI = "/api/chatbot";

  private final XtmOneProxyService proxyService;

  // ── GET /api/chatbot/config ────────────────────────────────────────────

  /** Returns the XTM One configuration status for the frontend. */
  @GetMapping(CHATBOT_URI + "/config")
  public ResponseEntity<ChatbotConfigOutput> getChatbotConfig() {
    return ResponseEntity.ok(proxyService.getChatbotConfig());
  }

  // ── GET /api/chatbot/health ────────────────────────────────────────────

  /** Returns the health status of the XTM One integration. */
  @GetMapping(CHATBOT_URI + "/health")
  public ResponseEntity<Map<String, String>> getChatbotHealth() {
    Map<String, String> health = proxyService.healthCheck();
    HttpStatus status = "up".equals(health.get("status")) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
    return ResponseEntity.status(status).body(health);
  }

  // ── GET /api/chatbot/agents ────────────────────────────────────────────

  /** Returns the list of enabled agents for the given intent. */
  @GetMapping(CHATBOT_URI + "/agents")
  public ResponseEntity<List<ChatbotAgentOutput>> getChatbotAgents(
      @RequestParam(value = "intent", defaultValue = "global.assistant") String intent) {
    return ResponseEntity.ok(proxyService.getAgentsForIntent(intent));
  }

  // ── POST /api/chatbot/sessions ─────────────────────────────────────────

  /** Proxies a session creation request to XTM One. */
  @PostMapping(CHATBOT_URI + "/sessions")
  public ResponseEntity<String> postChatbotSession(@RequestBody String body) {
    if (!proxyService.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body("{\"error\": \"XTM One is not configured\"}");
    }
    return proxyService.createSession(body);
  }

  // ── POST /api/chatbot/messages (streaming SSE) ─────────────────────────

  /** Streams a chat message response from XTM One as SSE events. */
  @PostMapping(CHATBOT_URI + "/messages")
  public ResponseEntity<StreamingResponseBody> postChatbotMessage(@RequestBody String body) {
    if (!proxyService.isConfigured()) {
      StreamingResponseBody errorBody =
          out ->
              out.write(
                  "{\"error\": \"XTM One is not configured\"}".getBytes(StandardCharsets.UTF_8));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .contentType(MediaType.APPLICATION_JSON)
          .body(errorBody);
    }

    StreamingResponseBody responseBody = outputStream -> proxyService.streamMessage(body, outputStream);
    return new ResponseEntity<>(responseBody, sseHeaders(), HttpStatus.OK);
  }

  // ── POST /api/chatbot/agent/stream (streaming SSE) ─────────────────────

  /** Streams an agent response from XTM One as SSE events. */
  @PostMapping(value = CHATBOT_URI + "/agent/stream", produces = "text/event-stream")
  public ResponseEntity<StreamingResponseBody> postAgentStream(@RequestBody JsonNode body) {
    if (!proxyService.isConfigured()) {
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

    StreamingResponseBody responseBody =
        outputStream -> proxyService.streamAgent(agentSlug, content, outputStream);
    return new ResponseEntity<>(responseBody, sseHeaders(), HttpStatus.OK);
  }

  // ── POST /api/chatbot/agent (non-streaming) ────────────────────────────

  /** Calls an agent in non-streaming mode, returning the response synchronously. */
  @PostMapping(CHATBOT_URI + "/agent")
  public ResponseEntity<ChatbotAgentResponse> postAgentMessage(@RequestBody JsonNode body) {
    if (!proxyService.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(
              ChatbotAgentResponse.builder()
                  .content("")
                  .status("error")
                  .error("XTM One is not configured")
                  .code(503)
                  .build());
    }

    String agentSlug = body.has("agent_slug") ? body.get("agent_slug").asText() : null;
    String content = body.has("content") ? body.get("content").asText() : null;

    if (agentSlug == null || content == null) {
      return ResponseEntity.badRequest()
          .body(
              ChatbotAgentResponse.builder()
                  .content("")
                  .status("error")
                  .error("agent_slug and content are required")
                  .code(400)
                  .build());
    }

    JsonNode files = body.has("files") ? body.get("files") : null;
    return ResponseEntity.ok(proxyService.callAgent(agentSlug, content, files));
  }

  // ── Private helpers ────────────────────────────────────────────────────

  private static HttpHeaders sseHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_EVENT_STREAM);
    headers.setCacheControl("no-cache, no-transform");
    headers.set("Connection", "keep-alive");
    headers.set("X-Accel-Buffering", "no");
    headers.set("Transfer-Encoding", "chunked");
    return headers;
  }
}
