package io.openaev.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.TenantUriUtils;
import io.openaev.database.model.User;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.utils.RequestUtils;
import io.openaev.utils.fixtures.JwtFixture;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * The per-user token a connected OpenCTI forwards on the XTM reverse lookup: accepted only from the
 * tenant's OpenCTI, for OpenAEV, on the reverse lookup routes, and never as the orchestrator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XtmUserJwtExtractor: per-user token forwarded by the connected OpenCTI")
class XtmUserJwtExtractorTest {

  private static final String TENANT = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String OPENCTI_URL = "https://opencti.test.filigran.io";
  private static final String AUDIENCE = "https://openaev.test.filigran.io";
  private static final String EMAIL = "analyst@filigran.io";
  private static final String LOOKUP = "/api/tenants/" + TENANT + "/xtm/objects/assets";

  @Mock private XtmConfig xtmConfig;
  @Mock private UserService userService;
  @Mock private HttpClientFactory httpClientFactory;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private CloseableHttpClient httpClient;

  private OpenCTIConfig opencti;
  private XtmUserJwtExtractor extractor;

  @BeforeEach
  void setUp() {
    RequestUtils requestUtils = new RequestUtils();
    extractor =
        new XtmUserJwtExtractor(
            xtmConfig,
            new TenantUriUtils(requestUtils),
            requestUtils,
            userService,
            httpClientFactory,
            new ObjectMapper(),
            openAEVConfig);
    opencti = new OpenCTIConfig();
    opencti.setEnable(true);
    opencti.setUrl(OPENCTI_URL);
    opencti.setToken("service token");
    lenient().when(xtmConfig.getOpencti()).thenReturn(Map.of(TENANT, opencti));
    lenient().when(openAEVConfig.getBaseUrl()).thenReturn(AUDIENCE);
  }

  private static MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    request.setRequestURI(uri);
    return request;
  }

  @SuppressWarnings("unchecked")
  private void stubJwks(String jwksJson, String... laterAnswers) throws Exception {
    when(httpClientFactory.httpClientNoRetry(any(Timeout.class))).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
        .thenReturn(jwksJson, laterAnswers);
  }

  @Test
  @DisplayName("a token of the tenant's OpenCTI for OpenAEV resolves the user, never the marker")
  @SuppressWarnings("unchecked")
  void validTokenResolvesTheUserWithoutTheOrchestratorMarker() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE + "/", false);
    stubJwks(bundle.jwks());
    User analyst = new User();
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(analyst));
    MockHttpServletRequest request = request(LOOKUP);

    Optional<User> user = extractor.authUser(bundle.jwtToken(), request);

    assertThat(user).containsSame(analyst);
    assertThat(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE)).isNull();
    ArgumentCaptor<ClassicHttpRequest> sent = ArgumentCaptor.forClass(ClassicHttpRequest.class);
    verify(httpClient).execute(sent.capture(), (HttpClientResponseHandler<String>) any());
    assertThat(sent.getValue().getUri().toString()).isEqualTo(OPENCTI_URL + "/xtm/auth/jwks");
    verify(httpClientFactory).httpClientNoRetry(XtmUserJwtExtractor.JWKS_TIMEOUT);
    verify(httpClientFactory, never()).httpClientCustom();
  }

  @Test
  @DisplayName("tokens naming an unknown key cost one JWKS read, not one per request")
  @SuppressWarnings("unchecked")
  void unknownKeyRefreshesAreThrottled() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    String rotatedOut =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false)
            .jwks()
            .replace("test-123", "rotated-out");
    stubJwks(rotatedOut);

    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
          .isInstanceOf(JwtException.class);
    }
    verify(httpClient, times(1))
        .execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any());
    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("a key set past its lifetime is never used when its refresh fails")
  void expiredKeySetIsDroppedWhenItsRefreshFails() throws Exception {
    Instant start = Instant.now();
    extractor.clock = Clock.fixed(start, ZoneOffset.UTC);
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    stubJwks(bundle.jwks(), (String) null);
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(new User()));
    assertThat(extractor.authUser(bundle.jwtToken(), request(LOOKUP))).isPresent();

    extractor.clock =
        Clock.fixed(
            start.plus(XtmUserJwtExtractor.JWKS_CACHE_TTL).plus(Duration.ofMinutes(1)),
            ZoneOffset.UTC);

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
        .isInstanceOf(JwtException.class);
  }

  @Test
  @DisplayName("OpenCTI reached on an internal URL: its configured issuer is trusted")
  @SuppressWarnings("unchecked")
  void configuredIssuerIsTrustedWithKeysFromTheConfiguredUrl() throws Exception {
    opencti.setUrl("http://opencti:8080");
    opencti.setIssuer(OPENCTI_URL);
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    stubJwks(bundle.jwks());
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(new User()));

    assertThat(extractor.authUser(bundle.jwtToken(), request(LOOKUP))).isPresent();
    ArgumentCaptor<ClassicHttpRequest> sent = ArgumentCaptor.forClass(ClassicHttpRequest.class);
    verify(httpClient).execute(sent.capture(), (HttpClientResponseHandler<String>) any());
    assertThat(sent.getValue().getUri().toString()).isEqualTo("http://opencti:8080/xtm/auth/jwks");
  }

  @Test
  @DisplayName("an issuer other than the tenant's OpenCTI is refused before any key is read")
  void untrustedIssuerIsRefused() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(
            "https://evil.attacker.example", EMAIL, AUDIENCE, false);

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
        .isInstanceOf(AuthenticationError.class);
    verifyNoInteractions(httpClientFactory, userService);
  }

  @Test
  @DisplayName("a token minted for another audience is refused")
  void wrongAudienceIsRefused() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(
            OPENCTI_URL, EMAIL, "https://another-platform.example", false);
    stubJwks(bundle.jwks());

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
        .isInstanceOf(JwtException.class);
    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("a token signed by a key OpenCTI does not publish is refused")
  void forgedSignatureIsRefused() throws Exception {
    JwtFixture.Bundle legitimate =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    JwtFixture.Bundle forged =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    stubJwks(legitimate.jwks());

    assertThatThrownBy(() -> extractor.authUser(forged.jwtToken(), request(LOOKUP)))
        .isInstanceOf(JwtException.class);
    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("an expired token is refused")
  void expiredTokenIsRefused() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, true);
    stubJwks(bundle.jwks());

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
        .isInstanceOf(JwtException.class);
    verifyNoInteractions(userService);
  }

  @Test
  @DisplayName("an email no OpenAEV user carries resolves nobody")
  void unknownEmailResolvesNobody() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);
    stubJwks(bundle.jwks());
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    assertThat(extractor.authUser(bundle.jwtToken(), request(LOOKUP))).isEmpty();
  }

  @Test
  @DisplayName("outside the reverse lookup the token opens nothing")
  void otherRoutesAreRefused() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);

    for (String uri :
        new String[] {
          "/api/me/tokens",
          "/api/tenants/" + TENANT + "/scenarios",
          "/api/tenants/" + TENANT + "/xtm/counterparts/assets",
          "/api/tenants/" + TENANT + "/xtm/objects/assets/extra"
        }) {
      assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(uri)))
          .as(uri)
          .isInstanceOf(AuthenticationError.class);
    }
    verifyNoInteractions(httpClientFactory, userService);
  }

  @Test
  @DisplayName("a tenant with no enabled OpenCTI connection trusts no forwarded token")
  void tenantWithoutOpenCtiIsRefused() throws Exception {
    opencti.setEnable(false);
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(OPENCTI_URL, EMAIL, AUDIENCE, false);

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(LOOKUP)))
        .isInstanceOf(AuthenticationError.class);
    String otherTenant = "/api/tenants/11111111-1111-4111-8111-111111111111/xtm/objects/assets";
    opencti.setEnable(true);
    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request(otherTenant)))
        .isInstanceOf(AuthenticationError.class);
    verifyNoInteractions(httpClientFactory, userService);
  }

  @Test
  @DisplayName("the issuer is compared canonical: case, default port and trailing slash")
  void issuerIsComparedCanonical() {
    assertThat(
            XtmUserJwtExtractor.isTrustedIssuer(opencti, "HTTPS://OpenCTI.test.filigran.io:443/"))
        .isTrue();
    assertThat(XtmUserJwtExtractor.isTrustedIssuer(opencti, OPENCTI_URL + "/other")).isFalse();
    assertThat(XtmUserJwtExtractor.isTrustedIssuer(opencti, "opencti")).isFalse();
  }
}
