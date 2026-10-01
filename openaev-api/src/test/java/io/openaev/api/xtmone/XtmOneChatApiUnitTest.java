package io.openaev.api.xtmone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Unit test for the {@code /api/xtmone/chat} proxy forwarding contract. For {@code /messages} it
 * executes the returned {@link StreamingResponseBody} so the controller body actually runs and we
 * can verify the arguments handed to {@link XtmOneClient#streamChatMessage}; the prompts, quota,
 * message feedback, workspace and conversation filing routes are checked for their validation and
 * what they hand to the client. Pure POJO (no Spring / async dispatch) to keep the contract check
 * fast and deterministic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Chat API forwarding tests")
class XtmOneChatApiUnitTest {

  @Mock private XtmOneClient client;
  @Mock private XtmOneConfig config;
  @Mock private AiMetricCollector aiMetricCollector;
  @InjectMocks private XtmOneChatApi api;

  @Test
  @DisplayName("Given a context in the request body should forward it to streamChatMessage")
  void given_context_should_forwardToClient() throws Exception {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(true);
    Map<String, Object> context = Map.of("url", "/dashboard/reports/1");
    Map<String, Object> body = new HashMap<>();
    body.put("content", "hello");
    body.put("agent_slug", "agent-1");
    body.put("context", context);

    // -- ACT --
    ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
    response.getBody().writeTo(new ByteArrayOutputStream());

    // -- ASSERT --
    verify(client)
        .streamChatMessage(
            eq("hello"), isNull(), eq("agent-1"), eq(context), eq(false), eq(List.of()), any());
  }

  @Test
  @DisplayName("Given no context in the request body should forward a null context")
  void given_noContext_should_forwardNull() throws Exception {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(true);
    Map<String, Object> body = new HashMap<>();
    body.put("content", "hello");
    body.put("agent_slug", "agent-1");

    // -- ACT --
    ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
    response.getBody().writeTo(new ByteArrayOutputStream());

    // -- ASSERT --
    verify(client)
        .streamChatMessage(
            eq("hello"), isNull(), eq("agent-1"), isNull(), eq(false), eq(List.of()), any());
  }

  @Test
  @DisplayName("Given supports_tool_approval true should forward the declaration upstream")
  void given_supportsToolApproval_should_forwardTrue() throws Exception {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(true);
    Map<String, Object> body = new HashMap<>();
    body.put("content", "hello");
    body.put("agent_slug", "agent-1");
    body.put("supports_tool_approval", true);

    // -- ACT --
    ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
    response.getBody().writeTo(new ByteArrayOutputStream());

    // -- ASSERT --
    verify(client)
        .streamChatMessage(
            eq("hello"), isNull(), eq("agent-1"), isNull(), eq(true), eq(List.of()), any());
  }

  @Test
  @DisplayName("Given supports_tool_approval false should forward false")
  void given_supportsToolApprovalFalse_should_forwardFalse() throws Exception {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(true);
    Map<String, Object> body = new HashMap<>();
    body.put("content", "hello");
    body.put("supports_tool_approval", false);

    // -- ACT --
    ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
    response.getBody().writeTo(new ByteArrayOutputStream());

    // -- ASSERT --
    verify(client)
        .streamChatMessage(
            eq("hello"), isNull(), isNull(), isNull(), eq(false), eq(List.of()), any());
  }

  @Test
  @DisplayName("Given a non-boolean supports_tool_approval should not claim support")
  void given_nonBooleanSupportsToolApproval_should_forwardFalse() throws Exception {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(true);
    Map<String, Object> body = new HashMap<>();
    body.put("content", "hello");
    body.put("supports_tool_approval", "true");

    // -- ACT --
    ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
    response.getBody().writeTo(new ByteArrayOutputStream());

    // -- ASSERT --
    verify(client)
        .streamChatMessage(
            eq("hello"), isNull(), isNull(), isNull(), eq(false), eq(List.of()), any());
  }

  @Nested
  @DisplayName("Conversation references")
  class ConversationReferences {

    private static final String FIRST_ID = "44444444-4444-4444-4444-444444444444";
    private static final String SECOND_ID = "55555555-5555-5555-5555-555555555555";
    private static final String EXCLUDED_ID = "11111111-1111-1111-1111-111111111111";

    private static Stream<Arguments> referencedConversationIds() {
      List<Object> withNull = new ArrayList<>();
      withNull.add(null);
      withNull.add(FIRST_ID);
      return Stream.of(
          Arguments.of(
              "drops what is not a UUID string",
              List.of(
                  SECOND_ID, 42, "not-a-uuid", "../" + FIRST_ID, Map.of("id", FIRST_ID), FIRST_ID),
              List.of(SECOND_ID, FIRST_ID)),
          Arguments.of(
              "drops a repeated conversation, whatever its case",
              List.of(FIRST_ID, SECOND_ID, FIRST_ID, SECOND_ID.toUpperCase(Locale.ROOT)),
              List.of(FIRST_ID, SECOND_ID)),
          Arguments.of(
              "forwards an upper-case UUID in lower case",
              List.of(FIRST_ID.replace('4', 'A')),
              List.of(FIRST_ID.replace('4', 'a'))),
          Arguments.of("drops a null entry", withNull, List.of(FIRST_ID)),
          Arguments.of("drops a field that is not an array", FIRST_ID, List.of()),
          Arguments.of("forwards none for an empty array", List.of(), List.of()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referencedConversationIds")
    @DisplayName("Given referenced conversations should forward only distinct UUIDs in order")
    void given_referencedConversations_should_forwardDistinctUuidsInOrder(
        String description, Object referenced, List<String> expected) throws Exception {
      // -- ARRANGE --
      when(config.isConfigured()).thenReturn(true);
      Map<String, Object> body = new HashMap<>();
      body.put("content", "hello");
      body.put("referenced_conversation_ids", referenced);

      // -- ACT --
      ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
      response.getBody().writeTo(new ByteArrayOutputStream());

      // -- ASSERT --
      verify(client)
          .streamChatMessage(
              eq("hello"), isNull(), isNull(), isNull(), eq(false), eq(expected), any());
    }

    @Test
    @DisplayName("Given more than five referenced conversations should forward the first five")
    void given_tooManyReferencedConversations_should_forwardFirstFive() throws Exception {
      // -- ARRANGE --
      when(config.isConfigured()).thenReturn(true);
      List<String> ids =
          Stream.of("1", "2", "3", "4", "5", "6", "7")
              .map(digit -> digit.repeat(8) + "-1111-1111-1111-111111111111")
              .toList();
      List<Object> referenced = new ArrayList<>(List.of("not-a-uuid", ids.get(0)));
      referenced.addAll(ids);
      Map<String, Object> body = new HashMap<>();
      body.put("content", "hello");
      body.put("referenced_conversation_ids", referenced);

      // -- ACT --
      ResponseEntity<StreamingResponseBody> response = api.sendMessage(TxCtx.missing(), body);
      response.getBody().writeTo(new ByteArrayOutputStream());

      // -- ASSERT --
      verify(client)
          .streamChatMessage(
              eq("hello"), isNull(), isNull(), isNull(), eq(false), eq(ids.subList(0, 5)), any());
    }

    /** Stubs any search with an empty answer, so a test can check what was forwarded. */
    private void stubAnySearch() {
      when(client.searchChatConversationReferences(any(), any(), any()))
          .thenReturn(new XtmOneClient.RelayedResponse(200, JsonNodeFactory.instance.objectNode()));
    }

    @Test
    @DisplayName("Given XTM One not configured should answer no conversation to reference")
    void given_notConfigured_should_answerNoConversation() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Object> response =
          api.searchConversationReferences(TxCtx.missing(), "red", "5", EXCLUDED_ID);

      assertEquals(Map.of("conversations", List.of()), response.getBody());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given valid search parameters should forward them, the text trimmed")
    void given_validParameters_should_forwardThem() {
      when(config.isConfigured()).thenReturn(true);

      stubAnySearch();

      api.searchConversationReferences(TxCtx.missing(), "  red team \t", " 20 ", EXCLUDED_ID);

      verify(client).searchChatConversationReferences("red team", 20, EXCLUDED_ID);
    }

    private static Stream<Arguments> invalidSearchParameters() {
      return Stream.of(
          Arguments.of(null, null, null),
          Arguments.of("   ", "0", "not-a-uuid"),
          Arguments.of("", "21", "../" + EXCLUDED_ID),
          Arguments.of("\t", "five", EXCLUDED_ID + "0"),
          Arguments.of(" ", "5.5", ""),
          Arguments.of(" ", "-1", " " + EXCLUDED_ID),
          Arguments.of(" ", "99999999999", EXCLUDED_ID.replace('-', '_')));
    }

    @ParameterizedTest
    @MethodSource("invalidSearchParameters")
    @DisplayName("Given a blank text, an out-of-range limit or a non-UUID exclude should omit it")
    void given_invalidParameters_should_omitThem(String query, String limit, String exclude) {
      when(config.isConfigured()).thenReturn(true);

      stubAnySearch();

      api.searchConversationReferences(TxCtx.missing(), query, limit, exclude);

      verify(client).searchChatConversationReferences(null, null, null);
    }

    @Test
    @DisplayName("Given a search text over 200 characters should forward its first 200")
    void given_overlongQuery_should_forwardFirst200Characters() {
      // XTM One counts characters (code points): 201 emoji are 402 Java chars.
      when(config.isConfigured()).thenReturn(true);
      String emoji = "😀";

      stubAnySearch();

      api.searchConversationReferences(TxCtx.missing(), " " + emoji.repeat(201), null, null);

      verify(client).searchChatConversationReferences(emoji.repeat(200), null, null);
    }

    @Test
    @DisplayName("Given XTM One answers should relay its status and body")
    void given_upstreamAnswer_should_relayStatusAndBody() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode refusal = JsonNodeFactory.instance.objectNode().put("detail", "[XTM One] HTTP 401");
      when(client.searchChatConversationReferences("red", null, null))
          .thenReturn(new XtmOneClient.RelayedResponse(422, refusal));

      ResponseEntity<Object> response =
          api.searchConversationReferences(TxCtx.missing(), "red", null, null);

      assertEquals(422, response.getStatusCode().value());
      assertEquals(refusal, response.getBody());
    }

    @Test
    @DisplayName("Given XTM One lists conversations should relay them unchanged")
    void given_conversations_should_relayThem() {
      when(config.isConfigured()).thenReturn(true);
      ObjectNode conversations = JsonNodeFactory.instance.objectNode();
      conversations
          .putArray("conversations")
          .addObject()
          .put("id", FIRST_ID)
          .put("title", "Red team plan")
          .put("key", "red-team-plan")
          .put("is_own", false);
      when(client.searchChatConversationReferences(null, null, null))
          .thenReturn(new XtmOneClient.RelayedResponse(200, conversations));

      ResponseEntity<Object> response =
          api.searchConversationReferences(TxCtx.missing(), null, null, null);

      assertEquals(200, response.getStatusCode().value());
      assertEquals(conversations, response.getBody());
    }
  }

  @Nested
  @DisplayName("Prompts and quota")
  class PromptsAndQuota {

    @Test
    @DisplayName("Given XTM One not configured should answer an empty prompt list")
    void given_notConfigured_should_answerNoPrompts() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Map<String, Object>> response = api.listPrompts(TxCtx.missing());

      assertEquals(Map.of("prompts", List.of()), response.getBody());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given XTM One configured should relay the prompts")
    void given_configured_should_relayPrompts() {
      when(config.isConfigured()).thenReturn(true);
      Map<String, Object> payload = Map.of("prompts", List.of(Map.of("id", "p-1")));
      when(client.getChatPrompts()).thenReturn(payload);

      ResponseEntity<Map<String, Object>> response = api.listPrompts(TxCtx.missing());

      assertEquals(payload, response.getBody());
    }

    @Test
    @DisplayName("Given XTM One not configured should answer a null quota")
    void given_notConfigured_should_answerNullQuota() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Object> response = api.getQuota(TxCtx.missing());

      assertEquals(NullNode.getInstance(), response.getBody());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given XTM One configured should relay the quota")
    void given_configured_should_relayQuota() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode quota = JsonNodeFactory.instance.objectNode().put("used", 3);
      when(client.getChatQuota()).thenReturn(quota);

      ResponseEntity<Object> response = api.getQuota(TxCtx.missing());

      assertEquals(quota, response.getBody());
    }
  }

  @Nested
  @DisplayName("Message feedback")
  class MessageFeedback {

    private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String MESSAGE_ID = "22222222-2222-2222-2222-222222222222";

    private static Stream<Arguments> invalidPaths() {
      return Stream.of(
          Arguments.of("not-a-uuid", MESSAGE_ID),
          Arguments.of(CONVERSATION_ID, "not-a-uuid"),
          Arguments.of(CONVERSATION_ID, "../" + MESSAGE_ID));
    }

    private static Stream<Arguments> invalidBodies() {
      return Stream.of(
          Arguments.of(Map.of()),
          Arguments.of(Map.of("rating", "Positive")),
          Arguments.of(Map.of("rating", 1)),
          Arguments.of(Map.of("rating", "positive", "comment", 42)),
          Arguments.of(Map.of("rating", "negative", "comment", "x".repeat(2001))));
    }

    @Test
    @DisplayName("Given XTM One not configured should refuse a rating")
    void given_notConfigured_should_refuseRating() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Map<String, Object>> response =
          api.submitMessageFeedback(
              TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID, Map.of("rating", "positive"));

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    @DisplayName("Given a malformed conversation or message id should refuse without forwarding")
    void given_malformedIds_should_refuse(String conversationId, String messageId) {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Map<String, Object>> post =
          api.submitMessageFeedback(
              TxCtx.missing(), conversationId, messageId, Map.of("rating", "positive"));
      ResponseEntity<Void> delete =
          api.retractMessageFeedback(TxCtx.missing(), conversationId, messageId);

      assertEquals(400, post.getStatusCode().value());
      assertEquals(400, delete.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    @DisplayName("Given an invalid rating or comment should refuse without forwarding")
    void given_invalidBody_should_refuse(Map<String, Object> body) {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Map<String, Object>> response =
          api.submitMessageFeedback(TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID, body);

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given a valid rating should forward it and relay the stored rating")
    void given_validRating_should_forward() {
      when(config.isConfigured()).thenReturn(true);
      Map<String, Object> stored = Map.of("rating", "negative", "comment", "Wrong CVE");
      when(client.submitMessageFeedback(CONVERSATION_ID, MESSAGE_ID, "negative", "Wrong CVE"))
          .thenReturn(stored);

      ResponseEntity<Map<String, Object>> response =
          api.submitMessageFeedback(
              TxCtx.missing(),
              CONVERSATION_ID,
              MESSAGE_ID,
              Map.of("rating", "negative", "comment", "Wrong CVE"));

      assertEquals(200, response.getStatusCode().value());
      assertEquals(stored, response.getBody());
    }

    @Test
    @DisplayName("Given no comment should forward a null comment")
    void given_noComment_should_forwardNullComment() {
      when(config.isConfigured()).thenReturn(true);

      api.submitMessageFeedback(
          TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID, Map.of("rating", "positive"));

      verify(client)
          .submitMessageFeedback(eq(CONVERSATION_ID), eq(MESSAGE_ID), eq("positive"), isNull());
    }

    @Test
    @DisplayName("Given a comment of 2000 characters beyond the BMP should forward it")
    void given_maxLengthCommentInCodePoints_should_forward() {
      // XTM One counts characters, not UTF-16 units: 2000 emoji are 4000 Java chars.
      when(config.isConfigured()).thenReturn(true);
      String comment = "\uD83D\uDE00".repeat(2000);

      ResponseEntity<Map<String, Object>> response =
          api.submitMessageFeedback(
              TxCtx.missing(),
              CONVERSATION_ID,
              MESSAGE_ID,
              Map.of("rating", "positive", "comment", comment));

      assertEquals(200, response.getStatusCode().value());
      verify(client).submitMessageFeedback(CONVERSATION_ID, MESSAGE_ID, "positive", comment);
    }

    @Test
    @DisplayName("Given XTM One not configured should refuse a retraction")
    void given_notConfigured_should_refuseRetraction() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Void> response =
          api.retractMessageFeedback(TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID);

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given valid ids should retract the rating and answer 204")
    void given_validIds_should_retractAndAnswerNoContent() {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Void> response =
          api.retractMessageFeedback(TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID);

      assertEquals(204, response.getStatusCode().value());
      verify(client).retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID);
    }
  }

  @Nested
  @DisplayName("Workspaces and conversation filing")
  class WorkspacesAndFiling {

    private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";
    private static final String WORKSPACE_ID = "33333333-3333-3333-3333-333333333333";

    private static Stream<Arguments> invalidConversationChanges() {
      return Stream.of(
          Arguments.of(Map.of("title", 42)),
          Arguments.of(Map.of("workspace_id", "not-a-uuid")),
          Arguments.of(Map.of("workspace_id", "../" + WORKSPACE_ID)),
          Arguments.of(Map.of("workspace_id", 7)));
    }

    private static Stream<Arguments> invalidWorkspaceBodies() {
      return Stream.of(
          Arguments.of(Map.of()),
          Arguments.of(Map.of("description", "Q3")),
          Arguments.of(Map.of("name", 1)),
          Arguments.of(Map.of("name", "Red team", "description", 2)));
    }

    @Test
    @DisplayName("Given a session body should forward it to XTM One as the panel sent it")
    void given_sessionBody_should_forwardItAsIs() {
      when(config.isConfigured()).thenReturn(true);
      Map<String, Object> body =
          Map.of("agent_slug", "ariane", "workspace_id", WORKSPACE_ID, "future_field", true);
      Map<String, Object> created = Map.of("conversation_id", CONVERSATION_ID);
      when(client.createChatSession(body)).thenReturn(created);

      ResponseEntity<Map<String, Object>> response = api.createSession(TxCtx.missing(), body);

      assertEquals(created, response.getBody());
    }

    @ParameterizedTest
    @MethodSource("invalidConversationChanges")
    @DisplayName("Given a malformed title or workspace id should refuse without forwarding")
    void given_malformedConversationChanges_should_refuse(Map<String, Object> body) {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Object> response = api.updateSession(TxCtx.missing(), CONVERSATION_ID, body);

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given a null workspace id should forward the explicit null that unfiles it")
    void given_nullWorkspaceId_should_forwardExplicitNull() {
      when(config.isConfigured()).thenReturn(true);
      Map<String, Object> body = new HashMap<>();
      body.put("workspace_id", null);
      JsonNode updated = JsonNodeFactory.instance.objectNode().putNull("workspace_id");
      Map<String, Object> expected = new HashMap<>();
      expected.put("workspace_id", null);
      when(client.updateChatSession(CONVERSATION_ID, expected))
          .thenReturn(new XtmOneClient.RelayedResponse(200, updated));

      ResponseEntity<Object> response = api.updateSession(TxCtx.missing(), CONVERSATION_ID, body);

      assertEquals(200, response.getStatusCode().value());
      assertEquals(updated, response.getBody());
    }

    @ParameterizedTest
    @MethodSource("invalidWorkspaceBodies")
    @DisplayName("Given a workspace without a string name should refuse without forwarding")
    void given_invalidWorkspaceBody_should_refuse(Map<String, Object> body) {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Object> response = api.createWorkspace(TxCtx.missing(), body);

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given a malformed workspace id should refuse an update and a deletion")
    void given_malformedWorkspaceId_should_refuse() {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Object> update =
          api.updateWorkspace(TxCtx.missing(), "not-a-uuid", Map.of("name", "Blue team"));
      ResponseEntity<Object> delete = api.deleteWorkspace(TxCtx.missing(), "../" + WORKSPACE_ID);

      assertEquals(400, update.getStatusCode().value());
      assertEquals(400, delete.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given XTM One refuses a deletion should relay its status and detail")
    void given_refusedDeletion_should_relayStatusAndDetail() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode refusal = JsonNodeFactory.instance.objectNode().put("detail", "Default workspace");
      when(client.deleteChatWorkspace(WORKSPACE_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(409, refusal));

      ResponseEntity<Object> response = api.deleteWorkspace(TxCtx.missing(), WORKSPACE_ID);

      assertEquals(409, response.getStatusCode().value());
      assertEquals(refusal, response.getBody());
    }

    @Test
    @DisplayName("Given XTM One deletes the workspace should answer 204 without a body")
    void given_deletedWorkspace_should_answerNoContent() {
      when(config.isConfigured()).thenReturn(true);
      when(client.deleteChatWorkspace(WORKSPACE_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(204, null));

      ResponseEntity<Object> response = api.deleteWorkspace(TxCtx.missing(), WORKSPACE_ID);

      assertEquals(204, response.getStatusCode().value());
      assertNull(response.getBody());
    }

    @Test
    @DisplayName("Given XTM One not configured should answer an empty workspace list")
    void given_notConfigured_should_answerNoWorkspace() {
      when(config.isConfigured()).thenReturn(false);

      ResponseEntity<Object> response = api.listWorkspaces(TxCtx.missing());

      assertEquals(Map.of("workspaces", List.of()), response.getBody());
      verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "createSession",
          "listSessions",
          "updateSession",
          "deleteSession",
          "listWorkspaces",
          "createWorkspace",
          "updateWorkspace",
          "deleteWorkspace",
          "searchConversationReferences",
          "sendMessage"
        })
    @DisplayName("Given a conversation or workspace route should require Enterprise Edition")
    void given_conversationOrWorkspaceRoute_should_requireEnterpriseEdition(String name) {
      // The EE license is only checked by AccessControlAspect on an @AccessControl endpoint.
      Method endpoint =
          Arrays.stream(XtmOneChatApi.class.getDeclaredMethods())
              .filter(method -> method.getName().equals(name))
              .findFirst()
              .orElseThrow();

      AccessControl accessControl = endpoint.getAnnotation(AccessControl.class);

      assertNotNull(accessControl, name + " must carry @AccessControl");
      assertTrue(accessControl.isEnterpriseEdition(), name + " must be Enterprise Edition gated");
    }
  }
}
