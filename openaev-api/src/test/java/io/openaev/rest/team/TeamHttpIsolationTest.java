package io.openaev.rest.team;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code teams} activated, the request scope alone isolates the table
 * through the real {@link TeamApi} endpoints.
 *
 * <p>Every read exercised here carries NO tenant predicate of its own: {@code optionsById} goes
 * through {@code findAllById}, and {@code find} and {@code search} go through a Criteria
 * specification that never names a tenant. They are therefore scoped by the statement inspector and
 * by nothing else, which is what makes this class an activation proof: emptying the armed list in
 * the {@code @TestPropertySource} above turns the cross-tenant assertions red. The by-id read path
 * is deliberately NOT used here, because it carries an explicit tenant predicate of its own and
 * would stay green with the table disarmed.
 *
 * <p>Each test method stays on a single tenant path or header so the per-request scope is resolved
 * once: changing it inside the test transaction would hit the nesting guard in {@code
 * TenantScopeTransactionAspect}.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=teams")
@WithMockUser(isAdmin = true)
@DisplayName("Teams read isolation through the real HTTP endpoints")
class TeamHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_OPTIONS_URI = "/api/tenants/{tenantId}/teams/options";
  private static final String TENANT_FIND_URI = "/api/tenants/{tenantId}/teams/find";
  private static final String TENANT_SEARCH_URI = "/api/tenants/{tenantId}/teams/search";
  private static final String OPTIONS_URI = TeamApi.TEAM_URI + "/options";
  private static final String SEARCH_URI = TeamApi.TEAM_URI + "/search";
  private static final String SEED_PREFIX = "team-http-iso-team-";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String teamA;
  private String teamB;

  @BeforeEach
  void seedTwoTenantsWithOneTeamEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("team-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("team-http-iso-b").getId();
    teamA = seedTeam(tenantA, SEED_PREFIX + "a");
    teamB = seedTeam(tenantB, SEED_PREFIX + "b");
  }

  @Test
  @DisplayName("given the seed, when counting raw rows, then one team exists per tenant")
  void given_seed_should_haveOneTeamPerTenant() {
    // Arrange, Act
    long count = rawCount(teamA, teamB);

    // Assert
    assertEquals(2L, count, "both seeded teams must exist before anything is asserted on them");
  }

  @Nested
  @DisplayName("options by id, a read with no tenant predicate of its own")
  class OptionsById {

    @Test
    @DisplayName("given tenant A's path, when asking for both ids, then only A's team is returned")
    void given_tenantAPath_should_returnOnlyTenantATeamInOptions() throws Exception {
      // Arrange
      String body = asJsonString(List.of(teamA, teamB));

      // Act
      String response = postUnderTenantPath(TENANT_OPTIONS_URI, body, tenantA);

      // Assert
      assertTrue(response.contains(teamA), "A's team must be an option under A's path");
      assertFalse(response.contains(teamB), "B's team must not be an option under A's path");
    }

    @Test
    @DisplayName("given tenant B's path, when asking for both ids, then only B's team is returned")
    void given_tenantBPath_should_returnOnlyTenantBTeamInOptions() throws Exception {
      // Arrange
      String body = asJsonString(List.of(teamA, teamB));

      // Act
      String response = postUnderTenantPath(TENANT_OPTIONS_URI, body, tenantB);

      // Assert
      assertTrue(response.contains(teamB), "B's team must be an option under B's path");
      assertFalse(response.contains(teamA), "A's team must not be an option under B's path");
    }

    @Test
    @DisplayName(
        "given the header route selecting tenant A, when asking for both ids, then only A's team"
            + " is returned")
    void given_headerRouteSelectingTenantA_should_returnOnlyTenantATeamInOptions()
        throws Exception {
      // Arrange
      String body = asJsonString(List.of(teamA, teamB));

      // Act
      String response = postWithTenantHeader(OPTIONS_URI, body, tenantA);

      // Assert
      assertTrue(response.contains(teamA), "A's team must be an option when A is selected");
      assertFalse(response.contains(teamB), "B's team must not be an option when A is selected");
    }
  }

  @Nested
  @DisplayName("find and search, specification reads with no tenant predicate of their own")
  class FindAndSearch {

    @Test
    @DisplayName("given tenant A's path, when finding both ids, then only A's team is returned")
    void given_tenantAPath_should_findOnlyTenantATeam() throws Exception {
      // Arrange
      String body = asJsonString(List.of(teamA, teamB));

      // Act
      String response = postUnderTenantPath(TENANT_FIND_URI, body, tenantA);

      // Assert
      assertTrue(response.contains(teamA), "A's team must be found under A's path");
      assertFalse(response.contains(teamB), "B's team must not be found under A's path");
    }

    @Test
    @DisplayName("given tenant A's path, when searching teams, then only A's team is listed")
    void given_tenantAPath_should_searchOnlyTenantATeam() throws Exception {
      // Arrange
      String body = asJsonString(PaginationFixture.getDefault().textSearch(SEED_PREFIX).build());

      // Act
      String response = postUnderTenantPath(TENANT_SEARCH_URI, body, tenantA);

      // Assert
      assertTrue(response.contains(teamA), "A's team must be in A's search results");
      assertFalse(response.contains(teamB), "B's team must not be in A's search results");
    }

    @Test
    @DisplayName(
        "given the header route selecting tenant B, when searching teams, then only B's team is"
            + " listed")
    void given_headerRouteSelectingTenantB_should_searchOnlyTenantBTeam() throws Exception {
      // Arrange
      String body = asJsonString(PaginationFixture.getDefault().textSearch(SEED_PREFIX).build());

      // Act
      String response = postWithTenantHeader(SEARCH_URI, body, tenantB);

      // Assert
      assertTrue(response.contains(teamB), "B's team must be in B's search results");
      assertFalse(response.contains(teamA), "A's team must not be in B's search results");
    }
  }

  private String postUnderTenantPath(String uri, String body, String tenantId) throws Exception {
    return mvc.perform(
            post(uri, tenantId).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String postWithTenantHeader(String uri, String body, String tenantId) throws Exception {
    return mvc.perform(
            post(uri)
                .header("X-Tenant-Ids", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * Ground truth, bypassing the scope: raw JDBC on the test's own connection sees the uncommitted
   * seed, and the rewriter never touches a statement it did not generate. Going through the entity
   * manager here would be rewritten and would report zero on a thread with no scope.
   */
  private long rawCount(String firstTeamId, String secondTeamId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM teams WHERE team_id IN (?, ?)")) {
                statement.setString(1, firstTeamId);
                statement.setString(2, secondTeamId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }

  private String seedTeam(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO teams (team_id, team_name, team_contextual, team_created_at,"
                + " team_updated_at, tenant_id) VALUES (:id, :name, false, now(), now(), :tenant)")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }
}
