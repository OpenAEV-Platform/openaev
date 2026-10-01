package io.openaev.xtmone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.jsonwebtoken.Jwts;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.service.xtm_auth.XtmAuthKeyService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Client tests")
class XtmOneClientTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Mock private HttpClientFactory httpClientFactory;
  @Mock private XtmOneConfig config;
  @Mock private ObjectMapper objectMapper;
  @Mock private CloseableHttpClient httpClient;

  @Spy @InjectMocks private XtmOneClient xtmOneClient;

  private void configureClientCommon() {
    when(config.isConfigured()).thenReturn(true);
    when(config.getUrl()).thenReturn("http://localhost:8080");
    when(httpClientFactory.httpClientNoRetry()).thenReturn(httpClient);
    doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
  }

  /** {@link #configureClientCommon} for a test that sends through several kinds of client. */
  private void configureClientLeniently() throws Exception {
    lenient().when(config.isConfigured()).thenReturn(true);
    lenient().when(config.getUrl()).thenReturn("http://localhost:8080");
    lenient().when(httpClientFactory.httpClientNoRetry()).thenReturn(httpClient);
    lenient().when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    lenient().doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
    lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
  }

  /** An XTM One answer: a status and a body (the JSON the mocked mapper then reads is stubbed). */
  private static ClassicHttpResponse answer(int statusCode, String body) throws Exception {
    ClassicHttpResponse httpResponse =
        mock(ClassicHttpResponse.class, withSettings().strictness(Strictness.LENIENT));
    when(httpResponse.getCode()).thenReturn(statusCode);
    HttpEntity entity = mock(HttpEntity.class, withSettings().strictness(Strictness.LENIENT));
    when(entity.getContent())
        .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    when(entity.getContentLength()).thenReturn((long) body.length());
    when(httpResponse.getEntity()).thenReturn(entity);
    return httpResponse;
  }

  /**
   * Stubs the HTTP exchange with the given status and a JSON body, capturing the request. The
   * response mocks are lenient because some handler paths (e.g. DELETE 204) never read the entity.
   */
  private ArgumentCaptor<Object> mockExchange(int statusCode) throws Exception {
    ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
    when(httpClient.execute(
            (ClassicHttpRequest) requestCaptor.capture(), any(HttpClientResponseHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpClientResponseHandler<?> handler = invocation.getArgument(1);
              return handler.handleResponse(answer(statusCode, "{}"));
            });
    return requestCaptor;
  }

  /** Every chat call relaying XTM One's answer, by name. */
  static Stream<Arguments> relayingCalls() {
    MockMultipartFile file = new MockMultipartFile("file", "iocs.csv", "text/csv", new byte[] {1});
    return Stream.of(
        relayingCall("createChatSession", c -> c.createChatSession(Map.of("agent_slug", "a"))),
        relayingCall("listChatSessions", XtmOneClient::listChatSessions),
        relayingCall("deleteChatSession", c -> c.deleteChatSession("conv-1")),
        relayingCall("updateChatSession", c -> c.updateChatSession("conv-1", Map.of("title", "t"))),
        relayingCall("listChatWorkspaces", XtmOneClient::listChatWorkspaces),
        relayingCall("createChatWorkspace", c -> c.createChatWorkspace(Map.of("name", "n"))),
        relayingCall(
            "updateChatWorkspace", c -> c.updateChatWorkspace("ws-1", Map.of("name", "n"))),
        relayingCall("deleteChatWorkspace", c -> c.deleteChatWorkspace("ws-1")),
        relayingCall("steerChatMessage", c -> c.steerChatMessage("hello", "conv-1")),
        relayingCall("approveToolCalls", c -> c.approveToolCalls("conv-1", List.of())),
        relayingCall("getPendingApprovals", c -> c.getPendingApprovals("conv-1")),
        relayingCall("getChatPrompts", XtmOneClient::getChatPrompts),
        relayingCall("getChatQuota", XtmOneClient::getChatQuota),
        relayingCall(
            "submitMessageFeedback",
            c -> c.submitMessageFeedback("conv-1", "msg-1", "positive", null)),
        relayingCall("retractMessageFeedback", c -> c.retractMessageFeedback("conv-1", "msg-1")),
        relayingCall("uploadChatFile", c -> c.uploadChatFile("conv-1", file)));
  }

  private static Arguments relayingCall(
      String name, Function<XtmOneClient, XtmOneClient.RelayedResponse> call) {
    return Arguments.of(Named.of(name, call));
  }

  /** Every chat call that relays only XTM One's refusal, by name. */
  static Stream<Arguments> refusingCalls() {
    return Stream.of(
        refusingCall("listChatAgents", c -> c.listChatAgents("global.assistant")),
        refusingCall("downloadChatFile", c -> c.downloadChatFile("file-1")),
        refusingCall(
            "streamChatMessage", c -> c.streamChatMessage("hello", null, null, stream -> {})));
  }

  private static Arguments refusingCall(String name, Consumer<XtmOneClient> call) {
    return Arguments.of(Named.of(name, call));
  }

  @Nested
  @DisplayName("Relayed answers")
  class RelayedAnswers {

    @Test
    @DisplayName("Given a success with a JSON body should keep its status and body")
    void given_successWithBody_should_keepStatusAndBody() throws Exception {
      ObjectNode payload = JsonNodeFactory.instance.objectNode().put("id", "ws-1");
      when(objectMapper.readTree(anyString())).thenReturn(payload);

      XtmOneClient.RelayedResponse result = xtmOneClient.relayed(answer(201, "{}"), "testing");

      assertEquals(new XtmOneClient.RelayedResponse(201, payload), result);
    }

    @ParameterizedTest(name = "Given an HTTP {0} without a readable body should keep the status")
    @MethodSource("successStatuses")
    @DisplayName("Given a success without a readable body should keep it with an empty object")
    void given_successWithoutReadableBody_should_relayEmptyObject(int code, String body)
        throws Exception {
      lenient()
          .when(objectMapper.readTree(anyString()))
          .thenThrow(new JsonParseException(null, "not JSON"));

      XtmOneClient.RelayedResponse result = xtmOneClient.relayed(answer(code, body), "testing");

      assertEquals(code, result.status());
      assertEquals(JsonNodeFactory.instance.objectNode(), result.body());
    }

    static Stream<Arguments> successStatuses() {
      return Stream.of(
          Arguments.of(200, ""), Arguments.of(201, "   "), Arguments.of(200, "<html>ok</html>"));
    }

    @Test
    @DisplayName("Given a 204 should relay it without a body")
    void given_noContent_should_relayWithoutBody() throws Exception {
      XtmOneClient.RelayedResponse result = xtmOneClient.relayed(answer(204, ""), "testing");

      assertEquals(new XtmOneClient.RelayedResponse(204, null), result);
    }

    @ParameterizedTest(name = "Given XTM One answers {0} should relay {1}")
    @MethodSource("refusalStatuses")
    @DisplayName("Given a refusal should map its status")
    void given_refusal_should_mapStatus(int code, int expected) throws Exception {
      XtmOneClient.RelayedResponse result = xtmOneClient.relayed(answer(code, "{}"), "testing");

      assertEquals(expected, result.status());
      assertEquals("[XTM One] HTTP " + code, result.detailText());
      assertEquals(1, result.body().size());
    }

    static Stream<Arguments> refusalStatuses() {
      return Stream.of(
          Arguments.of(401, 422),
          Arguments.of(403, 403),
          Arguments.of(404, 404),
          Arguments.of(409, 409),
          Arguments.of(429, 429),
          Arguments.of(500, 500),
          Arguments.of(503, 503),
          Arguments.of(302, 502),
          Arguments.of(101, 502));
    }

    @Test
    @DisplayName("Given a refusal with a detail should keep only the detail, as XTM One wrote it")
    void given_refusalWithDetail_should_keepOnlyTheDetail() throws Exception {
      ObjectNode refusal = JsonNodeFactory.instance.objectNode();
      refusal.putArray("detail").addObject().put("msg", "field required");
      refusal.put("trace", "internal");
      when(objectMapper.readTree(anyString())).thenReturn(refusal);

      XtmOneClient.RelayedResponse result = xtmOneClient.relayed(answer(400, "{}"), "testing");

      assertEquals(400, result.status());
      assertEquals(refusal.get("detail"), result.body().get("detail"));
      assertFalse(result.body().has("trace"));
      assertEquals("[{\"msg\":\"field required\"}]", result.detailText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.xtmone.XtmOneClientTest#relayingCalls")
    @DisplayName("Given XTM One rejects OpenAEV's credentials, every relaying call answers 422")
    void given_rejectedCredentials_should_relayUnprocessable(
        Function<XtmOneClient, XtmOneClient.RelayedResponse> call) throws Exception {
      configureClientLeniently();
      mockExchange(401);

      XtmOneClient.RelayedResponse result = call.apply(xtmOneClient);

      assertEquals(422, result.status());
      assertEquals("[XTM One] HTTP 401", result.detailText());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.xtmone.XtmOneClientTest#refusingCalls")
    @DisplayName("Given XTM One rejects OpenAEV's credentials, every other call refuses with 422")
    void given_rejectedCredentials_should_refuseUnprocessable(Consumer<XtmOneClient> call)
        throws Exception {
      configureClientLeniently();
      mockExchange(401);

      XtmOneUpstreamException ex =
          assertThrows(XtmOneUpstreamException.class, () -> call.accept(xtmOneClient));

      assertEquals(422, ex.getStatusCode().value());
      assertEquals("[XTM One] HTTP 401", ex.getReason());
      assertEquals(422, ex.getResponse().status());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.xtmone.XtmOneClientTest#relayingCalls")
    @DisplayName("Given XTM One not configured, every relaying call refuses without calling it")
    void given_notConfigured_relayingCall_should_refuse(
        Function<XtmOneClient, XtmOneClient.RelayedResponse> call) {
      when(config.isConfigured()).thenReturn(false);

      XtmOneNotConfiguredException ex =
          assertThrows(XtmOneNotConfiguredException.class, () -> call.apply(xtmOneClient));

      assertEquals(503, ex.getStatusCode().value());
      assertEquals(XtmOneNotConfiguredException.MESSAGE, ex.getReason());
      verifyNoInteractions(httpClientFactory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.xtmone.XtmOneClientTest#refusingCalls")
    @DisplayName("Given XTM One not configured, every other call refuses without calling it")
    void given_notConfigured_otherCall_should_refuse(Consumer<XtmOneClient> call) {
      when(config.isConfigured()).thenReturn(false);

      XtmOneNotConfiguredException ex =
          assertThrows(XtmOneNotConfiguredException.class, () -> call.accept(xtmOneClient));

      assertEquals(XtmOneNotConfiguredException.MESSAGE, ex.getReason());
      verifyNoInteractions(httpClientFactory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.xtmone.XtmOneClientTest#relayingCalls")
    @DisplayName("Given XTM One cannot be reached, every relaying call fails with a 500")
    void given_connectionFails_should_throwInternalServerError(
        Function<XtmOneClient, XtmOneClient.RelayedResponse> call) throws Exception {
      configureClientLeniently();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> call.apply(xtmOneClient));

      assertEquals(500, ex.getStatusCode().value());
    }
  }

  @Nested
  @DisplayName("listChatAgents")
  class ListChatAgents {

    @Test
    @DisplayName("Given XTM One returns 200 with agents should return the valid ones")
    void given_returns200WithAgents_should_returnList() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      when(objectMapper.readTree(anyString()))
          .thenReturn(
              JSON.readTree(
                  "[{\"intent\":\"global.assistant\",\"agents\":["
                      + "{\"agent_id\":\"agent-1\",\"agent_name\":\"Agent 1\","
                      + "\"agent_slug\":\"agent-1\",\"agent_description\":\"Agent 1 description\"},"
                      + "{\"agent_id\":\"agent-2\",\"agent_name\":\"No slug\"}]},"
                      + "{\"intent\":\"other\"}]"));
      when(objectMapper.convertValue(any(JsonNode.class), eq(ChatbotAgentOutput.class)))
          .thenAnswer(
              invocation -> JSON.convertValue(invocation.getArgument(0), ChatbotAgentOutput.class));

      // -- ACT --
      List<ChatbotAgentOutput> result = xtmOneClient.listChatAgents("intent");

      // -- ASSERT --
      assertEquals(1, result.size());
      assertEquals("agent-1", result.getFirst().id());
      assertEquals("agent-1", result.getFirst().slug());
    }

    @Test
    @DisplayName("Given XTM One returns 200 with an empty catalog should throw NOT_FOUND")
    void given_returns200Empty_should_throwNotFound() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      when(objectMapper.readTree(anyString())).thenReturn(JsonNodeFactory.instance.arrayNode());

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 without a readable body should throw NOT_FOUND")
    void given_returns200Unreadable_should_throwNotFound() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(404, ex.getStatusCode().value());
    }

    static Stream<Arguments> errorStatusCodes() {
      return Stream.of(
          Arguments.of(401, 422),
          Arguments.of(403, 403),
          Arguments.of(404, 404),
          Arguments.of(503, 503),
          Arguments.of(502, 502));
    }

    @ParameterizedTest(name = "Given XTM One returns {0} should refuse with {1}")
    @MethodSource("errorStatusCodes")
    void given_errorStatus_should_refuseWithTheRelayedStatus(int remoteStatus, int expectedStatus)
        throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(remoteStatus);

      // -- ACT & ASSERT --
      XtmOneUpstreamException ex =
          assertThrows(XtmOneUpstreamException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(expectedStatus, ex.getStatusCode().value());
      assertEquals(expectedStatus, ex.getResponse().status());
    }

    @Test
    @DisplayName("Given connection fails should throw INTERNAL_SERVER_ERROR")
    void given_connectionFails_should_throwInternalServerError() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(500, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given additional attack agents and XTM One not configured should list none")
    void given_notConfigured_additionalAttackAgents_should_returnEmpty() {
      when(config.isConfigured()).thenReturn(false);

      assertEquals(List.of(), xtmOneClient.listAdditionalAttackAgents());
    }

    @Test
    @DisplayName("Given additional attack agents and XTM One answers 404 should list none")
    void given_upstreamNotFound_additionalAttackAgents_should_returnEmpty() throws Exception {
      configureClientCommon();
      mockExchange(404);

      assertEquals(List.of(), xtmOneClient.listAdditionalAttackAgents());
    }
  }

  @Nested
  @DisplayName("streamChatMessage")
  class StreamChatMessage {

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> stubRequestBodyCapture() throws Exception {
      when(config.isConfigured()).thenReturn(true);
      when(config.getUrl()).thenReturn("http://localhost:8080");
      when(httpClientFactory.httpClientNoRetry()).thenReturn(httpClient);
      doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      return bodyCaptor;
    }

    @Test
    @DisplayName("Given a non-empty context should include it in the upstream request body")
    void given_context_should_includeItInBody() throws Exception {
      // -- ARRANGE --
      ArgumentCaptor<Map<String, Object>> bodyCaptor = stubRequestBodyCapture();
      Map<String, Object> context = Map.of("url", "/dashboard/reports/1");

      // -- ACT --
      xtmOneClient.streamChatMessage("hello", "conv-1", "agent-1", context, stream -> {});

      // -- ASSERT --
      Map<String, Object> body = bodyCaptor.getValue();
      assertEquals("hello", body.get("content"));
      assertEquals("conv-1", body.get("conversation_id"));
      assertEquals("agent-1", body.get("agent_slug"));
      assertEquals(context, body.get("context"));
    }

    @Test
    @DisplayName("Given a null context should omit it from the upstream request body")
    void given_nullContext_should_omitFromBody() throws Exception {
      // -- ARRANGE --
      ArgumentCaptor<Map<String, Object>> bodyCaptor = stubRequestBodyCapture();

      // -- ACT --
      xtmOneClient.streamChatMessage("hello", null, "agent-1", null, stream -> {});

      // -- ASSERT --
      Map<String, Object> body = bodyCaptor.getValue();
      assertEquals("hello", body.get("content"));
      assertFalse(body.containsKey("context"));
      assertFalse(body.containsKey("conversation_id"));
    }

    @Test
    @DisplayName("Given an empty context should omit it from the upstream request body")
    void given_emptyContext_should_omitFromBody() throws Exception {
      // -- ARRANGE --
      ArgumentCaptor<Map<String, Object>> bodyCaptor = stubRequestBodyCapture();

      // -- ACT --
      xtmOneClient.streamChatMessage("hello", null, "agent-1", Map.of(), stream -> {});

      // -- ASSERT --
      assertFalse(bodyCaptor.getValue().containsKey("context"));
    }

    @Test
    @DisplayName("Given a context via the 4-arg overload should default to null context (omitted)")
    void given_legacyOverload_should_omitContext() throws Exception {
      // -- ARRANGE --
      ArgumentCaptor<Map<String, Object>> bodyCaptor = stubRequestBodyCapture();

      // -- ACT --
      xtmOneClient.streamChatMessage("hello", null, "agent-1", stream -> {});

      // -- ASSERT --
      assertFalse(bodyCaptor.getValue().containsKey("context"));
    }

    @Test
    @DisplayName(
        "Given XTM One answers 429 with a detail should refuse with it, as the quota label")
    void given_quotaExceeded_should_refuseWithTheDetail() throws Exception {
      // -- ARRANGE --
      stubRequestBodyCapture();
      mockExchange(429);
      when(objectMapper.readTree(anyString()))
          .thenReturn(JsonNodeFactory.instance.objectNode().put("detail", "Daily quota reached"));

      // -- ACT & ASSERT --
      XtmOneUpstreamException ex =
          assertThrows(
              XtmOneUpstreamException.class,
              () -> xtmOneClient.streamChatMessage("hello", null, null, stream -> {}));
      assertEquals(429, ex.getStatusCode().value());
      assertEquals("Daily quota reached", ex.getReason());
    }
  }

  @Nested
  @DisplayName("Conversations")
  class Conversations {

    @Test
    @DisplayName("Given XTM One lists the conversations should relay its payload")
    void given_conversations_should_relayPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      JsonNode payload = JSON.readTree("{\"conversations\":[{\"workspace_id\":\"ws-1\"}]}");
      when(objectMapper.readTree(anyString())).thenReturn(payload);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatSessions();

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, payload), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("GET", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/sessions"));
      assertEquals("Bearer fake-jwt", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("Given XTM One fails to list the conversations should relay its refusal")
    void given_listFailure_should_relayRefusal() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(500);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatSessions();

      // -- ASSERT --
      assertEquals(500, result.status());
      assertEquals("[XTM One] HTTP 500", result.detailText());
    }

    @Test
    @DisplayName("Given a panel body should post it as-is, workspace and unknown fields included")
    @SuppressWarnings("unchecked")
    void given_panelBody_should_postItAsIs() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      JsonNode payload =
          JSON.readTree("{\"conversation_id\":\"conv-1\",\"workspace_id\":\"ws-1\"}");
      when(objectMapper.readTree(anyString())).thenReturn(payload);
      Map<String, Object> body =
          Map.of("agent_slug", "ariane", "workspace_id", "ws-1", "future_field", true);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.createChatSession(body);

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, payload), result);
      assertEquals(body, bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("POST", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/sessions"));
    }

    @Test
    @DisplayName("Given XTM One archives the conversation should relay a 204 without a body")
    void given_deletedConversation_should_relayNoContent() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.deleteChatSession("conv-1");

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(204, null), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("DELETE", request.getMethod());
    }

    @Test
    @DisplayName("Given a conversation id with path characters should URL-encode the segment")
    void given_pathCharacters_should_urlEncodeSegment() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      xtmOneClient.deleteChatSession("abc/.. def");

      // -- ASSERT --
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(
          request.getUri().toString().endsWith("/api/v1/platform/chat/sessions/abc%2F..%20def"));
    }

    @Test
    @DisplayName("Given a conversation unfiled should PATCH an explicit null workspace id")
    @SuppressWarnings("unchecked")
    void given_unfiledConversation_should_patchExplicitNull() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      when(objectMapper.readTree(anyString())).thenReturn(JsonNodeFactory.instance.objectNode());
      Map<String, Object> changes = new HashMap<>();
      changes.put("workspace_id", null);

      // -- ACT --
      xtmOneClient.updateChatSession("conv-1", changes);

      // -- ASSERT --
      assertTrue(bodyCaptor.getValue().containsKey("workspace_id"));
      assertNull(bodyCaptor.getValue().get("workspace_id"));
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("PATCH", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/sessions/conv-1"));
    }
  }

  @Nested
  @DisplayName("Workspaces")
  class Workspaces {

    @Test
    @DisplayName("Given XTM One lists workspaces should relay the payload read as the current user")
    void given_workspaces_should_relayPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ObjectNode workspaces = JsonNodeFactory.instance.objectNode();
      workspaces.set("workspaces", workspaceList());
      when(objectMapper.readTree(anyString())).thenReturn(workspaces);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatWorkspaces();

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, workspaces), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("GET", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/workspaces"));
      assertEquals("Bearer fake-jwt", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("Given a new workspace should post its fields and relay the 201")
    @SuppressWarnings("unchecked")
    void given_newWorkspace_should_postFieldsAndRelayCreated() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(201);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      JsonNode created = JsonNodeFactory.instance.objectNode().put("id", "ws-1");
      when(objectMapper.readTree(anyString())).thenReturn(created);

      // -- ACT --
      XtmOneClient.RelayedResponse result =
          xtmOneClient.createChatWorkspace(Map.of("name", "Red team"));

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(201, created), result);
      assertEquals(Map.of("name", "Red team"), bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("POST", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/workspaces"));
    }

    @Test
    @DisplayName("Given a workspace id with path characters should PATCH the encoded segment")
    void given_pathCharacters_should_patchEncodedSegment() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      when(objectMapper.readTree(anyString())).thenReturn(JsonNodeFactory.instance.objectNode());

      // -- ACT --
      xtmOneClient.updateChatWorkspace("ws/.. 1", Map.of("name", "Blue team"));

      // -- ASSERT --
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("PATCH", request.getMethod());
      assertTrue(
          request.getUri().toString().endsWith("/api/v1/platform/chat/workspaces/ws%2F..%201"));
    }

    @Test
    @DisplayName("Given XTM One deletes the workspace should relay a 204 without a body")
    void given_deletedWorkspace_should_relayNoContent() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.deleteChatWorkspace("ws-1");

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(204, null), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("DELETE", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/workspaces/ws-1"));
    }

    @Test
    @DisplayName("Given XTM One refuses with a detail should relay the status and only the detail")
    void given_refusalWithDetail_should_relayStatusAndDetail() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(409);
      ObjectNode refusal = JsonNodeFactory.instance.objectNode();
      refusal.put("detail", "Default workspace");
      refusal.put("trace", "internal");
      when(objectMapper.readTree(anyString())).thenReturn(refusal);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.deleteChatWorkspace("ws-1");

      // -- ASSERT --
      assertEquals(409, result.status());
      assertEquals("Default workspace", result.body().get("detail").asText());
      assertFalse(result.body().has("trace"));
    }

    @Test
    @DisplayName("Given XTM One answers 200 with no JSON body should relay the 200, empty")
    void given_unreadableSuccess_should_relayEmptyObject() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatWorkspaces();

      // -- ASSERT --
      assertEquals(
          new XtmOneClient.RelayedResponse(200, JsonNodeFactory.instance.objectNode()), result);
    }

    private JsonNode workspaceList() {
      ObjectNode workspace = JsonNodeFactory.instance.objectNode();
      workspace.put("id", "ws-1");
      workspace.put("is_default", true);
      workspace.put("is_own", true);
      workspace.put("can_manage", true);
      return JsonNodeFactory.instance.arrayNode().add(workspace);
    }
  }

  @Nested
  @DisplayName("Steering and tool approval")
  class SteeringAndApproval {

    @Test
    @DisplayName("Given XTM One accepts the steering should send the body and relay the payload")
    @SuppressWarnings("unchecked")
    void given_steeringAccepted_should_relayPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      JsonNode payload = JsonNodeFactory.instance.objectNode().put("status", "queued");
      when(objectMapper.readTree(anyString())).thenReturn(payload);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.steerChatMessage("hello", "conv-1");

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, payload), result);
      assertEquals(Map.of("content", "hello", "conversation_id", "conv-1"), bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/messages/steer"));
    }

    @ParameterizedTest(name = "Given XTM One answers {0} to a steering should relay {1}")
    @MethodSource("steeringStatuses")
    void given_steeringRefused_should_relayStatus(int remoteStatus, int expectedStatus)
        throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(remoteStatus);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      // -- ACT & ASSERT --
      assertEquals(expectedStatus, xtmOneClient.steerChatMessage("hello", "conv-1").status());
    }

    static Stream<Arguments> steeringStatuses() {
      return Stream.of(
          Arguments.of(409, 409),
          Arguments.of(429, 429),
          Arguments.of(404, 404),
          Arguments.of(503, 503),
          Arguments.of(401, 422));
    }

    @Test
    @DisplayName("Given tool call decisions should post them for the conversation")
    @SuppressWarnings("unchecked")
    void given_decisions_should_postThem() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      List<Map<String, Object>> decisions =
          List.of(Map.of("tool_call_id", "toolu_1", "decision", "approve"));

      // -- ACT --
      xtmOneClient.approveToolCalls("conv-1", decisions);

      // -- ASSERT --
      assertEquals(
          Map.of("conversation_id", "conv-1", "decisions", decisions), bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/messages/approve"));
    }

    @Test
    @DisplayName("Given a conversation should read its pending approvals")
    void given_conversation_should_readPendingApprovals() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);

      // -- ACT --
      xtmOneClient.getPendingApprovals("conv-1");

      // -- ASSERT --
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("GET", request.getMethod());
      assertTrue(
          request
              .getUri()
              .toString()
              .endsWith("/api/v1/platform/chat/conversations/conv-1/pending-approvals"));
    }
  }

  @Nested
  @DisplayName("Prompts and quota")
  class PromptsAndQuota {

    @Test
    @DisplayName("Given XTM One returns prompts should relay them read as the current user")
    void given_prompts_should_relayThem() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      JsonNode payload = JSON.readTree("{\"prompts\":[{\"id\":\"p-1\"}]}");
      when(objectMapper.readTree(anyString())).thenReturn(payload);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.getChatPrompts();

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, payload), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("GET", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/prompts"));
      assertEquals("Bearer fake-jwt", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("Given XTM One returns a quota object should relay it")
    void given_quotaObject_should_relayIt() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      JsonNode quota = JsonNodeFactory.instance.objectNode().put("used", 3).put("limit", 10);
      when(objectMapper.readTree(anyString())).thenReturn(quota);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.getChatQuota();

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, quota), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/quota"));
    }

    @ParameterizedTest(name = "Given XTM One returns {0} should answer a 200 JSON null")
    @MethodSource("nothingToShow")
    void given_nothingToShow_should_answerNull(String description, JsonNode quota)
        throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      when(objectMapper.readTree(anyString())).thenReturn(quota);

      // -- ACT & ASSERT --
      assertEquals(
          new XtmOneClient.RelayedResponse(200, NullNode.getInstance()),
          xtmOneClient.getChatQuota());
    }

    static Stream<Arguments> nothingToShow() {
      return Stream.of(
          Arguments.of("null", NullNode.getInstance()),
          Arguments.of("an array", JsonNodeFactory.instance.arrayNode()),
          Arguments.of("an empty object", JsonNodeFactory.instance.objectNode()),
          Arguments.of("no readable body", null));
    }

    @Test
    @DisplayName("Given XTM One returns 503 should relay it")
    void given_upstreamUnavailable_should_relayIt() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(503);

      // -- ACT & ASSERT --
      assertEquals(503, xtmOneClient.getChatQuota().status());
    }
  }

  @Nested
  @DisplayName("Message feedback")
  class MessageFeedback {

    @Test
    @DisplayName("Given XTM One returns 200 should post the rating and relay the stored one")
    @SuppressWarnings("unchecked")
    void given_returns200_should_postRatingAndRelayPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      JsonNode payload = JSON.readTree("{\"rating\":\"negative\",\"comment\":\"Wrong CVE\"}");
      when(objectMapper.readTree(anyString())).thenReturn(payload);

      // -- ACT --
      XtmOneClient.RelayedResponse result =
          xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "negative", "Wrong CVE");

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, payload), result);
      assertEquals(Map.of("rating", "negative", "comment", "Wrong CVE"), bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("POST", request.getMethod());
      assertTrue(
          request
              .getUri()
              .toString()
              .endsWith("/api/v1/platform/chat/conversations/conv-1/messages/msg-1/feedback"));
    }

    @Test
    @DisplayName("Given no comment should send an explicit null comment")
    @SuppressWarnings("unchecked")
    void given_noComment_should_sendNullComment() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");

      // -- ACT --
      xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "positive", null);

      // -- ASSERT --
      assertTrue(bodyCaptor.getValue().containsKey("comment"));
      assertNull(bodyCaptor.getValue().get("comment"));
    }

    @Test
    @DisplayName("Given XTM One returns 404 (message not readable) should relay 404")
    void given_upstreamNotFound_should_relayIt() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      // -- ACT & ASSERT --
      assertEquals(
          404, xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "positive", null).status());
    }

    @Test
    @DisplayName("Given XTM One returns 204 should DELETE the message feedback and relay it")
    void given_returns204_should_deleteFeedback() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.retractMessageFeedback("conv-1", "msg-1");

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(204, null), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("DELETE", request.getMethod());
      assertTrue(
          request
              .getUri()
              .toString()
              .endsWith("/api/v1/platform/chat/conversations/conv-1/messages/msg-1/feedback"));
      assertEquals("Bearer fake-jwt", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("Given ids with path characters should URL-encode each segment")
    void given_pathCharacters_should_urlEncodeSegments() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      xtmOneClient.retractMessageFeedback("a/..", "b c");

      // -- ASSERT --
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(
          request
              .getUri()
              .toString()
              .endsWith("/api/v1/platform/chat/conversations/a%2F../messages/b%20c/feedback"));
    }
  }

  @Nested
  @DisplayName("Files")
  class Files {

    @Test
    @DisplayName("Given XTM One stores an uploaded file should relay its id")
    void given_storedFile_should_relayItsId() throws Exception {
      // -- ARRANGE --
      when(config.isConfigured()).thenReturn(true);
      when(config.getUrl()).thenReturn("http://localhost:8080");
      when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
      doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      JsonNode stored = JsonNodeFactory.instance.objectNode().put("file_id", "file-1");
      when(objectMapper.readTree(anyString())).thenReturn(stored);

      // -- ACT --
      XtmOneClient.RelayedResponse result =
          xtmOneClient.uploadChatFile(
              "conv-1", new MockMultipartFile("file", "iocs.csv", "text/csv", new byte[] {1}));

      // -- ASSERT --
      assertEquals(new XtmOneClient.RelayedResponse(200, stored), result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(
          request
              .getUri()
              .toString()
              .endsWith("/api/v1/chat/conversations/conv-1/upload?create_message=false"));
    }

    @Test
    @DisplayName("Given XTM One refuses a download should refuse with its status and detail")
    void given_refusedDownload_should_refuseWithStatusAndDetail() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);
      when(objectMapper.readTree(anyString()))
          .thenReturn(JsonNodeFactory.instance.objectNode().put("detail", "File not found"));

      // -- ACT & ASSERT --
      XtmOneUpstreamException ex =
          assertThrows(XtmOneUpstreamException.class, () -> xtmOneClient.downloadChatFile("f-1"));
      assertEquals(404, ex.getStatusCode().value());
      assertEquals("File not found", ex.getReason());
    }
  }

  @Nested
  @DisplayName("issueAuthenticationJwt")
  class IssueAuthenticationJwt {

    private static final String INTERNAL_URL = "http://xtm-one:4000";
    private static final String PUBLIC_ISSUER = "http://localhost:8090";

    /** The audience of a token OpenAEV sends to XTM One reached on its internal URL. */
    @SuppressWarnings("unchecked")
    private Set<String> audienceWhenTheMetadataAnswers(Object answer) throws Exception {
      XtmOneConfig xtmOneConfig = new XtmOneConfig();
      xtmOneConfig.setUrl(INTERNAL_URL);
      OpenAEVConfig openAEVConfig = new OpenAEVConfig();
      openAEVConfig.setBaseUrl("http://localhost:8080");
      KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
      XtmAuthKeyService keyService = mock(XtmAuthKeyService.class);
      when(keyService.getKid()).thenReturn("openaev");
      when(keyService.getKeyPair()).thenReturn(keyPair);
      when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
      var metadata =
          when(
              httpClient.execute(
                  (ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()));
      if (answer instanceof Exception failure) {
        metadata.thenThrow(failure);
      } else {
        metadata.thenReturn((String) answer);
      }
      ObjectMapper mapper = new ObjectMapper();
      XtmOneClient client =
          new XtmOneClient(
              xtmOneConfig,
              mapper,
              keyService,
              openAEVConfig,
              httpClientFactory,
              null,
              new XtmOneIdentity(xtmOneConfig, httpClientFactory, mapper));

      String jwt = client.issueAuthenticationJwt("user-1", "Analyst", "analyst@example.com");

      return Jwts.parser()
          .verifyWith(keyPair.getPublic())
          .build()
          .parseSignedClaims(jwt)
          .getPayload()
          .getAudience();
    }

    @Test
    @DisplayName("Given XTM One publishes its identity should address the token to it")
    void given_publishedIdentity_should_addressTheTokenToIt() throws Exception {
      assertEquals(
          Set.of(PUBLIC_ISSUER),
          audienceWhenTheMetadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "/\"}"));
    }

    @Test
    @DisplayName("Given XTM One publishes no identity should address the token to its URL")
    void given_noPublishedIdentity_should_addressTheTokenToTheConfiguredUrl() throws Exception {
      assertEquals(
          Set.of(INTERNAL_URL),
          audienceWhenTheMetadataAnswers(new XtmOneIdentity.IdentityNotPublished()));
    }
  }
}
