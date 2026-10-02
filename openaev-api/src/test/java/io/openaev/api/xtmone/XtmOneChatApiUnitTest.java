package io.openaev.api.xtmone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import io.openaev.context.TxCtx;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Unit test for the {@code /api/xtmone/chat} proxy forwarding contract. For {@code /messages} it
 * executes the returned {@link StreamingResponseBody} so the controller body actually runs and we
 * can verify the arguments handed to {@link XtmOneClient#streamChatMessage}; the prompts, quota and
 * message feedback routes are checked for their validation and what they hand to the client. Pure
 * POJO (no Spring / async dispatch) to keep the contract check fast and deterministic.
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
}
