package io.openaev.api.xtmone;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.xtmone.dto.AgentCallInput;
import io.openaev.api.xtmone.dto.AgentCallOutput;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Proxy endpoints for programmatic XTM One agent calls from the OpenAEV frontend. These complement
 * the chatbot panel endpoints in {@link XtmOneChatApi} by providing intent-based agent resolution,
 * plus non-streaming and streaming agent calls (used by TextFieldAskAI and generic chatbot
 * interactions).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(XtmOneProxyApi.CHATBOT_URI)
@Tag(name = "XTM One Chatbot API", description = "Proxy endpoints for XTM One agent calls")
public class XtmOneProxyApi extends RestBehavior {

  public static final String CHATBOT_URI = "/api/chatbot";

  private final XtmOneConfig config;
  private final XtmOneClient client;
  private final AiMetricCollector aiMetricCollector;

  // -- READ --

  @GetMapping("/agents")
  @LogExecutionTime
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  @Operation(
      summary = "List XTM One agents for an intent",
      description =
          "Returns the agents enabled for the given intent in the discovered XTM One catalog.")
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<List<ChatbotAgentOutput>> getChatbotAgents(
      TxCtx ctx, @RequestParam(value = "intent", defaultValue = "global.assistant") String intent) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(List.of());
    }
    return ResponseEntity.ok(client.listChatAgents(intent));
  }

  // -- AGENT CALLS --

  @PostMapping("/agent")
  @Transactional(propagation = Propagation.NEVER)
  @LogExecutionTime
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  @Operation(
      summary = "Synchronous XTM One agent call",
      description =
          "Routes the user prompt to the requested agent and returns its full response. The client"
              + " supplied agent_slug is validated against the discovered intent catalog (any"
              + " enabled agent across all intents) before the request is forwarded.")
  public ResponseEntity<AgentCallOutput> postAgentCall(
      TxCtx ctx, @Valid @RequestBody AgentCallInput input) {
    if (!config.isConfigured()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(AgentCallOutput.error("XTM One is not configured"));
    }
    // Telemetry: feature-intent calls land in the backend-agnostic per-feature
    // counter; other agent calls are counted by agent slug.
    aiMetricCollector.recordAgentProxyCall(input.agentSlug(), input.intent());
    String result = client.callAgentSync(input.agentSlug(), input.content(), null);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(AgentCallOutput.error("Agent call failed"));
    }
    return ResponseEntity.ok(AgentCallOutput.success(result));
  }

  @PostMapping(value = "/agent/stream", produces = "text/event-stream")
  @Transactional(propagation = Propagation.NEVER)
  @LogExecutionTime
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  @Operation(
      summary = "Streaming XTM One agent call (SSE)",
      description =
          "Server-Sent Events stream for the requested agent. As with the synchronous variant, the"
              + " supplied agent_slug is validated against the discovered intent catalog before"
              + " the request is forwarded.")
  public ResponseEntity<StreamingResponseBody> postAgentStream(
      TxCtx ctx, @Valid @RequestBody AgentCallInput input) {
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

    // Telemetry: same feature-intent mapping as the synchronous variant.
    aiMetricCollector.recordAgentProxyCall(input.agentSlug(), input.intent());

    final String validatedSlug = input.agentSlug();
    final String content = input.content();

    // Resolve the user and mint the JWT inside the request thread (Spring Security context is not
    // propagated automatically into the streaming callback below).
    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
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
          } catch (ResponseStatusException e) {
            String detail =
                e.getReason() != null && !e.getReason().isBlank()
                    ? e.getReason()
                    : "Unable to connect to the AI assistant.";
            String errorContent =
                e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()
                    ? "⚠️ **Quota exceeded** — " + detail
                    : "⚠️ **Error** — " + detail;
            outputStream.write(
                ("data: {\"type\":\"error\",\"content\":\"" + errorContent + "\"}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
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
