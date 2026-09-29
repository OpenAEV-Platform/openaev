package io.openaev.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
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

  @ParameterizedTest
  @CsvSource({
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
  @CsvSource({"ftp://xtm-one", "not a url", "''"})
  @DisplayName("anything but an http(s) URL has no canonical form")
  void refusesNonHttpUrls(String url) {
    assertThat(XtmOneIdentity.canonical(url)).isEqualTo(Optional.empty());
  }
}
