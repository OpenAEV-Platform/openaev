package io.openaev.secrets.provider.impl.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.Context;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApacheAzureHttpClient tests")
class ApacheAzureHttpClientTest {

  private static final String TOKEN_URL = "https://login.example.test/tenant/oauth2/v2.0/token";

  @Mock private CloseableHttpClient apacheClient;

  private ApacheAzureHttpClient client;

  @BeforeEach
  void setUp() {
    client = new ApacheAzureHttpClient(apacheClient);
  }

  /** Makes the Apache client run the handler it is given against the described response. */
  @SuppressWarnings("unchecked")
  private void givenServerAnswers(int statusCode, byte[] body, Header... headers)
      throws IOException {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    when(response.getCode()).thenReturn(statusCode);
    when(response.getHeaders()).thenReturn(headers);
    when(response.getEntity()).thenReturn(body == null ? null : new ByteArrayEntity(body, null));
    when(apacheClient.execute(
            any(ClassicHttpRequest.class),
            any(HttpContext.class),
            any(HttpClientResponseHandler.class)))
        .thenAnswer(
            invocation ->
                ((HttpClientResponseHandler<?>) invocation.getArgument(2))
                    .handleResponse(response));
  }

  private ClassicHttpRequest capturedRequest() throws IOException {
    ArgumentCaptor<ClassicHttpRequest> captor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
    org.mockito.Mockito.verify(apacheClient)
        .execute(captor.capture(), any(HttpContext.class), any(HttpClientResponseHandler.class));
    return captor.getValue();
  }

  /** The cookie stores handed to the Apache client, one per call, in call order. */
  private List<CookieStore> capturedCookieStores(int calls) throws IOException {
    ArgumentCaptor<HttpContext> captor = ArgumentCaptor.forClass(HttpContext.class);
    org.mockito.Mockito.verify(apacheClient, org.mockito.Mockito.times(calls))
        .execute(
            any(ClassicHttpRequest.class), captor.capture(), any(HttpClientResponseHandler.class));
    return captor.getAllValues().stream()
        .map(context -> HttpClientContext.adapt(context).getCookieStore())
        .toList();
  }

  private static HttpRequest tokenRequest() throws Exception {
    return new HttpRequest(HttpMethod.POST, new URL(TOKEN_URL));
  }

  @Nested
  @DisplayName("Request translation")
  class RequestTranslation {

    @Test
    @DisplayName("The method and the URL of the SDK request are kept")
    void given_request_should_keepMethodAndUrl() throws Exception {
      // Arrange
      givenServerAnswers(200, new byte[0]);

      // Act
      client.sendSync(tokenRequest(), Context.NONE);

      // Assert
      ClassicHttpRequest sent = capturedRequest();
      assertThat(sent.getMethod()).isEqualTo("POST");
      assertThat(sent.getUri()).isEqualTo(URI.create(TOKEN_URL));
    }

    @Test
    @DisplayName("The headers are forwarded, except the ones Apache computes itself")
    void given_headers_should_forwardThemExceptComputedOnes() throws Exception {
      // Arrange
      givenServerAnswers(200, new byte[0]);
      HttpRequest request = tokenRequest();
      request.setHeader("Authorization", "Bearer token");
      request.setHeader("Content-Length", "12");
      request.setHeader("Transfer-Encoding", "chunked");
      request.setHeader("Host", "login.example.test");

      // Act
      client.sendSync(request, Context.NONE);

      // Assert
      ClassicHttpRequest sent = capturedRequest();
      assertThat(sent.getFirstHeader("Authorization").getValue()).isEqualTo("Bearer token");
      assertThat(sent.containsHeader("Content-Length")).isFalse();
      assertThat(sent.containsHeader("Transfer-Encoding")).isFalse();
      assertThat(sent.containsHeader("Host")).isFalse();
    }

    @Test
    @DisplayName("The body of the SDK request is sent as is")
    void given_body_should_sendItAsIs() throws Exception {
      // Arrange
      givenServerAnswers(200, new byte[0]);
      HttpRequest request = tokenRequest();
      request.setBody("grant_type=client_credentials");

      // Act
      client.sendSync(request, Context.NONE);

      // Assert
      ClassicHttpRequest sent = capturedRequest();
      assertThat(sent.getEntity().getContent().readAllBytes())
          .isEqualTo("grant_type=client_credentials".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A request without a body carries no entity")
    void given_noBody_should_sendNoEntity() throws Exception {
      // Arrange
      givenServerAnswers(200, new byte[0]);

      // Act
      client.sendSync(new HttpRequest(HttpMethod.GET, new URL(TOKEN_URL)), Context.NONE);

      // Assert
      assertThat(capturedRequest().getEntity()).isNull();
    }
  }

  @Nested
  @DisplayName("Cookie isolation")
  class CookieIsolation {

    @Test
    @DisplayName("Every request gets its own empty cookie store, so no cookie crosses tenants")
    void given_twoRequests_should_useDistinctEmptyCookieStores() throws Exception {
      // Arrange
      givenServerAnswers(200, new byte[0]);

      // Act
      client.sendSync(tokenRequest(), Context.NONE);
      client.sendSync(tokenRequest(), Context.NONE);

      // Assert
      List<CookieStore> stores = capturedCookieStores(2);
      assertThat(stores).allSatisfy(store -> assertThat(store.getCookies()).isEmpty());
      assertThat(stores.get(0)).isNotSameAs(stores.get(1));
    }
  }

  @Nested
  @DisplayName("Response translation")
  class ResponseTranslation {

    @Test
    @DisplayName("The status, the headers and the body are exposed to the SDK")
    void given_response_should_exposeStatusHeadersAndBody() throws Exception {
      // Arrange
      givenServerAnswers(
          400,
          "{\"error\":\"invalid_client\"}".getBytes(StandardCharsets.UTF_8),
          new BasicHeader("Content-Type", "application/json"),
          new BasicHeader("Set-Cookie", "a=1"),
          new BasicHeader("Set-Cookie", "b=2"));
      HttpRequest request = tokenRequest();

      // Act
      HttpResponse response = client.sendSync(request, Context.NONE);

      // Assert
      assertThat(response.getStatusCode()).isEqualTo(400);
      assertThat(response.getRequest()).isSameAs(request);
      assertThat(response.getHeaderValue("content-type")).isEqualTo("application/json");
      assertThat(response.getHeaders().getValues("Set-Cookie")).containsExactly("a=1", "b=2");
      assertThat(response.getBodyAsString().block()).isEqualTo("{\"error\":\"invalid_client\"}");
      assertThat(response.getBodyAsByteArray().block())
          .isEqualTo("{\"error\":\"invalid_client\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("The body can be read more than once")
    void given_response_should_beReadableTwice() throws Exception {
      // Arrange
      givenServerAnswers(200, "payload".getBytes(StandardCharsets.UTF_8));

      // Act
      HttpResponse response = client.sendSync(tokenRequest(), Context.NONE);

      // Assert
      assertThat(response.getBodyAsString().block()).isEqualTo("payload");
      assertThat(response.getBodyAsString().block()).isEqualTo("payload");
      assertThat(response.getBody().blockFirst()).isNotNull();
    }

    @Test
    @DisplayName("A response without an entity has an empty body")
    void given_noEntity_should_exposeAnEmptyBody() throws Exception {
      // Arrange
      givenServerAnswers(204, null);

      // Act
      HttpResponse response = client.sendSync(tokenRequest(), Context.NONE);

      // Assert
      assertThat(response.getStatusCode()).isEqualTo(204);
      assertThat(response.getBodyAsByteArray().block()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Reactive and failure behaviour")
  class ReactiveAndFailureBehaviour {

    @Test
    @DisplayName("The reactive send answers with the same response as the synchronous one")
    void given_reactiveSend_should_answerLikeSendSync() throws Exception {
      // Arrange
      givenServerAnswers(200, "ok".getBytes(StandardCharsets.UTF_8));

      // Act
      HttpResponse response = client.send(tokenRequest()).block();

      // Assert
      assertThat(response).isNotNull();
      assertThat(response.getStatusCode()).isEqualTo(200);
      assertThat(response.getBodyAsString().block()).isEqualTo("ok");
    }

    @Test
    @DisplayName("An I/O failure is surfaced as an unchecked exception")
    @SuppressWarnings("unchecked")
    void given_ioFailure_should_throwUncheckedIoException() throws Exception {
      // Arrange
      when(apacheClient.execute(
              any(ClassicHttpRequest.class),
              any(HttpContext.class),
              any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("connection reset"));
      HttpRequest request = tokenRequest();

      // Act / Assert
      assertThatThrownBy(() -> client.sendSync(request, Context.NONE))
          .isInstanceOf(UncheckedIOException.class)
          .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("An I/O failure fails the reactive send too")
    @SuppressWarnings("unchecked")
    void given_ioFailure_should_failTheReactiveSend() throws Exception {
      // Arrange
      when(apacheClient.execute(
              any(ClassicHttpRequest.class),
              any(HttpContext.class),
              any(HttpClientResponseHandler.class)))
          .thenThrow(new IOException("connection reset"));
      HttpRequest request = tokenRequest();

      // Act / Assert
      assertThatThrownBy(() -> client.send(request).block())
          .hasRootCauseInstanceOf(IOException.class);
    }
  }
}
