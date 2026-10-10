package io.openaev.api.xtmone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import io.openaev.xtmone.XtmOneNotConfiguredException;
import io.openaev.xtmone.XtmOneUpstreamException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Unit test for the {@code /api/xtmone/chat} proxy forwarding contract. For {@code /messages} it
 * executes the returned {@link StreamingResponseBody} so the controller body actually runs and we
 * can verify the arguments handed to {@link XtmOneClient#streamChatMessage}; the prompts, quota,
 * message feedback, workspace, conversation filing and upload routes are checked for their
 * validation and what they hand to the client, and every route for its refusal of an unconfigured
 * XTM One. Pure POJO (no Spring / async dispatch) to keep the contract check fast and
 * deterministic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Chat API forwarding tests")
class XtmOneChatApiUnitTest {

  private static final XtmOneClient.RelayedResponse OK_EMPTY =
      new XtmOneClient.RelayedResponse(200, JsonNodeFactory.instance.objectNode());

  @Mock private XtmOneClient client;
  @Mock private XtmOneConfig config;
  @Mock private AiMetricCollector aiMetricCollector;
  @InjectMocks private XtmOneChatApi api;

  @Nested
  @DisplayName("XTM One not configured")
  class NotConfigured {

    /** Every route of the chat API, by name. */
    static Stream<Arguments> endpoints() {
      return Arrays.stream(XtmOneChatApi.class.getDeclaredMethods())
          .filter(method -> Modifier.isPublic(method.getModifiers()))
          .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
          .sorted(Comparator.comparing(Method::getName))
          .map(method -> Arguments.of(Named.of(method.getName(), method)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    @DisplayName("Given XTM One not configured, every route refuses before doing anything")
    void given_notConfigured_should_refuseEveryRoute(Method endpoint) {
      when(config.isConfigured()).thenReturn(false);
      Object[] arguments =
          Arrays.stream(endpoint.getParameterTypes()).map(NotConfigured::placeholder).toArray();

      InvocationTargetException thrown =
          assertThrows(InvocationTargetException.class, () -> endpoint.invoke(api, arguments));

      XtmOneNotConfiguredException refusal =
          assertInstanceOf(XtmOneNotConfiguredException.class, thrown.getCause());
      assertEquals(XtmOneNotConfiguredException.MESSAGE, refusal.getReason());
      verifyNoInteractions(client, aiMetricCollector);
    }

    /** An argument the guard never reads: it refuses before validating anything. */
    private static Object placeholder(Class<?> type) {
      if (type == TxCtx.class) {
        return TxCtx.missing();
      }
      if (type == String.class) {
        return "11111111-1111-1111-1111-111111111111";
      }
      if (type == Map.class) {
        return new HashMap<String, Object>();
      }
      if (type == MultipartHttpServletRequest.class) {
        return mock(MultipartHttpServletRequest.class);
      }
      return null;
    }

    @Test
    @DisplayName("Given XTM One not configured, the chat routes answer 503 with one message")
    void given_notConfigured_should_answerServiceUnavailable() {
      ResponseEntity<Object> response =
          new XtmOneChatApiExceptionHandler()
              .handleNotConfigured(new XtmOneNotConfiguredException());

      assertEquals(503, response.getStatusCode().value());
      assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
      assertEquals(
          JsonNodeFactory.instance.objectNode().put("detail", "XTM One is not configured"),
          response.getBody());
    }

    @Test
    @DisplayName("Given XTM One refuses a route it does not relay, its refusal is relayed")
    void given_upstreamRefusal_should_relayIt() {
      XtmOneClient.RelayedResponse refusal =
          XtmOneClient.RelayedResponse.ofDetail(422, TextNode.valueOf("[XTM One] HTTP 401"));

      ResponseEntity<Object> response =
          new XtmOneChatApiExceptionHandler()
              .handleUpstreamRefusal(new XtmOneUpstreamException(refusal));

      assertEquals(422, response.getStatusCode().value());
      assertEquals(refusal.body(), response.getBody());
    }
  }

  @Nested
  @DisplayName("File upload")
  class FileUpload {

    private static final String CONVERSATION_ID = "11111111-1111-1111-1111-111111111111";

    private MultipartHttpServletRequest twoFiles() {
      LinkedMultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<>();
      files.add("file", new MockMultipartFile("file", "a.csv", "text/csv", new byte[] {1}));
      files.add("file", new MockMultipartFile("file", "b.csv", "text/csv", new byte[] {2}));
      MultipartHttpServletRequest request = mock(MultipartHttpServletRequest.class);
      when(request.getMultiFileMap()).thenReturn(files);
      return request;
    }

    @Test
    @DisplayName("Given XTM One stores one file of two should answer the stored id")
    void given_oneStored_should_answerItsId() {
      when(config.isConfigured()).thenReturn(true);
      when(client.uploadChatFile(eq(CONVERSATION_ID), any()))
          .thenReturn(
              new XtmOneClient.RelayedResponse(
                  200, JsonNodeFactory.instance.objectNode().put("file_id", "file-1")))
          .thenReturn(
              XtmOneClient.RelayedResponse.ofDetail(413, TextNode.valueOf("File too large")));

      ResponseEntity<Object> response =
          api.uploadFiles(TxCtx.missing(), CONVERSATION_ID, twoFiles());

      assertEquals(200, response.getStatusCode().value());
      assertEquals(Map.of("file_ids", List.of("file-1")), response.getBody());
    }

    @Test
    @DisplayName("Given XTM One stores no file should relay its refusal")
    void given_noneStored_should_relayRefusal() {
      when(config.isConfigured()).thenReturn(true);
      XtmOneClient.RelayedResponse refusal =
          XtmOneClient.RelayedResponse.ofDetail(413, TextNode.valueOf("File too large"));
      when(client.uploadChatFile(eq(CONVERSATION_ID), any())).thenReturn(refusal);

      ResponseEntity<Object> response =
          api.uploadFiles(TxCtx.missing(), CONVERSATION_ID, twoFiles());

      assertEquals(413, response.getStatusCode().value());
      assertEquals(refusal.body(), response.getBody());
    }
  }

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
        .streamChatMessage(eq("hello"), isNull(), eq("agent-1"), eq(context), eq(false), any());
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
        .streamChatMessage(eq("hello"), isNull(), eq("agent-1"), isNull(), eq(false), any());
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
        .streamChatMessage(eq("hello"), isNull(), eq("agent-1"), isNull(), eq(true), any());
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
    verify(client).streamChatMessage(eq("hello"), isNull(), isNull(), isNull(), eq(false), any());
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
    verify(client).streamChatMessage(eq("hello"), isNull(), isNull(), isNull(), eq(false), any());
  }

  @Nested
  @DisplayName("Prompts and quota")
  class PromptsAndQuota {

    @Test
    @DisplayName("Given XTM One configured should relay the prompts")
    void given_configured_should_relayPrompts() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode payload = JsonNodeFactory.instance.objectNode().putArray("prompts").addObject();
      when(client.getChatPrompts()).thenReturn(new XtmOneClient.RelayedResponse(200, payload));

      ResponseEntity<Object> response = api.listPrompts(TxCtx.missing());

      assertEquals(payload, response.getBody());
    }

    @Test
    @DisplayName("Given XTM One configured should relay the quota")
    void given_configured_should_relayQuota() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode quota = JsonNodeFactory.instance.objectNode().put("used", 3);
      when(client.getChatQuota()).thenReturn(new XtmOneClient.RelayedResponse(200, quota));

      ResponseEntity<Object> response = api.getQuota(TxCtx.missing());

      assertEquals(quota, response.getBody());
    }

    @Test
    @DisplayName("Given nothing to show should answer a JSON null")
    void given_nothingToShow_should_answerNull() {
      when(config.isConfigured()).thenReturn(true);
      when(client.getChatQuota())
          .thenReturn(new XtmOneClient.RelayedResponse(200, NullNode.getInstance()));

      ResponseEntity<Object> response = api.getQuota(TxCtx.missing());

      assertEquals(NullNode.getInstance(), response.getBody());
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

    @ParameterizedTest
    @MethodSource("invalidPaths")
    @DisplayName("Given a malformed conversation or message id should refuse without forwarding")
    void given_malformedIds_should_refuse(String conversationId, String messageId) {
      when(config.isConfigured()).thenReturn(true);

      ResponseEntity<Object> post =
          api.submitMessageFeedback(
              TxCtx.missing(), conversationId, messageId, Map.of("rating", "positive"));
      ResponseEntity<Object> delete =
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

      ResponseEntity<Object> response =
          api.submitMessageFeedback(TxCtx.missing(), CONVERSATION_ID, MESSAGE_ID, body);

      assertEquals(400, response.getStatusCode().value());
      verifyNoInteractions(client);
    }

    @Test
    @DisplayName("Given a valid rating should forward it and relay the stored rating")
    void given_validRating_should_forward() {
      when(config.isConfigured()).thenReturn(true);
      JsonNode stored =
          JsonNodeFactory.instance
              .objectNode()
              .put("rating", "negative")
              .put("comment", "Wrong CVE");
      when(client.submitMessageFeedback(CONVERSATION_ID, MESSAGE_ID, "negative", "Wrong CVE"))
          .thenReturn(new XtmOneClient.RelayedResponse(200, stored));

      ResponseEntity<Object> response =
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
      when(client.submitMessageFeedback(any(), any(), any(), any())).thenReturn(OK_EMPTY);

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
      when(client.submitMessageFeedback(any(), any(), any(), any())).thenReturn(OK_EMPTY);

      ResponseEntity<Object> response =
          api.submitMessageFeedback(
              TxCtx.missing(),
              CONVERSATION_ID,
              MESSAGE_ID,
              Map.of("rating", "positive", "comment", comment));

      assertEquals(200, response.getStatusCode().value());
      verify(client).submitMessageFeedback(CONVERSATION_ID, MESSAGE_ID, "positive", comment);
    }

    @Test
    @DisplayName("Given valid ids should retract the rating and answer 204")
    void given_validIds_should_retractAndAnswerNoContent() {
      when(config.isConfigured()).thenReturn(true);
      when(client.retractMessageFeedback(CONVERSATION_ID, MESSAGE_ID))
          .thenReturn(new XtmOneClient.RelayedResponse(204, null));

      ResponseEntity<Object> response =
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
      JsonNode created = JsonNodeFactory.instance.objectNode().put("conversation_id", "c-1");
      when(client.createChatSession(body))
          .thenReturn(new XtmOneClient.RelayedResponse(200, created));

      ResponseEntity<Object> response = api.createSession(TxCtx.missing(), body);

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
          "deleteWorkspace"
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
