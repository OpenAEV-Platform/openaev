package io.openaev.rest.team;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The isolation must not depend on the caller being an administrator. {@link TeamHttpIsolationTest}
 * runs as admin, with RBAC bypassed; this one runs as a non-admin that is a member of two tenants
 * and holds only the capability needed to read teams, and shows the same reads still return one
 * tenant's teams under its path. What isolates is the request scope, never the isAdmin flag.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=teams")
@WithMockUser(isAdmin = false)
@DisplayName("Teams isolation holds for a non-admin spanning two tenants")
class TeamNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_TEAMS = Set.of(Capability.ACCESS_TEAMS_AND_PLAYERS);
  private static final String SEED_PREFIX = "team-nonadmin-iso-team-";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String teamA;
  private String teamB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneTeamEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("team-nonadmin-iso-a", READ_TEAMS).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("team-nonadmin-iso-b", READ_TEAMS).getId();
    teamA = seedTeam(tenantA, SEED_PREFIX + "a");
    teamB = seedTeam(tenantB, SEED_PREFIX + "b");
  }

  @Test
  @DisplayName("given a non-admin under tenant A's path, when searching teams, then only A's team")
  void given_nonAdminUnderTenantAPath_should_searchOnlyTenantATeam() throws Exception {
    // Arrange
    String body = asJsonString(PaginationFixture.getDefault().textSearch(SEED_PREFIX).build());

    // Act
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/teams/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertTrue(response.contains(teamA), "A's team must be in A's search results");
    assertFalse(response.contains(teamB), "B's team must not be in A's search results");
  }

  @Test
  @DisplayName(
      "given a non-admin under tenant A's path, when asking for both option ids, then only A's team")
  void given_nonAdminUnderTenantAPath_should_returnOnlyTenantATeamInOptions() throws Exception {
    // Arrange
    String body = asJsonString(List.of(teamA, teamB));

    // Act
    String response =
        mvc.perform(
                post("/api/tenants/{tenantId}/teams/options", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    assertTrue(response.contains(teamA), "A's team must be an option under A's path");
    assertFalse(response.contains(teamB), "B's team must not be an option under A's path");
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
