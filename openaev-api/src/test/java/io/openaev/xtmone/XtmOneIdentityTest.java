package io.openaev.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * OpenAEV reaches XTM One on an internal URL ({@code http://xtm-one:4000}) while XTM One signs
 * with, and expects as audience, its public {@code BASE_URL}, published at {@code
 * /xtm/auth/metadata}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XtmOneIdentity")
class XtmOneIdentityTest {

  private static final String INTERNAL_URL = "http://xtm-one:4000";
  private static final String PUBLIC_ISSUER = "http://localhost:8090";

  @Mock private HttpClientFactory httpClientFactory;
  @Mock private CloseableHttpClient httpClient;

  private final XtmOneConfig config = new XtmOneConfig();
  private XtmOneIdentity identity;

  @BeforeEach
  void setUp() {
    config.setUrl(INTERNAL_URL);
    identity = new XtmOneIdentity(config, httpClientFactory, new ObjectMapper());
  }

  @SuppressWarnings("unchecked")
  private void metadataAnswers(String... bodies) throws IOException {
    when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
        .thenReturn(bodies[0], Arrays.copyOfRange(bodies, 1, bodies.length));
  }

  @Test
  @DisplayName("trusts the configured URL and the published issuer, nothing else")
  void trustsTheConfiguredUrlAndThePublishedIssuer() throws Exception {
    metadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "/\"}");

    assertThat(identity.isXtmOneIssuer(INTERNAL_URL + "/")).isTrue();
    assertThat(identity.isXtmOneIssuer(PUBLIC_ISSUER)).isTrue();
    assertThat(identity.isXtmOneIssuer("http://evil.example")).isFalse();
    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);
  }

  @Test
  @DisplayName("an XTM One that publishes no identity is known by its configured URL")
  void fallsBackToTheConfiguredUrl() throws Exception {
    metadataAnswers((String) null);

    assertThat(identity.isXtmOneIssuer(PUBLIC_ISSUER)).isFalse();
    assertThat(identity.isXtmOneIssuer(INTERNAL_URL)).isTrue();
    assertThat(identity.audience()).isEqualTo(INTERNAL_URL);
  }

  @Test
  @DisplayName("the published identity is fetched once, not on every token")
  void cachesThePublishedIdentity() throws Exception {
    metadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "\"}");

    identity.audience();
    identity.audience();
    identity.isXtmOneIssuer(PUBLIC_ISSUER);

    verify(httpClient, times(1))
        .execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any());
  }

  @Test
  @DisplayName("a 404 drops the published identity: XTM One is known by its configured URL again")
  @SuppressWarnings("unchecked")
  void notPublishedDropsThePublishedIdentity() throws Exception {
    Instant start = Instant.parse("2026-09-29T12:00:00Z");
    identity.clock = Clock.fixed(start, ZoneOffset.UTC);
    when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
        .thenReturn("{\"issuer\":\"" + PUBLIC_ISSUER + "\"}")
        .thenThrow(new XtmOneIdentity.IdentityNotPublished());
    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);

    identity.clock = Clock.fixed(start.plus(Duration.ofHours(2)), ZoneOffset.UTC);

    assertThat(identity.publishedIssuer()).isEmpty();
    assertThat(identity.isXtmOneIssuer(PUBLIC_ISSUER)).isFalse();
    assertThat(identity.audience()).isEqualTo(INTERNAL_URL);
  }

  @Test
  @DisplayName("a transient failure keeps the identity XTM One published last")
  void transientFailureKeepsThePublishedIdentity() throws Exception {
    Instant start = Instant.parse("2026-09-29T12:00:00Z");
    identity.clock = Clock.fixed(start, ZoneOffset.UTC);
    metadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "\"}", null);
    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);

    identity.clock = Clock.fixed(start.plus(Duration.ofHours(2)), ZoneOffset.UTC);

    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);
    verify(httpClient, times(2))
        .execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"issuer\":null}",
        "{\"issuer\":42}",
        "{\"issuer\":\"ftp://xtm.example\"}",
        "{\"issuer\":\"https://user@xtm.example\"}",
        "null"
      })
  @DisplayName("a 200 naming no usable issuer is a failed read: the last identity is kept")
  void metadataWithoutAUsableIssuerKeepsThePublishedIdentity(String body) throws Exception {
    Instant start = Instant.parse("2026-09-29T12:00:00Z");
    identity.clock = Clock.fixed(start, ZoneOffset.UTC);
    metadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "\"}", body, body);
    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);

    identity.clock = Clock.fixed(start.plus(Duration.ofHours(2)), ZoneOffset.UTC);

    assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);
    assertThat(identity.isXtmOneIssuer(PUBLIC_ISSUER)).isTrue();

    identity.clock =
        Clock.fixed(
            start.plus(Duration.ofHours(2)).plus(XtmOneIdentity.RETRY_AFTER).plusSeconds(1),
            ZoneOffset.UTC);
    identity.audience();
    verify(httpClient, times(3))
        .execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any());
  }

  @Test
  @DisplayName("the metadata is read on a client whose connection is bounded, not only the answer")
  void readsTheMetadataOnABoundedClient() throws Exception {
    metadataAnswers("{\"issuer\":\"" + PUBLIC_ISSUER + "\"}");

    identity.audience();

    verify(httpClientFactory).httpClientNoRetry(XtmOneIdentity.METADATA_TIMEOUT);
    assertThat(XtmOneIdentity.METADATA_TIMEOUT.toSeconds()).isLessThanOrEqualTo(10);
  }

  @ParameterizedTest
  @CsvSource({"200, body", "404, not published", "503, unavailable", "302, unavailable"})
  @DisplayName("the metadata response is read by its status code")
  void readsTheMetadataStatus(int code, String expected) throws Exception {
    ClassicHttpResponse response = mock(ClassicHttpResponse.class);
    when(response.getCode()).thenReturn(code);
    if (code == 200) {
      when(response.getEntity()).thenReturn(new StringEntity("{}"));
    }
    switch (expected) {
      case "body" -> assertThat(XtmOneIdentity.readMetadata(response)).isEqualTo("{}");
      case "not published" ->
          assertThatThrownBy(() -> XtmOneIdentity.readMetadata(response))
              .isInstanceOf(XtmOneIdentity.IdentityNotPublished.class);
      default -> assertThat(XtmOneIdentity.readMetadata(response)).isNull();
    }
  }

  @Test
  @DisplayName("XTM One on its public URL (SaaS): its own tokens are trusted without any call")
  void configuredIssuerNeedsNoCall() {
    config.setUrl("https://acme.one.filigran.io");

    assertThat(identity.isXtmOneIssuer("https://acme.one.filigran.io")).isTrue();
    verifyNoInteractions(httpClientFactory);
  }

  @Test
  @DisplayName("a caller never waits on another thread's read of XTM One")
  @SuppressWarnings("unchecked")
  void callersDoNotWaitOnARefresh() throws Exception {
    CountDownLatch reading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(httpClientFactory.httpClientNoRetry(any())).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
        .thenAnswer(
            invocation -> {
              reading.countDown();
              release.await(5, TimeUnit.SECONDS);
              return "{\"issuer\":\"" + PUBLIC_ISSUER + "\"}";
            });
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<String> slow = executor.submit(identity::audience);
      assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(identity.audience()).isEqualTo(INTERNAL_URL);

      release.countDown();
      assertThat(slow.get(5, TimeUnit.SECONDS)).isEqualTo(PUBLIC_ISSUER);
      assertThat(identity.audience()).isEqualTo(PUBLIC_ISSUER);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "http://xtm_one:80, http://xtm_one",
    "https://XTM_ONE:443/, https://xtm_one",
    "HTTP://LocalHost:8090/, http://localhost:8090",
    "https://xtm.example.com:443/base/, https://xtm.example.com/base",
    "http://xtm_one:4000, http://xtm_one:4000",
    "http://xtm-one:4000, http://xtm-one:4000",
  })
  @DisplayName("URLs are compared in one spelling")
  void canonicalizesUrls(String url, String expected) {
    assertThat(XtmOneIdentity.canonical(url)).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "ftp://xtm-one",
    "not a url",
    "''",
    "https://xtm-one.example/?target=other",
    "https://xtm-one.example/#fragment",
    "https://user@xtm-one.example",
    "http://user@xtm_one:4000",
    "http://xtm_one:notaport",
    "http://xtm_one:0",
    "http://xtm_one:99999",
    "http://xtm_one:-1",
    "http://xtm_one:-2",
    "http://xtm_one:+80",
    "http://xtm_one:",
    "http://xtm-one:-2",
  })
  @DisplayName("anything but an http(s) URL has no canonical form")
  void refusesNonHttpUrls(String url) {
    assertThat(XtmOneIdentity.canonical(url)).isEqualTo(Optional.empty());
  }
}
