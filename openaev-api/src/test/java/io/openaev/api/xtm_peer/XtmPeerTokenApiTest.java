package io.openaev.api.xtm_peer;

import static io.openaev.api.xtm_peer.XtmPeerTokenApi.XTM_PEER_TOKENS_URI;
import static io.openaev.rest.user.MeApi.ME_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.TokenRepository;
import io.openaev.service.UserService;
import io.openaev.service.account.ReservedKeyValidator;
import io.openaev.service.account.ReservedServiceAccount;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(Lifecycle.PER_CLASS)
@Transactional
@DisplayName("XTM peer tokens API")
class XtmPeerTokenApiTest extends IntegrationTest {

  private static final String EMAIL = "opencrq@xtm.openaev.filigran.io";
  private static final String ISSUE_BODY =
      """
      {"consumer": "opencrq", "name": "OpenCRQ", "email": "%s", "token_name": "xtm-peer-opencrq"}
      """;
  private static final String REVOKE_BODY =
      """
      {"email": "%s", "token_name": "xtm-peer-opencrq"}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private UserService userService;
  @Autowired private TokenRepository tokenRepository;
  @Autowired private EntityManager entityManager;

  @Value("${openbas.admin.token:${openaev.admin.token:#{null}}}")
  private String adminToken;

  private String issue(String bearer, String email, int expectedStatus) throws Exception {
    String body =
        mockMvc
            .perform(
                post(XTM_PEER_TOKENS_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(ISSUE_BODY.formatted(email)))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // A request commits its own transaction in production; the test transaction spans the requests,
    // so the account is written here, where the ambient scope is set, and not by the next request.
    entityManager.flush();
    return body;
  }

  private void meWith(String token, int expectedStatus) throws Exception {
    mockMvc
        .perform(get(ME_URI).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().is(expectedStatus));
  }

  @Test
  @DisplayName("refuses a bearer that is not the platform admin token with 401")
  void wrongTokenIsUnauthorized() throws Exception {
    issue(UUID.randomUUID().toString(), EMAIL, 401);
    String peerToken = JsonPath.read(issue(adminToken, EMAIL, 200), "$.token");
    // A valid token of another account is not the admin token either.
    issue(peerToken, "xtm-one@xtm.openaev.filigran.io", 401);
    mockMvc
        .perform(
            post(XTM_PEER_TOKENS_URI + "/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + peerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REVOKE_BODY.formatted(EMAIL)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("creates the account in the default tenant and answers the contract")
  void issueCreatesTheAccount() throws Exception {
    String body = issue(adminToken, EMAIL, 200);

    String userId = JsonPath.read(body, "$.user_id");
    assertThat((String) JsonPath.read(body, "$.email")).isEqualTo(EMAIL);
    assertThat((String) JsonPath.read(body, "$.name")).isEqualTo("OpenCRQ");
    assertThat((String) JsonPath.read(body, "$.token_name")).isEqualTo("xtm-peer-opencrq");
    String token = JsonPath.read(body, "$.token");
    User user = userService.findByEmailIgnoreCase(EMAIL).orElseThrow();
    assertThat(user.getId()).isEqualTo(userId);
    assertThat(user.isAdmin()).isFalse();
    assertThat(ReservedKeyValidator.isReservedUserEmail(EMAIL)).isTrue();
    assertThat(tokenRepository.findByValue(token)).isPresent();
  }

  @Test
  @DisplayName("an idempotent re-call keeps the account and rotates the token")
  void reissueRotatesTheToken() throws Exception {
    String first = issue(adminToken, EMAIL, 200);
    String second = issue(adminToken, EMAIL, 200);

    assertThat((String) JsonPath.read(second, "$.user_id"))
        .isEqualTo(JsonPath.read(first, "$.user_id"));
    String oldToken = JsonPath.read(first, "$.token");
    String newToken = JsonPath.read(second, "$.token");
    assertThat(newToken).isNotEqualTo(oldToken);
    assertThat(tokenRepository.findByValue(oldToken)).isEmpty();
    assertThat(tokenRepository.findByValue(newToken)).isPresent();
  }

  @Test
  @DisplayName("revoke deletes the tokens and keeps the account")
  void revokeKeepsTheAccount() throws Exception {
    String token = JsonPath.read(issue(adminToken, EMAIL, 200), "$.token");

    String body =
        mockMvc
            .perform(
                post(XTM_PEER_TOKENS_URI + "/revoke")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REVOKE_BODY.formatted(EMAIL)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((Boolean) JsonPath.read(body, "$.revoked")).isTrue();
    assertThat(tokenRepository.findByValue(token)).isEmpty();
    assertThat(userService.findByEmailIgnoreCase(EMAIL)).isPresent();
    meWith(token, 401);
  }

  @Test
  @DisplayName("refuses an email outside the peer identity space")
  void refusesAPersonEmail() throws Exception {
    issue(adminToken, "jane.doe@example.com", 400);
    assertThat(userService.findByEmailIgnoreCase("jane.doe@example.com")).isEmpty();
  }

  @Test
  @DisplayName("refuses a consumer the read-only peer role was not built for")
  void refusesAnUnknownConsumer() throws Exception {
    String email = "opencti@xtm.oaev.filigran.io";
    mockMvc
        .perform(
            post(XTM_PEER_TOKENS_URI)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"consumer": "opencti", "name": "OpenCTI", "email": "%s",
                     "token_name": "xtm-opencti"}
                    """
                        .formatted(email)))
        .andExpect(status().isBadRequest());
    assertThat(userService.findByEmailIgnoreCase(email)).isEmpty();
  }

  @Test
  @DisplayName("refuses an email that names another consumer than the one declared")
  void refusesAnotherConsumersEmail() throws Exception {
    issue(adminToken, "xtm-one@xtm.openaev.filigran.io", 400);
    assertThat(userService.findByEmailIgnoreCase("xtm-one@xtm.openaev.filigran.io")).isEmpty();
  }

  @Test
  @DisplayName("the peer role reads only: no MANAGE or DELETE capability, the snapshot read in")
  void thePeerRoleIsReadOnly() {
    assertThat(ReservedServiceAccount.XTM_PEER.access().capabilities())
        .allSatisfy(
            capability ->
                assertThat(capability.name())
                    .doesNotStartWith("MANAGE_")
                    .doesNotStartWith("DELETE_"))
        .contains(Capability.ACCESS_SNAPSHOT_OBSERVATION)
        .doesNotContain(
            Capability.ACCESS_PAYLOADS,
            Capability.ACCESS_DOCUMENTS,
            Capability.ACCESS_SECURITY_PLATFORMS);
  }

  @Test
  @DisplayName("the peer token is refused on a write route (403)")
  void thePeerTokenCannotWrite() throws Exception {
    String token = JsonPath.read(issue(adminToken, EMAIL, 200), "$.token");
    mockMvc
        .perform(
            post("/api/scenarios")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario_name\": \"written by a peer\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("the peer group does not receive every marking of the tenant")
  void thePeerGroupReceivesNoNewMarking() throws Exception {
    issue(adminToken, EMAIL, 200);
    // The membership is written as one users_groups row: read the account back from the database,
    // not from the instance this test's session already holds.
    entityManager.flush();
    entityManager.clear();
    User user = userService.findByEmailIgnoreCase(EMAIL).orElseThrow();
    assertThat(user.getUnscopedGroups()).isNotEmpty().noneMatch(Group::isAutoNewMarking);
  }
}
