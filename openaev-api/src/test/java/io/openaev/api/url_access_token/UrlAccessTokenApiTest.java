package io.openaev.api.url_access_token;

import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_COOKIE_NAME;
import static io.openaev.api.url_access_token.UrlAccessTokenApi.URL_ACCESS_URI;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.UrlAccessToken;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("URL Access Token API tests")
public class UrlAccessTokenApiTest extends IntegrationTest {

  public static final String TARGET_URL = "/api/exercises";
  public static final String VALID_RAW_TOKEN = "a-valid-raw-token";
  public static final String INVALID_RAW_TOKEN = "an-invalid-raw-token";
  public static final String TOKEN_ID = "token-id";
  public static final String EXERCISE_ID = "exercise-id";

  @Autowired private MockMvc mvc;

  @MockitoBean private PreviewFeatureService previewFeatureService;
  @MockitoBean private UrlAccessTokenService urlAccessTokenService;

  // -- READ --

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Validate all aspects about the token")
  class TokenValidations {

    @BeforeEach
    void enableFeature() {
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
    @DisplayName("Valid token cookie should have HttpOnly and SameSite=Strict attributes")
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
          .andExpect(status().isUnauthorized());
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
          .andExpect(status().isUnauthorized());
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

    @Test
    @DisplayName("Admin can revoke one token by id")
    void given_adminUser_when_deleteTokenById_should_return_204() throws Exception {
      // -- ARRANGE --
      doNothing().when(urlAccessTokenService).revokeToken(TOKEN_ID);

      // -- ACT & ASSERT --
      mvc.perform(deleteRequest(URL_ACCESS_URI + "/" + TOKEN_ID)).andExpect(status().isNoContent());

      verify(urlAccessTokenService).revokeToken(TOKEN_ID);
    }

    @Test
    @DisplayName("Admin can revoke all tokens by exercise id")
    void given_adminUser_when_deleteTokensByExerciseId_should_return_204() throws Exception {
      // -- ARRANGE --
      doReturn(2).when(urlAccessTokenService).revokeAllForExercise(EXERCISE_ID);

      // -- ACT & ASSERT --
      mvc.perform(deleteRequest(URL_ACCESS_URI + "/exercise/" + EXERCISE_ID))
          .andExpect(status().isNoContent());

      verify(urlAccessTokenService).revokeAllForExercise(EXERCISE_ID);
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("DELETE endpoints for non-admin users")
  class DeleteEndpointsForNonAdmin {

    @Test
    @DisplayName("Non-admin cannot revoke token by id")
    void given_nonAdminUser_when_deleteTokenById_should_return_403() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(deleteRequest(URL_ACCESS_URI + "/" + TOKEN_ID)).andExpect(status().isForbidden());

      verify(urlAccessTokenService, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("Non-admin cannot revoke tokens by exercise id")
    void given_nonAdminUser_when_deleteTokensByExerciseId_should_return_403() throws Exception {
      // -- ACT & ASSERT --
      mvc.perform(deleteRequest(URL_ACCESS_URI + "/exercise/" + EXERCISE_ID))
          .andExpect(status().isForbidden());

      verify(urlAccessTokenService, never()).revokeAllForExercise(anyString());
    }
  }

  private MockHttpServletRequestBuilder deleteRequest(String uri) {
    return delete(uri).with(csrf());
  }
}
