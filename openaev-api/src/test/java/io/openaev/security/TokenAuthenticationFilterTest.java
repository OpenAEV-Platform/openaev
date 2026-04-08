package io.openaev.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.openaev.IntegrationTest;
import io.openaev.utils.fixtures.JwtFixture;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TokenComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockConfig.WithMockXtmOneConfig;
import io.openaev.xtmone.XtmOneConfig;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Transactional
public class TokenAuthenticationFilterTest extends IntegrationTest {
  @Autowired private MockMvc mvc;
  @Autowired private UserComposer userComposer;
  @Autowired private TokenComposer tokenComposer;

  @MockitoBean private XtmOneConfig xtmOneConfig;

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
      @DisplayName("With platform JWT")
      public class WithPlatformJwt {
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
          }

          @Test
          @DisplayName("Given existing user and valid issuer, succeed authentication")
          public void given_existingUserAndValidIssuer_then_succeedAuthentication()
              throws Exception {
            UserComposer.Composer userWrapper =
                userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generatePlatformJwtBundle(userWrapper.get().getEmail(), false);
            when(xtmOneConfig.getToken())
                .thenReturn(JwtFixture.b64(bundle.keyPair().getPublic().getEncoded()));

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isOk());
          }

          @Test
          @DisplayName(
              "Given existing user and valid issuer and bad algorithm, fail authentication")
          public void given_existingUserAndValidIssuerAndBadAlgorithm_then_failAuthentication()
              throws Exception {
            UserComposer.Composer userWrapper =
                userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generatePlatformJwtBundleWithSigAlgo(
                    userWrapper.get().getEmail(), Jwts.SIG.ES256, false);
            when(xtmOneConfig.getToken())
                .thenReturn(JwtFixture.b64(bundle.keyPair().getPublic().getEncoded()));

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }

          @Test
          @DisplayName("Given existing user and invalid issuer, fail authentication")
          public void given_existingUserAndInvalidIssuer_then_failAuthentication()
              throws Exception {
            UserComposer.Composer userWrapper =
                userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist();
            JwtFixture.Bundle bundle =
                JwtFixture.generateForeignJwtBundle(userWrapper.get().getEmail(), false);
            when(xtmOneConfig.getToken())
                .thenReturn(JwtFixture.b64(bundle.keyPair().getPublic().getEncoded()));

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
                JwtFixture.generatePlatformJwtBundle(userWrapper.get().getEmail(), true);
            when(xtmOneConfig.getToken())
                .thenReturn(JwtFixture.b64(bundle.keyPair().getPublic().getEncoded()));

            mvc.perform(
                    get("/api/me/tokens")
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Authorization", headerValueMask.formatted(bundle.jwtToken())))
                .andExpect(status().isUnauthorized());
          }

          @Test
          @DisplayName("Given missing user and valid jwt, fail authentication")
          public void given_missingUserAndValidJwt_then_failAuthentication() throws Exception {
            JwtFixture.Bundle bundle = JwtFixture.generatePlatformJwtBundle("anon@ymo.us", true);
            when(xtmOneConfig.getToken())
                .thenReturn(JwtFixture.b64(bundle.keyPair().getPublic().getEncoded()));

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
