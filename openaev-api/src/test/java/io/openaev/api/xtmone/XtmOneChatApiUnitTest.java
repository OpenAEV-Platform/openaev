package io.openaev.api.xtmone;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TxCtx;
import io.openaev.telemetry.metric_collectors.AiMetricCollector;
import io.openaev.xtmone.XtmOneClient;
import io.openaev.xtmone.XtmOneConfig;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Unit test for the {@code /api/xtmone/chat/messages} proxy forwarding contract. Executes the
 * returned {@link StreamingResponseBody} so the controller body actually runs and we can verify the
 * arguments handed to {@link XtmOneClient#streamChatMessage}. Pure POJO (no Spring / async
 * dispatch) to keep the contract check fast and deterministic.
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
}
