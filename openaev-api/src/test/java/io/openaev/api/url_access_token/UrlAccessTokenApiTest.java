package io.openaev.api.url_access_token;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_URI;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("URL Access Token API tests")
public class UrlAccessTokenApiTest extends IntegrationTest {

  public static final String TARGET_URL = "/api/exercises";
  public static final String VALID_RAW_TOKEN = "a-valid-raw-token";
  public static final String INVALID_RAW_TOKEN = "an-invalid-raw-token";

  @Autowired private MockMvc mvc;

  @MockitoBean private PreviewFeatureService previewFeatureService;
  @MockitoBean private UrlAccessTokenService urlAccessTokenService;

  @BeforeEach
  void setup() {
    // Reset mocks before each test
  }

  // -- READ --

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("When URL_ACCESS_TOKEN feature is disabled")
  class WhenFeatureIsDisabled {

    @BeforeEach
    void disableFeature() {
      doReturn(false).when(previewFeatureService).isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN);
    }

    @Test
    @DisplayName("Any token request should return 401 Unauthorized")
    void given_disabledFeature_should_return_401() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", VALID_RAW_TOKEN))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("When URL_ACCESS_TOKEN feature is enabled")
  class WhenFeatureIsEnabled {

    @BeforeEach
    void enableFeature() {
      doReturn(true).when(previewFeatureService).isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN);
      doNothing().when(urlAccessTokenService).updateLastUsed(any());
    }

    @Test
    @DisplayName("Valid token should redirect to target URL and set secure cookie")
    void given_validToken_should_redirect_and_set_cookie() throws Exception {
      // -- ARRANGE --
      UrlAccessToken token = new UrlAccessToken();
      token.setId("token-id");
      token.setUrl(TARGET_URL);

      doReturn(token).when(urlAccessTokenService).validateTokenExpiration(VALID_RAW_TOKEN);

      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", VALID_RAW_TOKEN))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", TARGET_URL))
          .andExpect(
              header()
                  .string(
                      "Set-Cookie",
                      containsString(URL_ACCESS_COOKIE_NAME + "=" + VALID_RAW_TOKEN)));

      verify(urlAccessTokenService).updateLastUsed(token);
    }

    @Test
    @DisplayName("Valid token cookie should have HttpOnly, Secure and SameSite=Strict attributes")
    void given_validToken_should_set_cookie_with_security_attributes() throws Exception {
      // -- ARRANGE --
      UrlAccessToken token = new UrlAccessToken();
      token.setId("token-id");
      token.setUrl(TARGET_URL);

      doReturn(token).when(urlAccessTokenService).validateTokenExpiration(VALID_RAW_TOKEN);

      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", VALID_RAW_TOKEN))
          .andExpect(status().isFound())
          .andExpect(
              header()
                  .string(
                      "Set-Cookie",
                      allOf(
                          containsString("HttpOnly"),
                          containsString("Secure"),
                          containsString("SameSite=Strict"),
                          containsString("Path=/"))));
    }

    @Test
    @DisplayName("Expired or revoked token should return 401 Unauthorized")
    void given_expiredOrRevokedToken_should_return_401() throws Exception {
      // -- ARRANGE --
      doThrow(new AccessDeniedException("Invalid URL access token"))
          .when(urlAccessTokenService)
          .validateTokenExpiration(INVALID_RAW_TOKEN);

      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", INVALID_RAW_TOKEN))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Unknown token should return 401 Unauthorized")
    void given_unknownToken_should_return_401() throws Exception {
      // -- ARRANGE --
      doThrow(new AccessDeniedException("Invalid URL access token"))
          .when(urlAccessTokenService)
          .validateTokenExpiration(anyString());

      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", "unknown-token"))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Missing token parameter should return 400 Bad Request")
    void given_missingTokenParam_should_return_400() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Token URL is used as redirect Location")
    void given_validToken_with_specific_url_should_redirect_to_that_url() throws Exception {
      // -- ARRANGE --
      String specificUrl = "/api/exercises/123/overview";
      UrlAccessToken token = new UrlAccessToken();
      token.setId("another-token-id");
      token.setUrl(specificUrl);

      doReturn(token).when(urlAccessTokenService).validateTokenExpiration(VALID_RAW_TOKEN);

      // -- ACT & ASSERT --
      mvc.perform(get(URL_ACCESS_URI).param("token", VALID_RAW_TOKEN))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", specificUrl));
    }
  }
}
