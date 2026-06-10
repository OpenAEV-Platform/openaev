package io.openaev.xtmone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.xtmone.dto.ChatbotAgentOutput;
import io.openaev.authorisation.HttpClientFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Client tests")
class XtmOneClientTest {

  @Mock private HttpClientFactory httpClientFactory;
  @Mock private XtmOneConfig config;
  @Mock private ObjectMapper objectMapper;
  @Mock private CloseableHttpClient httpClient;

  @Spy @InjectMocks private XtmOneClient xtmOneClient;

  @Nested
  @DisplayName("listChatAgents")
  class ListChatAgents {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      // -- ARRANGE --
      when(config.isConfigured()).thenReturn(false);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 with agents should return the list")
    void given_returns200WithAgents_should_returnList() throws Exception {
      // -- ARRANGE --
      configureClient();
      when(objectMapper.convertValue(any(), eq(ChatbotAgentOutput.class)))
          .thenAnswer(
              invocation -> {
                Object source = invocation.getArgument(0);
                if (!(source instanceof Map<?, ?> map)) {
                  return null;
                }
                String id = map.get("agent_id") != null ? map.get("agent_id").toString() : null;
                String name =
                    map.get("agent_name") != null ? map.get("agent_name").toString() : null;
                String slug =
                    map.get("agent_slug") != null ? map.get("agent_slug").toString() : null;
                String description =
                    map.get("agent_description") != null
                        ? map.get("agent_description").toString()
                        : null;
                return new ChatbotAgentOutput(id, name, slug, description);
              });
      List<Map<String, Object>> catalog =
          List.of(
              Map.of(
                  "intent",
                  "global.assistant",
                  "agents",
                  List.of(
                      Map.of(
                          "agent_id",
                          "agent-1",
                          "agent_name",
                          "Agent 1",
                          "agent_slug",
                          "agent-1",
                          "agent_description",
                          "Agent 1 description"))));
      mockHttpResponse(catalog);

      // -- ACT --
      List<ChatbotAgentOutput> result = xtmOneClient.listChatAgents("intent");

      // -- ASSERT --
      assertEquals(1, result.size());
      assertEquals("agent-1", result.getFirst().id());
    }

    @Test
    @DisplayName("Given XTM One returns 200 with empty list should throw NOT_FOUND")
    void given_returns200Empty_should_throwNotFound() throws Exception {
      // -- ARRANGE --
      configureClient();
      mockHttpResponse(List.of());

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(404, ex.getStatusCode().value());
    }

    static Stream<Arguments> errorStatusCodes() {
      return Stream.of(
          Arguments.of(401, 401, "UNAUTHORIZED"),
          Arguments.of(403, 403, "FORBIDDEN"),
          Arguments.of(503, 503, "SERVICE_UNAVAILABLE"),
          Arguments.of(502, 500, "INTERNAL_SERVER_ERROR (default)"));
    }

    @ParameterizedTest(name = "Given XTM One returns {0} should throw {2}")
    @MethodSource("errorStatusCodes")
    void given_errorStatus_should_throwMatchingException(
        int remoteStatus, int expectedStatus, String description) throws Exception {
      // -- ARRANGE --
      configureClient();
      mockHttpResponseWithStatus(remoteStatus);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(expectedStatus, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 404 should throw NOT_FOUND")
    void given_returns404_should_throwNotFound() throws Exception {
      // -- ARRANGE --
      configureClient();
      mockHttpResponseWithStatus(404);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given connection fails should throw INTERNAL_SERVER_ERROR")
    void given_connectionFails_should_throwInternalServerError() throws Exception {
      // -- ARRANGE --
      configureClient();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatAgents("intent"));
      assertEquals(500, ex.getStatusCode().value());
    }

    private void configureClient() {
      when(config.isConfigured()).thenReturn(true);
      when(config.getUrl()).thenReturn("http://localhost:8080");
      when(httpClientFactory.httpClientNoRetry()).thenReturn(httpClient);
      doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
    }

    @SuppressWarnings("unchecked")
    private void mockHttpResponse(List<Map<String, Object>> responseBody) throws Exception {
      String json = "[]";
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenAnswer(
              invocation -> {
                HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                ClassicHttpResponse httpResponse = mock(ClassicHttpResponse.class);
                when(httpResponse.getCode()).thenReturn(200);
                HttpEntity entity = mock(HttpEntity.class);
                when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
                when(entity.getContentLength()).thenReturn((long) json.length());
                when(httpResponse.getEntity()).thenReturn(entity);
                when(objectMapper.readValue(anyString(), any(Class.class)))
                    .thenReturn(responseBody);
                return handler.handleResponse(httpResponse);
              });
    }

    @SuppressWarnings("unchecked")
    private void mockHttpResponseWithStatus(int statusCode) throws Exception {
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenAnswer(
              invocation -> {
                HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                ClassicHttpResponse httpResponse = mock(ClassicHttpResponse.class);
                when(httpResponse.getCode()).thenReturn(statusCode);
                HttpEntity entity = mock(HttpEntity.class);
                when(entity.getContent())
                    .thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
                when(entity.getContentLength()).thenReturn(2L);
                when(httpResponse.getEntity()).thenReturn(entity);
                return handler.handleResponse(httpResponse);
              });
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
  }

  private void configureClientCommon() {
    when(config.isConfigured()).thenReturn(true);
    when(config.getUrl()).thenReturn("http://localhost:8080");
    when(httpClientFactory.httpClientNoRetry()).thenReturn(httpClient);
    doReturn("fake-jwt").when(xtmOneClient).issueJwtForCurrentUser();
  }

  /**
   * Stubs the HTTP exchange with the given status and a JSON body, capturing the request. The
   * response mocks are lenient because some handler paths (e.g. DELETE 204) never read the entity.
   */
  private ArgumentCaptor<Object> mockExchange(int statusCode) throws Exception {
    ArgumentCaptor<Object> requestCaptor = ArgumentCaptor.forClass(Object.class);
    when(httpClient.execute(
            (org.apache.hc.core5.http.ClassicHttpRequest) requestCaptor.capture(),
            any(HttpClientResponseHandler.class)))
        .thenAnswer(
            invocation -> {
              HttpClientResponseHandler<?> handler = invocation.getArgument(1);
              ClassicHttpResponse httpResponse =
                  mock(
                      ClassicHttpResponse.class,
                      withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
              when(httpResponse.getCode()).thenReturn(statusCode);
              HttpEntity entity =
                  mock(
                      HttpEntity.class,
                      withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
              when(entity.getContent())
                  .thenReturn(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
              when(entity.getContentLength()).thenReturn(2L);
              when(httpResponse.getEntity()).thenReturn(entity);
              return handler.handleResponse(httpResponse);
            });
    return requestCaptor;
  }

  @Nested
  @DisplayName("listChatSessions")
  class ListChatSessions {

    @Test
    @DisplayName("Given not configured should return null")
    void given_notConfigured_should_returnNull() {
      when(config.isConfigured()).thenReturn(false);

      assertNull(xtmOneClient.listChatSessions());
    }

    @Test
    @DisplayName("Given XTM One returns 200 should return the parsed payload")
    @SuppressWarnings("unchecked")
    void given_returns200_should_returnPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      Map<String, Object> payload = Map.of("conversations", List.of());
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(payload);

      // -- ACT & ASSERT --
      assertEquals(payload, xtmOneClient.listChatSessions());
    }

    @Test
    @DisplayName("Given XTM One returns an error status should return null")
    void given_errorStatus_should_returnNull() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(500);

      // -- ACT & ASSERT --
      assertNull(xtmOneClient.listChatSessions());
    }

    @Test
    @DisplayName("Given the connection fails should return null")
    void given_connectionFails_should_returnNull() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      assertNull(xtmOneClient.listChatSessions());
    }
  }

  @Nested
  @DisplayName("deleteChatSession")
  class DeleteChatSession {

    @Test
    @DisplayName("Given not configured should return false")
    void given_notConfigured_should_returnFalse() {
      when(config.isConfigured()).thenReturn(false);

      assertFalse(xtmOneClient.deleteChatSession("conv-1"));
    }

    @Test
    @DisplayName("Given XTM One returns 204 should return true")
    void given_returns204_should_returnTrue() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(204);

      // -- ACT & ASSERT --
      assertTrue(xtmOneClient.deleteChatSession("conv-1"));
    }

    @Test
    @DisplayName("Given XTM One returns an error status should return false")
    void given_errorStatus_should_returnFalse() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);

      // -- ACT & ASSERT --
      assertFalse(xtmOneClient.deleteChatSession("conv-1"));
    }

    @Test
    @DisplayName("Given a conversation id with path characters should URL-encode the segment")
    void given_pathCharacters_should_urlEncodeSegment() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      xtmOneClient.deleteChatSession("abc/../def");

      // -- ASSERT --
      org.apache.hc.core5.http.ClassicHttpRequest request =
          (org.apache.hc.core5.http.ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(
          request.getUri().toString().endsWith("/api/v1/platform/chat/sessions/abc%2F..%2Fdef"));
    }
  }

  @Nested
  @DisplayName("steerChatMessage")
  class SteerChatMessage {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.steerChatMessage("hi", "conv-1"));
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 should return the parsed payload and send the body")
    @SuppressWarnings("unchecked")
    void given_returns200_should_returnPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      Map<String, Object> payload = Map.of("status", "queued");
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(payload);

      // -- ACT & ASSERT --
      assertEquals(payload, xtmOneClient.steerChatMessage("hello", "conv-1"));
      assertEquals("hello", bodyCaptor.getValue().get("content"));
      assertEquals("conv-1", bodyCaptor.getValue().get("conversation_id"));
    }

    static Stream<Arguments> upstreamStatusCodes() {
      return Stream.of(
          Arguments.of(409, 409, "CONFLICT preserved (no run active)"),
          Arguments.of(429, 429, "TOO_MANY_REQUESTS preserved"),
          Arguments.of(404, 404, "NOT_FOUND preserved"),
          Arguments.of(503, 503, "SERVICE_UNAVAILABLE preserved"));
    }

    @ParameterizedTest(name = "Given XTM One returns {0} should propagate {1} ({2})")
    @MethodSource("upstreamStatusCodes")
    void given_upstreamError_should_preserveStatusCode(
        int remoteStatus, int expectedStatus, String description) throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(remoteStatus);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.steerChatMessage("hello", "conv-1"));
      assertEquals(expectedStatus, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given the connection fails should throw INTERNAL_SERVER_ERROR")
    void given_connectionFails_should_throwInternalServerError() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.steerChatMessage("hello", "conv-1"));
      assertEquals(500, ex.getStatusCode().value());
    }
  }
}
