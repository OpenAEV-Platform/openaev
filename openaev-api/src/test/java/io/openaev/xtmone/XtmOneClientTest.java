package io.openaev.xtmone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
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
          Arguments.of(401, 422, "UNPROCESSABLE_ENTITY"),
          Arguments.of(403, 422, "UNPROCESSABLE_ENTITY"),
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
      xtmOneClient.deleteChatSession("abc/.. def");

      // -- ASSERT --
      org.apache.hc.core5.http.ClassicHttpRequest request =
          (org.apache.hc.core5.http.ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(
          request.getUri().toString().endsWith("/api/v1/platform/chat/sessions/abc%2F..%20def"));
    }
  }

  @Nested
  @DisplayName("createChatSession")
  class CreateChatSession {

    @Test
    @DisplayName("Given not configured should return null")
    void given_notConfigured_should_returnNull() {
      when(config.isConfigured()).thenReturn(false);

      assertNull(xtmOneClient.createChatSession(Map.of("agent_slug", "ariane")));
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
      Map<String, Object> payload = Map.of("conversation_id", "conv-1", "workspace_id", "ws-1");
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(payload);
      Map<String, Object> body =
          Map.of("agent_slug", "ariane", "workspace_id", "ws-1", "future_field", true);

      // -- ACT --
      Map<String, Object> result = xtmOneClient.createChatSession(body);

      // -- ASSERT --
      assertEquals(payload, result);
      assertEquals(body, bodyCaptor.getValue());
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("POST", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/sessions"));
    }
  }

  @Nested
  @DisplayName("Workspaces and conversation filing")
  class WorkspacesAndFiling {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatWorkspaces());
      assertEquals(503, ex.getStatusCode().value());
    }

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

    @ParameterizedTest(name = "Given XTM One answers {0} without a detail should relay {1}")
    @MethodSource("statusesWithoutDetail")
    void given_statusWithoutDetail_should_relayTheStatusCode(int remoteStatus, int expectedStatus)
        throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(remoteStatus);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatWorkspaces();

      // -- ASSERT --
      assertEquals(expectedStatus, result.status());
      assertEquals("[XTM One] HTTP " + remoteStatus, result.body().get("detail").asText());
    }

    static Stream<Arguments> statusesWithoutDetail() {
      return Stream.of(
          Arguments.of(401, 422),
          Arguments.of(403, 403),
          Arguments.of(500, 500),
          Arguments.of(302, 502));
    }

    @Test
    @DisplayName("Given XTM One answers 200 with no JSON body should relay a 502")
    void given_unreadableSuccess_should_relayBadGateway() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);

      // -- ACT --
      XtmOneClient.RelayedResponse result = xtmOneClient.listChatWorkspaces();

      // -- ASSERT --
      assertEquals(502, result.status());
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

    @Test
    @DisplayName("Given the connection fails should throw INTERNAL_SERVER_ERROR")
    void given_connectionFails_should_throwInternalServerError() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.listChatWorkspaces());
      assertEquals(500, ex.getStatusCode().value());
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

  @Nested
  @DisplayName("getChatPrompts")
  class GetChatPrompts {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.getChatPrompts());
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 should return the payload read as the current user")
    @SuppressWarnings("unchecked")
    void given_returns200_should_returnPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      Map<String, Object> payload = Map.of("prompts", List.of(Map.of("id", "p-1")));
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(payload);

      // -- ACT --
      Map<String, Object> result = xtmOneClient.getChatPrompts();

      // -- ASSERT --
      assertEquals(payload, result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertEquals("GET", request.getMethod());
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/prompts"));
      assertEquals("Bearer fake-jwt", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    @DisplayName("Given XTM One returns 404 should propagate 404")
    void given_upstreamNotFound_should_preserveStatusCode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.getChatPrompts());
      assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given the connection fails should throw INTERNAL_SERVER_ERROR")
    void given_connectionFails_should_throwInternalServerError() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      when(httpClient.execute(any(), any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("Connection refused"));

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.getChatPrompts());
      assertEquals(500, ex.getStatusCode().value());
    }
  }

  @Nested
  @DisplayName("getChatQuota")
  class GetChatQuota {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.getChatQuota());
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns a quota object should return it")
    void given_quotaObject_should_returnIt() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      JsonNode quota = JsonNodeFactory.instance.objectNode().put("used", 3).put("limit", 10);
      when(objectMapper.readTree(anyString())).thenReturn(quota);

      // -- ACT --
      JsonNode result = xtmOneClient.getChatQuota();

      // -- ASSERT --
      assertEquals(quota, result);
      ClassicHttpRequest request = (ClassicHttpRequest) requestCaptor.getValue();
      assertTrue(request.getUri().toString().endsWith("/api/v1/platform/chat/quota"));
    }

    @Test
    @DisplayName("Given XTM One returns null (nothing to show) should return a null node")
    void given_nullQuota_should_returnNullNode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      when(objectMapper.readTree(anyString())).thenReturn(NullNode.getInstance());

      // -- ACT & ASSERT --
      assertTrue(xtmOneClient.getChatQuota().isNull());
    }

    @Test
    @DisplayName("Given XTM One returns something other than an object should return a null node")
    void given_nonObjectQuota_should_returnNullNode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(200);
      when(objectMapper.readTree(anyString())).thenReturn(JsonNodeFactory.instance.arrayNode());

      // -- ACT & ASSERT --
      assertTrue(xtmOneClient.getChatQuota().isNull());
    }

    @Test
    @DisplayName("Given XTM One returns 503 should propagate 503")
    void given_upstreamUnavailable_should_preserveStatusCode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(503);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(ResponseStatusException.class, () -> xtmOneClient.getChatQuota());
      assertEquals(503, ex.getStatusCode().value());
    }
  }

  @Nested
  @DisplayName("submitMessageFeedback")
  class SubmitMessageFeedback {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "positive", null));
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 should post the rating and return the stored one")
    @SuppressWarnings("unchecked")
    void given_returns200_should_postRatingAndReturnPayload() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(200);
      ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
      when(objectMapper.writeValueAsString(bodyCaptor.capture())).thenReturn("{}");
      Map<String, Object> payload = Map.of("rating", "negative", "comment", "Wrong CVE");
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(payload);

      // -- ACT --
      Map<String, Object> result =
          xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "negative", "Wrong CVE");

      // -- ASSERT --
      assertEquals(payload, result);
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
      when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(Map.of());

      // -- ACT --
      xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "positive", null);

      // -- ASSERT --
      assertTrue(bodyCaptor.getValue().containsKey("comment"));
      assertNull(bodyCaptor.getValue().get("comment"));
    }

    @Test
    @DisplayName("Given XTM One returns 404 (message not readable) should propagate 404")
    void given_upstreamNotFound_should_preserveStatusCode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);
      when(objectMapper.writeValueAsString(any())).thenReturn("{}");

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.submitMessageFeedback("conv-1", "msg-1", "positive", null));
      assertEquals(404, ex.getStatusCode().value());
    }
  }

  @Nested
  @DisplayName("retractMessageFeedback")
  class RetractMessageFeedback {

    @Test
    @DisplayName("Given not configured should throw SERVICE_UNAVAILABLE")
    void given_notConfigured_should_throwServiceUnavailable() {
      when(config.isConfigured()).thenReturn(false);

      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.retractMessageFeedback("conv-1", "msg-1"));
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 204 should send a DELETE to the message feedback")
    void given_returns204_should_deleteFeedback() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      ArgumentCaptor<Object> requestCaptor = mockExchange(204);

      // -- ACT --
      xtmOneClient.retractMessageFeedback("conv-1", "msg-1");

      // -- ASSERT --
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

    @Test
    @DisplayName("Given XTM One returns 404 should propagate 404")
    void given_upstreamNotFound_should_preserveStatusCode() throws Exception {
      // -- ARRANGE --
      configureClientCommon();
      mockExchange(404);

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class,
              () -> xtmOneClient.retractMessageFeedback("conv-1", "msg-1"));
      assertEquals(404, ex.getStatusCode().value());
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
