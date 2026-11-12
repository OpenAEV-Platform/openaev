package io.openaev.rest.team;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The team update links the team to the tags of the team's own tenant.
 *
 * <p>On the non-prefixed route {@code X-Tenant-Ids} can name several tenants. The tag ids of the
 * body resolve in the tenant of the team alone. The links are read from {@code teams_tags} through
 * JDBC on the test connection, which the statement inspector does not rewrite. The class arms the
 * production list.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Team update links only the tags of the team tenant")
class TeamTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String TEAMS_TAGS = "SELECT tag_id FROM teams_tags WHERE team_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ObjectMapper objectMapper;

  private String tenantB;
  private String tagInB;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("team-tag-link-b").getId();
    tagInB = saveTag(tagRepository, tenantB);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a team of B when the body names a tag of each tenant then only the tag of B is"
            + " linked")
    void given_teamOfB_should_linkOnlyTheTagOfB() throws Exception {
      // -- Arrange --
      String team = saveTeamInB(null);
      armRequest(team);

      // -- Act --
      putOnHeaderRoute(team, teamBody(List.of(tagInB, tagInDefault))).andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, TEAMS_TAGS, team))
          .as("tags linked to the team of B: kept %s, dropped %s", tagInB, tagInDefault)
          .containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Body with a null tag list")
  class BodyWithNullTagList {

    @Test
    @DisplayName(
        "given a team update with a null tag list on a team of B holding a tag of B then the"
            + " request is refused and the link stays")
    void given_teamUpdateWithNullTagList_should_refuseAndKeepTheLinks() throws Exception {
      // -- Arrange --
      String team = saveTeamInB(tagInB);
      armRequest(team);

      // -- Act --
      int status = putOnHeaderRoute(team, teamBody(null)).andReturn().getResponse().getStatus();

      // -- Assert --
      assertThat(status).as("status of a team update with a null tag list").isEqualTo(400);
      assertThat(column(entityManager, TEAMS_TAGS, team)).containsExactly(tagInB);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a team of B when the body under the path of B names a tag of B then the tag is"
            + " linked")
    void given_teamOfB_should_linkTheTagOfBOnThePrefixedRoute() throws Exception {
      // -- Arrange --
      String team = saveTeamInB(null);
      armRequest(team);

      // -- Act --
      mvc.perform(
              put(TENANT_PREFIX + "/teams/{id}", tenantB, team)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(teamBody(List.of(tagInB))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, TEAMS_TAGS, team)).containsExactly(tagInB);
    }
  }

  // -- Helpers --

  private ResultActions putOnHeaderRoute(String team, Map<String, Object> body) throws Exception {
    return mvc.perform(
        put(TEAM_URI + "/" + team)
            .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
            .with(csrf()));
  }

  private Map<String, Object> teamBody(List<String> tags) {
    Map<String, Object> body = new HashMap<>();
    body.put("team_name", "team-tag-link-" + UUID.randomUUID());
    body.put("team_tags", tags);
    return body;
  }

  private String saveTeamInB(String tagId) {
    Team team = TeamFixture.createTeamWithName("team-tag-link-" + UUID.randomUUID());
    team.setTenant(new Tenant(tenantB));
    if (tagId != null) {
      team.setTags(new HashSet<>(tagRepository.findAllByIdInAndTenantId(List.of(tagId), tenantB)));
    }
    String id = teamRepository.save(team).getId();
    entityManager.flush();
    entityManager.clear();
    return id;
  }

  /**
   * Each tag and the team carry the tenant the scenario relies on, then the ambient tenant is reset
   * to the default one the request thread of the header route carries.
   */
  private void armRequest(String team) {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInB))
        .containsExactly(tenantB);
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInDefault))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(column(entityManager, "SELECT tenant_id FROM teams WHERE team_id = ?", team))
        .containsExactly(tenantB);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}
