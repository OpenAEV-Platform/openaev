package io.openaev.api.xtmone;

import io.openaev.aop.AccessControl;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Slf4j
@RestController
@RequiredArgsConstructor
public class XtmOneChatApi extends RestBehavior {

  private static final String XTM_ONE_URI = "/api/xtmone";
  private static final Pattern FILE_ID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final Pattern CONVERSATION_ID_PATTERN = FILE_ID_PATTERN;

  private static final String REJECT_VERDICT = "reject";
  private static final Set<String> ALLOWED_VERDICTS =
      Set.of("approve", "approve_always", REJECT_VERDICT);
  private static final int MAX_DECISIONS_PER_REQUEST = 50;
  private static final int MAX_REJECTION_REASON_LENGTH = 2000;

  private final XtmOneClient client;
  private final XtmOneConfig config;
  private final AiMetricCollector aiMetricCollector;

  @GetMapping(XTM_ONE_URI + "/chat/agents")
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<List<ChatbotAgentOutput>> listAgents(TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(List.of());
    }
    return ResponseEntity.ok(client.listChatAgents("global.assistant"));
  }

  @PostMapping(XTM_ONE_URI + "/chat/sessions")
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<Map<String, Object>> createSession(
      @RequestBody Map<String, Object> body, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    Map<String, Object> result = client.createChatSession(agentSlug, conversationId);
    if (result == null) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.ok(result);
  }

  /** Lists past conversations for the chatbot history menu. */
  @GetMapping(XTM_ONE_URI + "/chat/sessions")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: chat data lives in XTM One and is scoped there to the per-user JWT minted by
  // XtmOneClient — there is no OpenAEV resource to check grants against. The EE gate matches the
  // Ariane feature gating (see AskArianeButton) and XtmOneProxyApi.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> listSessions(TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(Map.of("conversations", List.of()));
    }
    Map<String, Object> result = client.listChatSessions();
    if (result == null) {
      // Degrade to an empty history instead of breaking the chat panel.
      return ResponseEntity.ok(Map.of("conversations", List.of()));
    }
    return ResponseEntity.ok(result);
  }

  /** Removes a conversation from the chatbot history menu (archived upstream). */
  @DeleteMapping(XTM_ONE_URI + "/chat/sessions/{conversationId}")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Void> deleteSession(@PathVariable String conversationId, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    if (!client.deleteChatSession(conversationId)) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.noContent().build();
  }

  /**
   * Mid-run steering: injects a user message into the running agent loop of the conversation.
   * Upstream status codes propagate as-is (e.g. 409 when no response is currently being generated)
   * — the chatbot rolls back its optimistic bubble on any non-2xx.
   */
  @PostMapping(XTM_ONE_URI + "/chat/messages/steer")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> steerMessage(
      @RequestBody Map<String, Object> body, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    if (content.isBlank()
        || conversationId == null
        || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(client.steerChatMessage(content, conversationId));
  }

  @PostMapping(XTM_ONE_URI + "/chat/messages/approve")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> approveToolCalls(
      @RequestBody Map<String, Object> body, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    if (!(body.get("decisions") instanceof List<?> rawDecisions) || rawDecisions.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    if (rawDecisions.size() > MAX_DECISIONS_PER_REQUEST) {
      return ResponseEntity.badRequest().build();
    }
    List<Map<String, Object>> decisions = new ArrayList<>();
    for (Object raw : rawDecisions) {
      if (!(raw instanceof Map<?, ?> decision)) {
        return ResponseEntity.badRequest().build();
      }
      Object toolCallId = decision.get("tool_call_id");
      Object verdict = decision.get("decision");
      if (toolCallId == null || verdict == null) {
        return ResponseEntity.badRequest().build();
      }
      if (!ALLOWED_VERDICTS.contains(verdict.toString())) {
        return ResponseEntity.badRequest().build();
      }
      Map<String, Object> forwarded = new HashMap<>();
      forwarded.put("tool_call_id", toolCallId.toString());
      forwarded.put("decision", verdict.toString());
      Object reason = decision.get("rejection_reason");
      if (reason != null) {
        String reasonText = reason.toString();
        if (reasonText.length() > MAX_REJECTION_REASON_LENGTH) {
          return ResponseEntity.badRequest().build();
        }
        // Dropped rather than rejected: a stray reason beside an approval is still a valid
        // decision, and a 400 would lose a consent the reviewer did give.
        if (REJECT_VERDICT.equals(verdict.toString())) {
          forwarded.put("rejection_reason", reasonText);
        }
      }
      decisions.add(forwarded);
    }
    return ResponseEntity.ok(client.approveToolCalls(conversationId, decisions));
  }

  @GetMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/pending-approvals")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> pendingApprovals(
      @PathVariable String conversationId, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(client.getPendingApprovals(conversationId));
  }

  @PostMapping(path = XTM_ONE_URI + "/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<StreamingResponseBody> sendMessage(
      @RequestBody Map<String, Object> body, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    // Telemetry: one chatbot message (attempts semantics, before the upstream call).
    aiMetricCollector.recordChatbotMessage();
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    String agentSlug = body.get("agent_slug") != null ? body.get("agent_slug").toString() : null;
    // Arbitrary host page/application context (e.g. current URL) forwarded so
    // the agent is aware of where the user is. Optional and flexible — only
    // passed upstream when present.
    @SuppressWarnings("unchecked")
    Map<String, Object> context =
        body.get("context") instanceof Map ? (Map<String, Object>) body.get("context") : null;
    boolean supportsToolApproval = Boolean.TRUE.equals(body.get("supports_tool_approval"));

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
                content,
                conversationId,
                agentSlug,
                context,
                supportsToolApproval,
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
                    : "Unable to connect to the AI assistant. Please try again.";
            String errorContent =
                e.getStatusCode().value() == 429
                    ? "⚠️ **Quota exceeded** — " + detail
                    : "⚠️ **Error** — " + detail;
            outputStream.write(
                ("data: {\"type\":\"error\",\"content\":\"" + errorContent + "\"}\n\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
          } catch (Exception e) {
            log.warn("[XTM One Chat] Stream error, agent={}.", agentSlug, e);
            outputStream.write(
                ("data: "
                        + "{\"type\":\"error\",\"content\":\"Unable to connect to the AI assistant. Please try again.\"}"
                        + "\n\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            outputStream.flush();
          }
        };

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(responseBody);
  }

  @PostMapping(path = XTM_ONE_URI + "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<Map<String, Object>> uploadFiles(
      @RequestParam("conversation_id") String conversationId,
      MultipartHttpServletRequest request,
      TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    List<MultipartFile> requestedFiles =
        request.getMultiFileMap().values().stream()
            .flatMap(List::stream)
            .filter(file -> file != null && !file.isEmpty())
            .toList();
    if (requestedFiles.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<String> fileIds = new ArrayList<>();
    for (MultipartFile file : requestedFiles) {
      String fileId = client.uploadChatFile(conversationId, file);
      if (fileId != null && !fileId.isBlank()) {
        fileIds.add(fileId);
      }
    }

    if (fileIds.isEmpty()) {
      return ResponseEntity.internalServerError().build();
    }
    return ResponseEntity.ok(Map.of("file_ids", fileIds));
  }

  /**
   * Downloads an agent-generated file from XTM One.
   *
   * <p>The OpenAEV user is authenticated here (platform session + CSRF); the XTM One JWT is minted
   * server-side by {@link XtmOneClient#downloadChatFile}. The end user therefore never
   * authenticates to XTM One directly — the embedded chatbot points its download URL at this proxy
   * (relative to its {@code apiBaseUrl} of {@code /api/xtmone/chat}).
   */
  @GetMapping(XTM_ONE_URI + "/chat/files/{fileId}/download")
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<byte[]> downloadFile(@PathVariable String fileId, TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (fileId == null || !FILE_ID_PATTERN.matcher(fileId).matches()) {
      return ResponseEntity.badRequest().build();
    }

    XtmOneClient.DownloadedFile file = client.downloadChatFile(fileId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(parseContentType(file.contentType()));
    if (file.contentDisposition() != null && !file.contentDisposition().isBlank()) {
      headers.set(HttpHeaders.CONTENT_DISPOSITION, file.contentDisposition());
    }
    return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
  }

  private MediaType parseContentType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
    try {
      return MediaType.parseMediaType(contentType);
    } catch (Exception ignored) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }
}
