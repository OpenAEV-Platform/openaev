package io.openaev.opencrq.counterpart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.opencrq.config.OpenCRQConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenCRQCounterpartProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private OpenCRQCounterpartProvider provider(Map<String, Object> opencrq) {
    XtmConfig config = new XtmConfig();
    org.springframework.test.util.ReflectionTestUtils.setField(config, "mapper", mapper);
    config.setOpencrq(opencrq);
    return new OpenCRQCounterpartProvider(config, null, mapper);
  }

  private static final Map<String, Object> CONNECTED =
      Map.of("t", Map.of("enable", true, "url", "https://ocrq/", "token", "ogrc_secret"));

  private final HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
  private final CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

  private OpenCRQCounterpartProvider connected() {
    XtmConfig config = new XtmConfig();
    org.springframework.test.util.ReflectionTestUtils.setField(config, "mapper", mapper);
    config.setOpencrq(CONNECTED);
    return new OpenCRQCounterpartProvider(config, httpClientFactory, mapper);
  }

  /** OpenCRQ answers {@code status} with {@code body} to the next lookup. */
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
  @DisplayName("the product is opencrq and a counterpart without facts carries an empty list")
  void product() {
    assertThat(provider(Map.of()).product()).isEqualTo("opencrq");
    assertThat(new Counterpart("k", "opencrq", "i", "t", "n", "u", null).facts()).isEmpty();
  }

  @Test
  @DisplayName("a connected tenant is looked up with its service-account token, server side")
  void resolve() throws Exception {
    ArgumentCaptor<ClassicHttpRequest> request =
        answers(
            200,
            """
            {"result":{"data":[{"object_key":"cve-2024-1","object_id":"v1",
              "object_url":"https://ocrq/v1","object_facts":[{"key":"fix_value","value":"9"},
              {"key":"annual_loss"}]}]}}
            """);

    Map<String, Counterpart> found =
        connected().resolve("t", CounterpartKind.VULNERABILITY, List.of("CVE-2024-1"));

    assertThat(found).containsOnlyKeys("CVE-2024-1");
    Counterpart c = found.get("CVE-2024-1");
    assertThat(c.entityType()).isEqualTo("vulnerability");
    assertThat(c.name()).isEqualTo("cve-2024-1");
    assertThat(c.facts()).containsExactly(new Counterpart.Fact("fix_value", "9"));
    HttpGet get = (HttpGet) request.getValue();
    assertThat(get.getUri().toString()).startsWith("https://ocrq/trpc/xtm.lookup?input=");
    assertThat(get.getFirstHeader(HttpHeaders.AUTHORIZATION).getValue())
        .isEqualTo("Bearer ogrc_secret");
  }

  @Test
  @DisplayName("no key or an unsupported kind answers nothing without calling OpenCRQ")
  void resolveNothingToAsk() throws Exception {
    OpenCRQCounterpartProvider p = connected();

    assertThat(p.resolve("t", CounterpartKind.ASSET, List.of())).isEmpty();
    assertThat(p.resolve("t", CounterpartKind.ATTACK_PATTERN, List.of("T1059"))).isEmpty();
    verifyNoInteractions(httpClientFactory);
  }

  @Test
  @DisplayName("a refused lookup makes OpenCRQ unavailable")
  void resolveRefused() throws Exception {
    answers(401, "{\"error\":{}}");

    assertThatThrownBy(() -> connected().resolve("t", CounterpartKind.ASSET, List.of("host")))
        .isInstanceOf(CounterpartUnavailableException.class)
        .hasMessageContaining("401");
  }

  @Test
  @DisplayName("an unreadable answer makes OpenCRQ unavailable")
  void resolveUnreadable() throws Exception {
    answers(200, "not json {");

    assertThatThrownBy(() -> connected().resolve("t", CounterpartKind.THREAT, List.of("x")))
        .isInstanceOf(CounterpartUnavailableException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("an unreachable OpenCRQ is unavailable")
  void resolveUnreachable() throws Exception {
    when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<Integer>) any()))
        .thenThrow(new IOException("connection refused"));

    assertThatThrownBy(() -> connected().resolve("t", CounterpartKind.ASSET, List.of("host")))
        .isInstanceOf(CounterpartUnavailableException.class)
        .hasMessage("OpenCRQ cannot be reached");
  }

  @Test
  @DisplayName("the tRPC base is api-url when set, else {url}/trpc, and none without a url")
  void apiUrl() {
    OpenCRQConfig config = new OpenCRQConfig();
    assertThat(config.getApiUrl()).isNull();
    config.setUrl("https://ocrq/");
    assertThat(config.getApiUrl()).isEqualTo("https://ocrq/trpc");
    assertThat(config.getFormattedUrl()).isEqualTo("https://ocrq/");
    config.setApiUrl("http://opencrq-back:4000/trpc/");
    assertThat(config.getApiUrl()).isEqualTo("http://opencrq-back:4000/trpc");
    config.setUrl("https://ocrq");
    assertThat(config.getFormattedUrl()).isEqualTo("https://ocrq/");
  }

  @Test
  @DisplayName("a tenant is connected to OpenCRQ only with enable, url and token")
  void connectedUrl() {
    OpenCRQCounterpartProvider p =
        provider(
            Map.of(
                "a", Map.of("enable", true, "url", "https://ocrq", "token", "ogrc_x"),
                "b", Map.of("enable", false, "url", "https://ocrq", "token", "ogrc_x"),
                "c", Map.of("enable", true, "url", "https://ocrq")));

    assertThat(p.connectedUrl("a")).contains("https://ocrq/");
    assertThat(p.connectedUrl("b")).isEmpty();
    assertThat(p.connectedUrl("c")).isEmpty();
    assertThat(p.connectedUrl("unknown")).isEmpty();
    assertThat(p.connectedUrl(null)).isEmpty();
  }

  @Test
  @DisplayName("OpenCRQ holds threats, vulnerabilities and assets, never attack patterns or alerts")
  void supports() {
    OpenCRQCounterpartProvider p = provider(Map.of());

    assertThat(p.supports(CounterpartKind.THREAT)).isTrue();
    assertThat(p.supports(CounterpartKind.VULNERABILITY)).isTrue();
    assertThat(p.supports(CounterpartKind.ASSET)).isTrue();
    assertThat(p.supports(CounterpartKind.ATTACK_PATTERN)).isFalse();
    assertThat(p.supports(CounterpartKind.ALERT)).isFalse();
  }

  @Test
  @DisplayName("the lookup is one tRPC query of xtm.lookup with the kind and the keys")
  void lookupUrl() {
    String url =
        provider(Map.of())
            .lookupUrl(
                "https://ocrq/trpc", CounterpartKind.VULNERABILITY, List.of("CVE-2024-1", "CVE-2"));

    assertThat(url).startsWith("https://ocrq/trpc/xtm.lookup?input=");
    String input = URLDecoder.decode(url.substring(url.indexOf('=') + 1), StandardCharsets.UTF_8);
    assertThat(input).isEqualTo("{\"kind\":\"VULNERABILITY\",\"keys\":[\"CVE-2024-1\",\"CVE-2\"]}");
  }

  @Test
  @DisplayName("an answered key matches the asked key in any case and carries the risk facts")
  void match() throws Exception {
    JsonNode body =
        mapper.readTree(
            """
            {"result":{"data":[
              {"object_key":"intrusion-set--abc","object_id":"t1","object_type":"Intrusion-Set",
               "object_name":"APT29","object_url":"https://ocrq/t/threat-center/threats/t1",
               "object_facts":[{"key":"risk_scenarios","value":"3"},
                               {"key":"annual_loss","value":"125000"}]},
              {"object_key":"intrusion-set--zzz","object_id":"t2","object_type":"Intrusion-Set",
               "object_name":"other","object_url":"https://ocrq/t/x","object_facts":[]},
              {"object_key":"intrusion-set--abc","object_id":"t3","object_type":"Intrusion-Set",
               "object_name":"dup","object_url":"https://ocrq/t/y","object_facts":[]}
            ]}}
            """);

    Map<String, Counterpart> found =
        provider(Map.of()).match(CounterpartKind.THREAT, List.of("Intrusion-Set--ABC"), body);

    assertThat(found).containsOnlyKeys("Intrusion-Set--ABC");
    Counterpart c = found.get("Intrusion-Set--ABC");
    assertThat(c.product()).isEqualTo("opencrq");
    assertThat(c.entityId()).isEqualTo("t1");
    assertThat(c.url()).isEqualTo("https://ocrq/t/threat-center/threats/t1");
    assertThat(c.facts())
        .containsExactly(
            new Counterpart.Fact("risk_scenarios", "3"),
            new Counterpart.Fact("annual_loss", "125000"));
  }

  @Test
  @DisplayName("an answer without the tRPC result shape matches nothing")
  void matchEmpty() throws Exception {
    assertThat(
            provider(Map.of())
                .match(
                    CounterpartKind.ASSET,
                    List.of("host"),
                    mapper.readTree("{\"error\":{\"message\":\"FORBIDDEN\"}}")))
        .isEmpty();
  }

  @Test
  @DisplayName("a tenant not connected to OpenCRQ is refused as unavailable before any call")
  void notConnected() {
    assertThatThrownBy(
            () -> provider(Map.of()).resolve("t", CounterpartKind.ASSET, List.of("host")))
        .isInstanceOf(CounterpartUnavailableException.class);
  }
}
