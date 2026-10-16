package io.openaev.config;

import static io.openaev.rest.executor.ExecutorApi.AGENT_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.user.MeApi.ME_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import io.openaev.utils.helpers.SessionTestHelper;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(Lifecycle.PER_CLASS)
@Transactional
@DisplayName("App Security Config tests")
public class AppSecurityConfigTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenRepository tokenRepository;
  @Autowired private ServiceAccountPrivilegeService serviceAccountPrivilegeService;
  @Autowired private SessionTestHelper sessionTestHelper;
  @Autowired private EntityManager entityManager;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  private static final String SCENARIO_SEARCH_URI = SCENARIO_URI + "/search";
  private static final String AUTH_COOKIE_NAME = "openaev_token";
  private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
  private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
  // base64("proxyuser:proxypass") - shape of a reverse-proxy-injected Basic auth header, never an
  // OpenAEV token.
  private static final String BASIC_AUTH_HEADER_VALUE = "Basic cHJveHl1c2VyOnByb3h5cGFzcw==";
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
  @DisplayName("a bearer call's session is not reusable to authenticate without the bearer token")
  void given_replayedBearerSessionWithoutToken_should_returnUnauthorized() throws Exception {
    // A bearer call may run with a session present, but token auth is stateless: its
    // SecurityContext is never persisted to that session (the #6343 root cause).
    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .session(session)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isOk());

    // Replaying that same server-side session (via .session(...); the JSESSIONID cookie below is
    // redundant in MockMvc) with a valid CSRF token but no bearer token stays unauthenticated.
    Cookie jsessionCookie = new Cookie("JSESSIONID", Objects.requireNonNull(session.getId()));
    Cookie csrfCookie = new Cookie(CSRF_COOKIE_NAME, "test-csrf-token");

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .session(session)
                .cookie(jsessionCookie, csrfCookie)
                .header(CSRF_HEADER_NAME, "test-csrf-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY)
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given pure bearer request, should not create session nor issue JSESSIONID")
  void given_pureBearerRequest_should_notCreateSessionNorIssueJsessionId() throws Exception {
    // The #6343 root cause: a pure bearer (stateless) call must neither establish a server-side
    // session nor emit a JSESSIONID for the client to replay - otherwise CSRF re-engages on the
    // next call and standards-compliant clients (Postman, httpx, requests) get 403 from the second
    // request onwards. No session is injected here so the absence is actually asserted.
    MvcResult result =
        mockMvc
            .perform(
                post(SCENARIO_SEARCH_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(SEARCH_BODY))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName("given pure auth cookie request, should not create session nor issue JSESSIONID")
  void given_pureAuthCookieRequest_should_notCreateSessionNorIssueJsessionId() throws Exception {
    // The auth-cookie form of TokenAuthenticationFilter is also a token authentication: like the
    // bearer header form, it must never establish a server-side session nor emit a JSESSIONID for
    // the client to replay.
    Cookie authCookie = new Cookie(AUTH_COOKIE_NAME, adminToken);

    MvcResult result =
        mockMvc.perform(get(ME_URI).cookie(authCookie)).andExpect(status().isOk()).andReturn();

    assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName("given raw token header request, should not create session nor issue JSESSIONID")
  void given_rawTokenHeaderRequest_should_notCreateSessionNorIssueJsessionId() throws Exception {
    // A raw (non-bearer-prefixed) Authorization header is the other stateless form
    // TokenAuthenticationFilter accepts; it must give the same no-session guarantee as the
    // bearer-prefixed form.
    MvcResult result =
        mockMvc
            .perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, adminToken))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getCookie("JSESSIONID")).isNull();
    assertThat(result.getRequest().getSession(false)).isNull();
  }

  @Test
  @DisplayName(
      "given valid session and a reverse-proxy Basic auth header, should authenticate via session")
  void given_sessionWithBasicAuthHeader_should_authenticateViaSession() throws Exception {
    // A reverse proxy in front of SSO/local login can inject its own Authorization: Basic ...
    // header on every request. TokenAuthenticationFilter only ever treats "Bearer <token>" or a
    // bare <token> as an OpenAEV credential, so a Basic header must not force the request onto the
    // stateless path - otherwise the session carrying the actual login is never loaded, and the
    // user is logged out right after logging in.
    Token currentAdminToken = tokenRepository.findByValue(adminToken).orElseThrow();
    User adminUser = currentAdminToken.getUser();
    String sessionId =
        sessionTestHelper.createAuthenticatedSession(
            buildAuthenticationToken(adminUser), adminUser.getId());
    Cookie sessionCookie = sessionTestHelper.cookieFor(sessionId);

    mockMvc
        .perform(
            get(ME_URI)
                .cookie(sessionCookie)
                .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_HEADER_VALUE))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("given only a Basic auth header, should return HTTP 401")
  void given_onlyBasicAuthHeader_should_returnUnauthorized() throws Exception {
    // A Basic header alone is not an OpenAEV credential - it must not be mistaken for a raw token
    // (TokenAuthenticationFilter's plain-token extractor would just fail to match it) nor for an
    // existing session, so the request stays unauthenticated.
    mockMvc
        .perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_HEADER_VALUE))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("given renewed token, should reject old bearer and accept new bearer")
  void given_renewedToken_should_reject_old_bearer_and_accept_new_bearer() throws Exception {
    // -- ARRANGE --
    Token currentAdminToken = tokenRepository.findByValue(adminToken).orElseThrow();
    String refreshBody =
        """
        {
          "token_id": "%s"
        }
        """
            .formatted(currentAdminToken.getId());

    // -- ACT --
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/api/me/token/refresh")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody))
            .andExpect(status().isOk())
            .andReturn();

    String refreshedToken =
        JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.token_value");
    assertThat(refreshedToken).isNotBlank().isNotEqualTo(adminToken);

    // -- ASSERT --
    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post(SCENARIO_SEARCH_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SEARCH_BODY))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "a pre-rotation session cannot retrieve the replacement installer token after rotation")
  void given_preRotationSession_should_notRetrieveReplacementTokenAfterRotation() throws Exception {
    // -- ARRANGE --
    // Bootstrap the tenant's service account and model a session that already exists while it
    // holds the soon-to-be-rotated token - the thing the admin rotation remediation must revoke,
    // regardless of how the session came to exist (token authentications are themselves stateless
    // post-fix: see AppSecurityConfig#hasTokenCredential).
    serviceAccountPrivilegeService.ensurePrivilegedUserExists(Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();
    User serviceAccount =
        serviceAccountPrivilegeService
            .getUserServiceAccountByTenant(Tenant.DEFAULT_TENANT_UUID)
            .orElseThrow();
    String sessionId =
        sessionTestHelper.createAuthenticatedSession(
            buildAuthenticationToken(serviceAccount), serviceAccount.getId());
    Cookie sessionCookie = sessionTestHelper.cookieFor(sessionId);
    String installerTokenUri = AGENT_URI + "/installer/openaev/token";

    // Pre-rotation: the session alone (no token ever presented on this request) reaches the
    // installer token endpoint, proving it is really an authenticated, reusable session.
    mockMvc.perform(get(installerTokenUri).cookie(sessionCookie)).andExpect(status().isOk());

    // -- ACT --
    // The admin remediation path: rotate the tenant's service-account token.
    serviceAccountPrivilegeService.rotateTokenForTenant(Tenant.DEFAULT_TENANT_UUID);
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    // Replaying the pre-rotation session cannot retrieve the replacement token: rotation killed
    // the session itself, it is not merely that the old token value stopped working.
    mockMvc
        .perform(get(installerTokenUri).cookie(sessionCookie))
        .andExpect(status().isUnauthorized());
  }
}
