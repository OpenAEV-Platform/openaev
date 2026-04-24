package io.openaev.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.utils.fixtures.JwtFixture;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockConfig.WithMockXtmOneConfig;
import io.openaev.xtmone.XtmOneConfig;
import jakarta.transaction.Transactional;
import java.io.IOException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Transactional
public class TokenAuthenticationFilterTest extends IntegrationTest {
  private static final String TRUSTED_ISSUER_URL = "https://xtmone.test.filigran.io";

  @Autowired private MockMvc mvc;
  @Autowired private UserComposer userComposer;
  @Autowired private TokenComposer tokenComposer;
  @Autowired private OpenAEVConfig openAEVConfig;

  @MockitoBean private XtmOneConfig xtmOneConfig;
  @MockitoBean private HttpClientFactory httpClientFactory;
  @Mock private CloseableHttpClient mockHttpClient;

  @Nested
  @DisplayName("Passing an API token via header")
  public class PassingAPITokenViaHeaderTest {

    @Nested
    @DisplayName("When authorization header value does not have 'bearer' prefix")
    public class WhenAuthorizationHeaderValueDoesNotHaveBearerPrefix {
      @Test
      @DisplayName("Given invalid API token then fail authentication")
      public void given_invalidToken_then_failAuthentication() throws Exception {
        String invalid = "invalid token";

        mvc.perform(
                get("/api/me/tokens")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", invalid))
            .andExpect(status().isUnauthorized());
      }

      @Test
      @DisplayName("Given valid API token then succeed authentication")
      public void given_validToken_then_succeedAuthentication() throws Exception {
        String valid = "valid token";
        userComposer
            .forUser(UserFixture.getUserWithDefaultEmail())
            .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue(valid)))
            .persist();

        mvc.perform(
                get("/api/me/tokens")
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", valid))
            .andExpect(status().isOk());
      }
    }

    @Nested
    @DisplayName("When authorization header value has 'bearer' prefix")
    public class WhenAuthorizationHeaderValueHasBearerPrefix {
      private final String headerValueMask = "Bearer %s";

      @Nested
      @DisplayName("With plain API token")
      public class WithPlainApiToken {
        @Test
        @DisplayName("Given invalid API token then fail authentication")
        public void given_invalidToken_then_failAuthentication() throws Exception {
          String invalid = "invalid token";

          mvc.perform(
                  get("/api/me/tokens")
                      .accept(MediaType.APPLICATION_JSON)
                      .header("Authorization", headerValueMask.formatted(invalid)))
              .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Given valid API token then succeed authentication")
        public void given_validToken_then_succeedAuthentication() throws Exception {
          String valid = "valid token";
          userComposer
              .forUser(UserFixture.getUserWithDefaultEmail())
              .withToken(tokenComposer.forToken(TokenFixture.getTokenWithValue(valid)))
              .persist();

          mvc.perform(
                  get("/api/me/tokens")
                      .accept(MediaType.APPLICATION_JSON)
                      .header("Authorization", headerValueMask.formatted(valid)))
              .andExpect(status().isOk());
        }
      }

      @Nested
      @DisplayName("With XTM JWKS JWT")
      public class WithXtmJwksJwt {
        @Nested
        @DisplayName("When XTM One is not configured")
        @WithMockXtmOneConfig // unconfigured
        public class WhenXtmOneIsNotConfigured {
          @Test
          @DisplayName("Fail authentication")
          public void failAuthentication() throws Exception {
            JwtFixture.Bundle bundle = JwtFixture.generateConnectorJwtBundle(false);

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }
        }

        @Nested
        @DisplayName("When XTM One is configured")
        public class WhenXtmOneIsConfigured {
          @BeforeEach
          public void before() {
            when(xtmOneConfig.isConfigured()).thenReturn(true);
            when(xtmOneConfig.getUrl()).thenReturn(TRUSTED_ISSUER_URL);
            when(httpClientFactory.httpClientCustom()).thenReturn(mockHttpClient);
          }

          private void stubJwksResponse(String jwksJson) throws IOException {
            when(mockHttpClient.execute(
                    (ClassicHttpRequest) any(), (HttpClientResponseHandler<String>) any()))
                .thenReturn(jwksJson);
          }

          @Test
          @DisplayName("Given existing user and valid issuer, succeed authentication")
          public void given_existingUserAndValidIssuer_then_succeedAuthentication()
              throws Exception {
            UserComposer.Composer userWrapper =
                userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generateXtmJwksJwtBundle(
                    TRUSTED_ISSUER_URL,
                    userWrapper.get().getEmail(),
                    openAEVConfig.getBaseUrl(),
                    false);
            stubJwksResponse(bundle.jwks());

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isOk());
          }

          @Test
          @DisplayName("Given existing user and invalid issuer, fail authentication")
          public void given_existingUserAndInvalidIssuer_then_failAuthentication()
              throws Exception {
            userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generateXtmJwksJwtBundle(
                    "https://evil.attacker.com",
                    UserFixture.EMAIL,
                    openAEVConfig.getBaseUrl(),
                    false);

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }

          @Test
          @DisplayName("Given existing user and expired jwt, fail authentication")
          public void given_existingUserAndExpiredJwt_then_failAuthentication() throws Exception {
            UserComposer.Composer userWrapper =
                userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generateXtmJwksJwtBundle(
                    TRUSTED_ISSUER_URL,
                    userWrapper.get().getEmail(),
                    openAEVConfig.getBaseUrl(),
                    true);
            stubJwksResponse(bundle.jwks());

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }

          @Test
          @DisplayName("Given missing user and valid jwt, fail authentication")
          public void given_missingUserAndValidJwt_then_failAuthentication() throws Exception {
            JwtFixture.Bundle bundle =
                JwtFixture.generateXtmJwksJwtBundle(
                    TRUSTED_ISSUER_URL, "anon@ymo.us", openAEVConfig.getBaseUrl(), false);
            stubJwksResponse(bundle.jwks());

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }
        }
      }
    }
  }
}
