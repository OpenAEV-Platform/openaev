package io.openaev.xtmone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.service.xtm_auth.XtmAuthKeyService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Client tests")
class XtmOneClientTest {

  @Mock private HttpClientFactory httpClientFactory;
  @Mock private XtmOneConfig config;
  @Mock private ObjectMapper objectMapper;
  @Mock private XtmAuthKeyService keyService;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private CloseableHttpClient httpClient;

  @InjectMocks private XtmOneClient xtmOneClient;

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
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.listChatAgents("test-jwt"));
      assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Given XTM One returns 200 with agents should return the list")
    void given_returns200WithAgents_should_returnList() throws Exception {
      // -- ARRANGE --
      configureClient();
      List<Map<String, Object>> expectedAgents =
          List.of(Map.of("id", "agent-1", "name", "Agent 1"));
      mockHttpResponse(expectedAgents);

      // -- ACT --
      List<Map<String, Object>> result = xtmOneClient.listChatAgents("test-jwt");

      // -- ASSERT --
      assertEquals(1, result.size());
      assertEquals("agent-1", result.getFirst().get("id"));
    }

    @Test
    @DisplayName("Given XTM One returns 200 with empty list should throw NOT_FOUND")
    void given_returns200Empty_should_throwNotFound() throws Exception {
      // -- ARRANGE --
      configureClient();
      mockHttpResponse(List.of());

      // -- ACT & ASSERT --
      ResponseStatusException ex =
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.listChatAgents("test-jwt"));
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
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.listChatAgents("test-jwt"));
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
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.listChatAgents("test-jwt"));
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
          assertThrows(
              ResponseStatusException.class, () -> xtmOneClient.listChatAgents("test-jwt"));
      assertEquals(500, ex.getStatusCode().value());
    }

    private void configureClient() {
      when(config.isConfigured()).thenReturn(true);
      when(config.getUrl()).thenReturn("http://localhost:8080");
      when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
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
}
