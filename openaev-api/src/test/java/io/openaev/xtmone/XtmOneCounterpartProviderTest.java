package io.openaev.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class XtmOneCounterpartProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final XtmOneConfig config = new XtmOneConfig();
  private final XtmOneIdentity identity = mock(XtmOneIdentity.class);
  private final XtmOneClient client = mock(XtmOneClient.class);
  private final HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
  private final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

  private XtmOneCounterpartProvider provider() {
    return new XtmOneCounterpartProvider(config, identity, client, httpClientFactory, mapper);
  }

  private void connect() {
    config.setUrl("http://xtm-one:4000");
    config.setToken("platform-token");
    when(identity.browserUrl()).thenReturn("https://xtm-one.example");
    when(client.issueJwtForCurrentUser()).thenReturn("user-jwt");
  }

  /** XTM One answers {@code status} with {@code body} to the next lookup. */
  @SuppressWarnings("unchecked")
  private ArgumentCaptor<ClassicHttpRequest> answers(int status, String body) throws IOException {
    ArgumentCaptor<ClassicHttpRequest> request = ArgumentCaptor.forClass(ClassicHttpRequest.class);
    when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
    when(httpClient.execute(request.capture(), (HttpClientResponseHandler<Integer>) any()))
        .thenAnswer(
            invocation -> {
              BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
              response.setEntity(new StringEntity(body));
              return ((HttpClientResponseHandler<Integer>) invocation.getArgument(1))
                  .handleResponse(response);
            });
    return request;
  }

  @Test
  @DisplayName("XTM One holds assets only, read as the calling user")
  void scope() {
    XtmOneCounterpartProvider provider = provider();
    assertThat(provider.product()).isEqualTo("xtmone");
    assertThat(provider.supports(CounterpartKind.ASSET)).isTrue();
    assertThat(provider.supports(CounterpartKind.VULNERABILITY)).isFalse();
    assertThat(provider.supports(CounterpartKind.THREAT)).isFalse();
    assertThat(provider.readsAsCaller()).isTrue();
  }

  @Test
  @DisplayName("not configured: not connected, and a lookup is unavailable without a call")
  void notConnected() {
    XtmOneCounterpartProvider provider = provider();
    assertThat(provider.connectedUrl("t")).isEmpty();
    assertThatThrownBy(() -> provider.resolve("t", CounterpartKind.ASSET, List.of("web-02")))
        .isInstanceOf(CounterpartUnavailableException.class);
    verifyNoInteractions(httpClientFactory);
  }

  @Test
  @DisplayName("connected: the browser URL is XTM One's published identity, never its internal one")
  void connectedUrl() {
    connect();
    assertThat(provider().connectedUrl("t")).contains("https://xtm-one.example");
  }

  @Test
  @DisplayName("a lookup posts the keys with the user's own JWT and keeps the assets answered")
  void resolve() throws Exception {
    connect();
    ArgumentCaptor<ClassicHttpRequest> request =
        answers(
            200,
            """
            {"items": [
              {"key": "WEB-02", "id": "7f1c", "name": "web-02", "category": "server",
               "criticality": "high", "internet_facing": true, "owner": null, "matches": 1,
               "url": "https://xtm-one.example/ctem/inventory/assets/7f1c"},
              {"key": "unasked", "id": "x", "name": "x", "matches": 1, "url": "u"}
            ]}
            """);
    Map<String, Counterpart> found =
        provider().resolve("t", CounterpartKind.ASSET, List.of("web-02", "db-01"));

    ClassicHttpRequest sent = request.getValue();
    assertThat(sent.getUri().toString())
        .isEqualTo("http://xtm-one:4000/api/v1/organization-context/assets/lookup");
    assertThat(sent.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue())
        .isEqualTo("Bearer user-jwt");
    assertThat(EntityUtils.toString(sent.getEntity()))
        .isEqualTo("{\"keys\":[\"web-02\",\"db-01\"]}");

    assertThat(found).containsOnlyKeys("web-02");
    Counterpart asset = found.get("web-02");
    assertThat(asset.product()).isEqualTo("xtmone");
    assertThat(asset.entityId()).isEqualTo("7f1c");
    assertThat(asset.entityType()).isEqualTo("asset");
    assertThat(asset.url()).isEqualTo("https://xtm-one.example/ctem/inventory/assets/7f1c");
    assertThat(asset.facts())
        .containsExactly(
            new Counterpart.Fact("criticality", "high"),
            new Counterpart.Fact("category", "server"));
  }

  @Test
  @DisplayName("another kind is never sent to XTM One")
  void otherKind() throws Exception {
    connect();
    assertThat(provider().resolve("t", CounterpartKind.VULNERABILITY, List.of("CVE-2024-1")))
        .isEmpty();
    verifyNoInteractions(httpClientFactory);
  }

  @Test
  @DisplayName("a refusal is unavailable, never a miss")
  void refused() throws Exception {
    connect();
    answers(401, "{\"detail\": \"unauthorised\"}");
    assertThatThrownBy(() -> provider().resolve("t", CounterpartKind.ASSET, List.of("web-02")))
        .isInstanceOf(CounterpartUnavailableException.class)
        .hasMessageContaining("401");
  }
}
