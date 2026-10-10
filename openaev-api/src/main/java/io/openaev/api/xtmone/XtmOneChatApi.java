package io.openaev.api.xtmone;

import static io.openaev.xtmone.XtmOneNotConfiguredException.requireConfigured;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.openaev.aop.AccessControl;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

/**
 * The embedded chat panel's proxy to XTM One. Every route first checks that XTM One is configured
 * ({@link io.openaev.xtmone.XtmOneNotConfiguredException#requireConfigured}), so an unconfigured
 * XTM One is the same {@code 503} on all of them ({@link XtmOneChatApiExceptionHandler}), then
 * relays what {@link XtmOneClient} relays of XTM One's answer ({@link #relay}).
 */
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

  private static final String REFERENCED_CONVERSATION_IDS_FIELD = "referenced_conversation_ids";
  // XTM One reads at most 5 referenced conversations per message.
  private static final int MAX_REFERENCED_CONVERSATIONS = 5;
  // XTM One's bounds on the @ menu search (in characters, i.e. code points).
  private static final int MAX_REFERENCE_QUERY_LENGTH = 200;
  private static final int MIN_REFERENCE_LIMIT = 1;
  private static final int MAX_REFERENCE_LIMIT = 20;

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
    requireConfigured(config);
    return ResponseEntity.ok(client.listChatAgents("global.assistant"));
  }

  /**
   * Creates (or restores) a chat conversation. The body is forwarded as the chat panel sent it
   * ({@code agent_slug}, {@code conversation_id}, {@code workspace_id}, ...), so a field XTM One
   * accepts is never dropped by the proxy. XTM One's answer is relayed as it came, so the messages
   * of a restored conversation keep every field, such as the {@code conversation_refs} of a user
   * message that referenced other conversations.
   */
  @PostMapping(XTM_ONE_URI + "/chat/sessions")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> createSession(TxCtx ctx, @RequestBody Map<String, Object> body) {
    requireConfigured(config);
    return relay(client.createChatSession(body));
  }

  /** Lists past conversations for the chatbot history menu. */
  @GetMapping(XTM_ONE_URI + "/chat/sessions")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: chat data lives in XTM One and is scoped there to the per-user JWT minted by
  // XtmOneClient — there is no OpenAEV resource to check grants against. The EE gate matches the
  // Ariane feature gating (see AskArianeButton) and XtmOneProxyApi.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> listSessions(TxCtx ctx) {
    requireConfigured(config);
    return relay(client.listChatSessions());
  }

  /** Removes a conversation from the chatbot history menu (archived upstream). */
  @DeleteMapping(XTM_ONE_URI + "/chat/sessions/{conversationId}")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> deleteSession(TxCtx ctx, @PathVariable String conversationId) {
    requireConfigured(config);
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.deleteChatSession(conversationId));
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
    requireConfigured(config);
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
   * not licensed).
   */
  @GetMapping(XTM_ONE_URI + "/chat/workspaces")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> listWorkspaces(TxCtx ctx) {
    requireConfigured(config);
    return relay(client.listChatWorkspaces());
  }

  /** Creates a personal XTM One workspace ({@code name}, optional {@code description}). */
  @PostMapping(XTM_ONE_URI + "/chat/workspaces")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> createWorkspace(TxCtx ctx, @RequestBody Map<String, Object> body) {
    requireConfigured(config);
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
    requireConfigured(config);
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
    requireConfigured(config);
    if (workspaceId == null || !WORKSPACE_ID_PATTERN.matcher(workspaceId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.deleteChatWorkspace(workspaceId));
  }

  /**
   * The conversations the chat panel's {@code @} menu offers to reference in a message: those the
   * user can open in XTM One, searched by {@code q} ({@code limit} of them, not {@code exclude}).
   * Only valid parameters are forwarded: a malformed one is left out rather than refused, so the
   * menu still opens. XTM One's answer is relayed as the workspace list is.
   */
  @GetMapping(XTM_ONE_URI + "/chat/conversation-references")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> searchConversationReferences(
      TxCtx ctx,
      @RequestParam(name = "q", required = false) String query,
      @RequestParam(name = "limit", required = false) String limit,
      @RequestParam(name = "exclude", required = false) String exclude) {
    requireConfigured(config);
    return relay(
        client.searchChatConversationReferences(
            referenceQuery(query), referenceLimit(limit), excludedConversation(exclude)));
  }

  /**
   * The {@code @} menu search text, trimmed and cut to XTM One's 200 characters (a longer one would
   * be refused there); {@code null} when blank.
   */
  private static String referenceQuery(String query) {
    if (query == null || query.isBlank()) {
      return null;
    }
    String trimmed = query.strip();
    if (trimmed.codePointCount(0, trimmed.length()) <= MAX_REFERENCE_QUERY_LENGTH) {
      return trimmed;
    }
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_REFERENCE_QUERY_LENGTH)).strip();
  }

  /**
   * How many conversations the {@code @} menu asks for, when it is a whole number from 1 to 20;
   * {@code null} (XTM One's default) otherwise.
   */
  private static Integer referenceLimit(String limit) {
    if (limit == null) {
      return null;
    }
    try {
      int value = Integer.parseInt(limit.strip());
      return value >= MIN_REFERENCE_LIMIT && value <= MAX_REFERENCE_LIMIT ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * The conversation the {@code @} menu must not offer, when it is a UUID; {@code null} otherwise.
   */
  private static String excludedConversation(String exclude) {
    return exclude != null && CONVERSATION_ID_PATTERN.matcher(exclude).matches() ? exclude : null;
  }

  /**
   * The conversations a message references with {@code @}: the UUID strings of a JSON array, in
   * their order, each once (compared and forwarded in lower case), at most 5. Anything else is
   * dropped rather than refused, so a stray entry never costs the user their message; XTM One then
   * reads only the conversations they can open.
   */
  private static List<String> referencedConversationIds(Object raw) {
    if (!(raw instanceof List<?> entries)) {
      return List.of();
    }
    Set<String> ids = new LinkedHashSet<>();
    for (Object entry : entries) {
      if (ids.size() == MAX_REFERENCED_CONVERSATIONS) {
        break;
      }
      if (entry instanceof String id && CONVERSATION_ID_PATTERN.matcher(id).matches()) {
        ids.add(id.toLowerCase(Locale.ROOT));
      }
    }
    return List.copyOf(ids);
  }

  /**
   * Answers with what the client relays of XTM One's answer, its status and JSON body, so the chat
   * panel shows XTM One's {@code detail} when it refuses a change. The one way the chat routes
   * answer for XTM One, their exception handler included ({@link XtmOneChatApiExceptionHandler}).
   */
  static ResponseEntity<Object> relay(XtmOneClient.RelayedResponse response) {
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
   * Mid-run steering: injects a user message into the running agent loop of the conversation. XTM
   * One's answer is relayed (e.g. a 409 when no response is currently being generated): the chatbot
   * rolls back its optimistic bubble on any non-2xx.
   */
  @PostMapping(XTM_ONE_URI + "/chat/messages/steer")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> steerMessage(TxCtx ctx, @RequestBody Map<String, Object> body) {
    requireConfigured(config);
    String content = body.get("content") != null ? body.get("content").toString() : "";
    String conversationId =
        body.get("conversation_id") != null ? body.get("conversation_id").toString() : null;
    if (content.isBlank()
        || conversationId == null
        || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.steerChatMessage(content, conversationId));
  }

  @PostMapping(XTM_ONE_URI + "/chat/messages/approve")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> approveToolCalls(TxCtx ctx, @RequestBody Map<String, Object> body) {
    requireConfigured(config);
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
    return relay(client.approveToolCalls(conversationId, decisions));
  }

  @GetMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/pending-approvals")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions — per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> pendingApprovals(TxCtx ctx, @PathVariable String conversationId) {
    requireConfigured(config);
    if (conversationId == null || !CONVERSATION_ID_PATTERN.matcher(conversationId).matches()) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.getPendingApprovals(conversationId));
  }

  /** The prompt library of the user's XTM One web chat, offered by the chatbot prompt picker. */
  @GetMapping(XTM_ONE_URI + "/chat/prompts")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> listPrompts(TxCtx ctx) {
    requireConfigured(config);
    return relay(client.getChatPrompts());
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
    requireConfigured(config);
    return relay(client.getChatQuota());
  }

  /** Rates an assistant message (thumbs up / down with an optional comment). */
  @PostMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> submitMessageFeedback(
      TxCtx ctx,
      @PathVariable String conversationId,
      @PathVariable String messageId,
      @RequestBody Map<String, Object> body) {
    requireConfigured(config);
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
    return relay(client.submitMessageFeedback(conversationId, messageId, rating, comment));
  }

  /** Removes the user's rating of an assistant message. */
  @DeleteMapping(XTM_ONE_URI + "/chat/conversations/{conversationId}/messages/{messageId}/feedback")
  @Transactional(propagation = Propagation.NEVER)
  // skipRBAC: see listSessions - per-user scoping is enforced upstream by the minted JWT.
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  public ResponseEntity<Object> retractMessageFeedback(
      TxCtx ctx, @PathVariable String conversationId, @PathVariable String messageId) {
    requireConfigured(config);
    if (!isValidMessagePath(conversationId, messageId)) {
      return ResponseEntity.badRequest().build();
    }
    return relay(client.retractMessageFeedback(conversationId, messageId));
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
    requireConfigured(config);
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
    // Conversations picked from the panel's @ menu.
    List<String> referencedConversationIds =
        referencedConversationIds(body.get(REFERENCED_CONVERSATION_IDS_FIELD));

    StreamingResponseBody responseBody =
        outputStream -> {
          try {
            client.streamChatMessage(
                content,
                conversationId,
                agentSlug,
                context,
                supportsToolApproval,
                referencedConversationIds,
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
            outputStream.write(sseError(errorContent));
            outputStream.flush();
          } catch (Exception e) {
            log.warn("[XTM One Chat] Stream error, agent={}.", agentSlug, e);
            outputStream.write(
                sseError("Unable to connect to the AI assistant. Please try again."));
            outputStream.flush();
          }
        };

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .header("Cache-Control", "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(responseBody);
  }

  /**
   * An SSE {@code error} event, written as JSON so a quote in XTM One's {@code detail} cannot break
   * it.
   */
  private static byte[] sseError(String content) {
    String event =
        JsonNodeFactory.instance
            .objectNode()
            .put("type", "error")
            .put("content", content)
            .toString();
    return ("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Uploads the files of a chat message, one XTM One upload each, and answers the ids of those XTM
   * One stored ({@code {"file_ids": [...]}}). When it stored none, its last refusal is relayed.
   */
  @PostMapping(path = XTM_ONE_URI + "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Transactional(propagation = Propagation.NEVER)
  public ResponseEntity<Object> uploadFiles(
      TxCtx ctx,
      @RequestParam("conversation_id") String conversationId,
      MultipartHttpServletRequest request) {
    requireConfigured(config);
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
    XtmOneClient.RelayedResponse refusal = null;
    for (MultipartFile file : requestedFiles) {
      XtmOneClient.RelayedResponse uploaded = client.uploadChatFile(conversationId, file);
      if (uploaded.isSuccess()) {
        JsonNode fileId = uploaded.body() != null ? uploaded.body().get("file_id") : null;
        if (fileId != null && !fileId.isNull() && !fileId.asText().isBlank()) {
          fileIds.add(fileId.asText());
        }
      } else {
        refusal = uploaded;
      }
    }

    if (fileIds.isEmpty()) {
      return refusal != null ? relay(refusal) : ResponseEntity.internalServerError().build();
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
    requireConfigured(config);
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
