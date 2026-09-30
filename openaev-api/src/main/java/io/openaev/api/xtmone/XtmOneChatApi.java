package io.openaev.api.xtmone;

import com.fasterxml.jackson.databind.node.NullNode;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
  private static final Pattern MESSAGE_ID_PATTERN = FILE_ID_PATTERN;
  private static final Pattern WORKSPACE_ID_PATTERN = FILE_ID_PATTERN;

  private static final String TITLE_FIELD = "title";
  private static final String WORKSPACE_ID_FIELD = "workspace_id";
  private static final List<String> WORKSPACE_FIELDS = List.of("name", "description");

  private static final String REJECT_VERDICT = "reject";
  private static final Set<String> ALLOWED_VERDICTS =
      Set.of("approve", "approve_always", REJECT_VERDICT);
  private static final int MAX_DECISIONS_PER_REQUEST = 50;
  private static final int MAX_REJECTION_REASON_LENGTH = 2000;

  private static final Set<String> ALLOWED_RATINGS = Set.of("positive", "negative");
  private static final int MAX_FEEDBACK_COMMENT_LENGTH = 2000;

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

  /**
   * Creates (or restores) a chat conversation. The body is forwarded as the chat panel sent it
   * ({@code agent_slug}, {@code conversation_id}, {@code workspace_id}, ...), so a field XTM One
   * accepts is never dropped by the proxy.
   */
  @PostMapping(XTM_ONE_URI + "/chat/sessions")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> createSession(
      TxCtx ctx, @RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, Object> result = client.createChatSession(body);
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
  public ResponseEntity<Void> deleteSession(TxCtx ctx, @PathVariable String conversationId) {
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
   * Renames a conversation of the chatbot history menu, or files it into a workspace ({@code
   * workspace_id}; {@code null} takes it out of its workspace). XTM One's status and {@code detail}
   * are relayed, e.g. a 404 for a conversation that is not one of the user's.
   */
  @PatchMapping(XTM_ONE_URI + "/chat/sessions/{conversationId}")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> updateSession(
      TxCtx ctx, @PathVariable String conversationId, @RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, Object> changes = conversationChanges(body);
    if (changes == null) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.updateChatSession(conversationId, changes));
  }

  /**
   * Lists the user's XTM One workspaces, which the chatbot history menu groups conversations by.
   * XTM One's answer is relayed as it came, every workspace field included (a 403 while XTM One is
   * not licensed); an XTM One that is not configured has no workspace to list.
   */
  @GetMapping(XTM_ONE_URI + "/chat/workspaces")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> listWorkspaces(TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(Map.of("workspaces", List.of()));
    }
    return relay(client.listChatWorkspaces());
  }

  /** Creates a personal XTM One workspace ({@code name}, optional {@code description}). */
  @PostMapping(XTM_ONE_URI + "/chat/workspaces")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> createWorkspace(TxCtx ctx, @RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, Object> fields = workspaceFields(body);
    if (fields == null || !fields.containsKey("name")) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.createChatWorkspace(fields));
  }

  /** Renames an XTM One workspace, or changes its description. */
  @PatchMapping(XTM_ONE_URI + "/chat/workspaces/{workspaceId}")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> updateWorkspace(
      TxCtx ctx, @PathVariable String workspaceId, @RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (workspaceId == null || !WORKSPACE_ID_PATTERN.matcher(workspaceId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    Map<String, Object> fields = workspaceFields(body);
    if (fields == null) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.updateChatWorkspace(workspaceId, fields));
  }

  /**
   * Deletes an XTM One workspace. XTM One refuses the user's default workspace and one that still
   * holds work items: its status and {@code detail} are relayed.
   */
  @DeleteMapping(XTM_ONE_URI + "/chat/workspaces/{workspaceId}")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> deleteWorkspace(TxCtx ctx, @PathVariable String workspaceId) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (workspaceId == null || !WORKSPACE_ID_PATTERN.matcher(workspaceId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.deleteChatWorkspace(workspaceId));
  }

  /**
   * Answers with XTM One's own status and JSON body, so the chat panel shows XTM One's {@code
   * detail} when it refuses a change.
   */
  private static ResponseEntity<Object> relay(XtmOneClient.RelayedResponse response) {
    ResponseEntity.BodyBuilder answer = ResponseEntity.status(response.status());
    if (response.body() == null) {
      return answer.build();
    }
    return answer.contentType(MediaType.APPLICATION_JSON).body(response.body());
  }

  /**
   * The workspace fields the chat panel may send, each a string when present; {@code null} when one
   * is not. Whether a name is blank or too long is XTM One's rule: its refusal reaches the panel in
   * its own words.
   */
  private static Map<String, Object> workspaceFields(Map<String, Object> body) {
    Map<String, Object> fields = new HashMap<>();
    for (String field : WORKSPACE_FIELDS) {
      if (body.containsKey(field)) {
        if (!(body.get(field) instanceof String value)) {
          return null;
        }
        fields.put(field, value);
      }
    }
    return fields;
  }

  /**
   * The conversation changes the chat panel may send: a string {@code title}, and a {@code
   * workspace_id} that is a workspace id or an explicit {@code null} (kept, as it takes the
   * conversation out of its workspace); {@code null} when a field is malformed.
   */
  private static Map<String, Object> conversationChanges(Map<String, Object> body) {
    Map<String, Object> changes = new HashMap<>();
    if (body.containsKey(TITLE_FIELD)) {
      if (!(body.get(TITLE_FIELD) instanceof String title)) {
        return null;
      }
      changes.put(TITLE_FIELD, title);
    }
    if (body.containsKey(WORKSPACE_ID_FIELD)) {
      Object workspaceId = body.get(WORKSPACE_ID_FIELD);
      if (workspaceId != null
          && !(workspaceId instanceof String id && WORKSPACE_ID_PATTERN.matcher(id).matches())) {
        return null;
      }
      changes.put(WORKSPACE_ID_FIELD, workspaceId);
    }
    return changes;
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
      TxCtx ctx, @RequestBody Map<String, Object> body) {
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
      TxCtx ctx, @RequestBody Map<String, Object> body) {
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
      TxCtx ctx, @PathVariable String conversationId) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok(client.getPendingApprovals(conversationId));
  }

  /** The prompt library of the user's XTM One web chat, offered by the chatbot prompt picker. */
  @GetMapping(XTM_ONE_URI + "/chat/prompts")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> listPrompts(TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(Map.of("prompts", List.of()));
    }
    return ResponseEntity.ok(client.getChatPrompts());
  }

  /**
   * The user's agentic quota for the chatbot quota indicator: the XTM One payload as-is, or a JSON
   * {@code null} when there is nothing to show (the chatbot then hides the indicator).
   */
  @GetMapping(XTM_ONE_URI + "/chat/quota")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> getQuota(TxCtx ctx) {
    if (!config.isConfigured()) {
      return ResponseEntity.ok(NullNode.getInstance());
    }
    return ResponseEntity.ok(client.getChatQuota());
  }

  /** Rates an assistant message (thumbs up / down with an optional comment). */
  @PostMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Map<String, Object>> submitMessageFeedback(
      TxCtx ctx,
      @PathVariable String conversationId,
      @PathVariable String messageId,
      @RequestBody Map<String, Object> body) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (!isValidMessagePath(conversationId, messageId)) {
      return ResponseEntity.badRequest().build();
    }
    if (!(body.get("rating") instanceof String rating) || !ALLOWED_RATINGS.contains(rating)) {
      return ResponseEntity.badRequest().build();
    }
    String comment = null;
    Object rawComment = body.get("comment");
    if (rawComment != null) {
      // XTM One caps the comment in characters (code points), not UTF-16 units: a comment it
      // would store must not be refused here.
      if (!(rawComment instanceof String text)
          || text.codePointCount(0, text.length()) > MAX_FEEDBACK_COMMENT_LENGTH) {
        return ResponseEntity.badRequest().build();
      }
      comment = text;
    }
    return ResponseEntity.ok(
        client.submitMessageFeedback(conversationId, messageId, rating, comment));
  }

  /** Removes the user's rating of an assistant message. */
  @DeleteMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Void> retractMessageFeedback(
      TxCtx ctx, @PathVariable String conversationId, @PathVariable String messageId) {
    if (!config.isConfigured()) {
      return ResponseEntity.badRequest().build();
    }
    if (!isValidMessagePath(conversationId, messageId)) {
      return ResponseEntity.badRequest().build();
    }
    client.retractMessageFeedback(conversationId, messageId);
    return ResponseEntity.noContent().build();
  }

  private static boolean isValidMessagePath(String conversationId, String messageId) {
    return conversationId != null
        && CONVERSATION_ID_PATTERN.matcher(conversationId).matches()
        && messageId != null
        && MESSAGE_ID_PATTERN.matcher(messageId).matches();
  }

  @PostMapping(path = XTM_ONE_URI + "/chat/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<StreamingResponseBody> sendMessage(
      TxCtx ctx, @RequestBody Map<String, Object> body) {
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
      TxCtx ctx,
      @RequestParam("conversation_id") String conversationId,
      MultipartHttpServletRequest request) {
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
  public ResponseEntity<byte[]> downloadFile(TxCtx ctx, @PathVariable String fileId) {
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
