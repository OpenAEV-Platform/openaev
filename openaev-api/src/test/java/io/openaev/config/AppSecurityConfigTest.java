package io.openaev.config;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(Lifecycle.PER_CLASS)
@Transactional
@DisplayName("App Security Config tests")
public class AppSecurityConfigTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  private static final String SCENARIO_SEARCH_URI = SCENARIO_URI + "/search";
  private static final String AUTH_COOKIE_NAME = "openaev_token";
  private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
  private static final String SEARCH_BODY =
      """
      {
        "page": 0,
        "size": 20,
        "sorts": []
      }
      """;

  @Test
  @DisplayName("given valid admin bearer token without cookies, should return HTTP 200")
  void given_validAdminBearerTokenWithoutCookies_should_returnOk() throws Exception {
    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given invalid bearer token, should return HTTP 401")
  void given_invalidBearerToken_should_returnUnauthorized() throws Exception {
    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given valid bearer token and non auth cookie, should return HTTP 403")
  void given_validBearerTokenAndNonAuthCookie_should_returnForbidden() throws Exception {
    Cookie trackingCookie = new Cookie("tracking_id", "abc");

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .cookie(trackingCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given valid auth cookie and csrf without bearer token, should return HTTP 200")
  void given_validAuthCookieAndCsrfWithoutBearerToken_should_returnOk() throws Exception {
    Cookie authCookie = new Cookie(AUTH_COOKIE_NAME, adminToken);
    Cookie csrfCookie = new Cookie(CSRF_COOKIE_NAME, "test-csrf-token");

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .cookie(authCookie, csrfCookie)
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "given valid auth cookie and invalid csrf without bearer token, should return HTTP 403")
  void given_validAuthCookieAndInvalidCsrfWithoutBearerToken_should_returnUnauthorized()
      throws Exception {
    Cookie authCookie = new Cookie(AUTH_COOKIE_NAME, adminToken);
    Cookie csrfCookie = new Cookie(CSRF_COOKIE_NAME, "test-csrf-token-broken");

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .cookie(authCookie, csrfCookie)
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("given jsessionid cookie and csrf without bearer token, should return HTTP 401")
  void given_jsessionIdCookieAndCsrfWithoutBearerToken_should_returnUnauthorized()
      throws Exception {
    Cookie jsessionCookie = new Cookie("JSESSIONID", "dummy-session-id");
    Cookie csrfCookie = new Cookie(CSRF_COOKIE_NAME, "test-csrf-token");

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .cookie(jsessionCookie, csrfCookie)
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given valid jsessionid and csrf without bearer token, should return HTTP 200")
  void given_validJsessionIdAndCsrfWithoutBearerToken_should_returnOk() throws Exception {
    MockHttpSession session = new MockHttpSession();
    Cookie csrfCookie = new Cookie(CSRF_COOKIE_NAME, "test-csrf-token");
    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .session(session)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .cookie(csrfCookie)
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY)
                .with(csrf()))
        .andExpect(status().isOk());

    String sessionId = Objects.requireNonNull(session.getId());
    Cookie jsessionCookie = new Cookie("JSESSIONID", sessionId);

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .session(session)
                .cookie(jsessionCookie)
                .cookie(new Cookie(CSRF_COOKIE_NAME, "test-csrf-token"))
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY)
                .with(csrf()))
        .andExpect(status().isOk());
  }
}
