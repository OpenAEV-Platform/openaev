package io.openaev.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.User;
import io.openaev.security.error.AuthenticationError;
import io.openaev.service.UserService;
import io.openaev.utils.fixtures.JwtFixture;
import io.openaev.xtmone.XtmOneConfig;
import java.util.Optional;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit coverage for the cross-platform service-identity marker: {@code authUser} must stamp {@link
 * XtmJwksExtractor#CROSS_PLATFORM_ATTRIBUTE} ONLY when the bearer fully validated as an XTM One
 * cross-platform JWT (trusted issuer, JWKS signature, expected audience) AND resolved a user -
 * never on a refused issuer, a wrong audience, or an unresolved user. {@code TxCtxArgumentResolver}
 * grants run-authoritative tenant scope on exactly this marker, so a stray stamp would be a
 * cross-tenant privilege grant.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("XtmJwksExtractor cross-platform service-identity marker")
class XtmJwksExtractorTest {

  private static final String TRUSTED_ISSUER = "https://xtmone.test.filigran.io";
  private static final String AUDIENCE = "https://openaev.test.filigran.io";
  private static final String EMAIL = "orchestrator@filigran.io";

  @Mock private XtmOneConfig xtmOneConfig;
  @Mock private UserService userService;
  @Mock private HttpClientFactory httpClientFactory;
  @Mock private OpenAEVConfig openAEVConfig;
  @Mock private CloseableHttpClient httpClient;

  private XtmJwksExtractor extractor;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    extractor =
        new XtmJwksExtractor(
            xtmOneConfig, userService, httpClientFactory, new ObjectMapper(), openAEVConfig);
    request = new MockHttpServletRequest();
    // Reached by every case: the configured-check and the trusted-issuer list come first.
    when(xtmOneConfig.isConfigured()).thenReturn(true);
    when(xtmOneConfig.getUrl()).thenReturn(TRUSTED_ISSUER);
  }

  @SuppressWarnings("unchecked")
  private void stubJwks(String jwksJson) throws Exception {
    when(httpClientFactory.httpClientCustom()).thenReturn(httpClient);
    when(httpClient.execute((ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
        .thenReturn(jwksJson);
  }

  @Test
  @DisplayName("a fully validated token that resolves a user stamps the marker")
  void validatedTokenStampsMarker() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(TRUSTED_ISSUER, EMAIL, AUDIENCE, false);
    stubJwks(bundle.jwks());
    when(openAEVConfig.getBaseUrl()).thenReturn(AUDIENCE);
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(new User()));

    Optional<User> user = extractor.authUser(bundle.jwtToken(), request);

    assertThat(user).isPresent();
    assertThat(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE))
        .isEqualTo(Boolean.TRUE);
  }

  @Test
  @DisplayName("an untrusted issuer is refused and never stamps the marker")
  void untrustedIssuerDoesNotStampMarker() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(
            "https://evil.attacker.example", EMAIL, AUDIENCE, false);

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request))
        .isInstanceOf(AuthenticationError.class);
    assertThat(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE)).isNull();
  }

  @Test
  @DisplayName("a token minted for another audience is refused and never stamps the marker")
  void wrongAudienceDoesNotStampMarker() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(
            TRUSTED_ISSUER, EMAIL, "https://another-platform.example", false);
    stubJwks(bundle.jwks());
    when(openAEVConfig.getBaseUrl()).thenReturn(AUDIENCE);

    assertThatThrownBy(() -> extractor.authUser(bundle.jwtToken(), request))
        .isInstanceOf(JwtException.class);
    assertThat(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE)).isNull();
  }

  @Test
  @DisplayName("a valid token whose email resolves no user does not stamp the marker")
  void unresolvedUserDoesNotStampMarker() throws Exception {
    JwtFixture.Bundle bundle =
        JwtFixture.generateXtmJwksJwtBundle(TRUSTED_ISSUER, EMAIL, AUDIENCE, false);
    stubJwks(bundle.jwks());
    when(openAEVConfig.getBaseUrl()).thenReturn(AUDIENCE);
    when(userService.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());

    Optional<User> user = extractor.authUser(bundle.jwtToken(), request);

    assertThat(user).isEmpty();
    assertThat(request.getAttribute(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE)).isNull();
  }
}
