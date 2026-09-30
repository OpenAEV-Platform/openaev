package io.openaev.rest.organization;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.ZipUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A user is platform-level (member of several tenants) but points at ONE organization, owned by one
 * tenant. An export produced in the context of a tenant must only carry that tenant's
 * organizations: a player's organization owned by another tenant must be left out, never exported
 * and never allowed to break the export.
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=organizations,tags")
@DisplayName("Organization tenant isolation in exports")
class OrganizationExportIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Resource private ObjectMapper mapper;

  private Tenant tenantA;
  private Tenant tenantB;
  private String prefix;

  @BeforeEach
  void setUp() throws Exception {
    organizationComposer.reset();
    tagComposer.reset();
    userComposer.reset();
    teamComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectComposer.reset();
    tenantA = tenantHelper.createTenant("organization-export-a");
    tenantB = tenantHelper.createTenant("organization-export-b");
    // Onboarding enrolls its creator; each test grants only the memberships it exercises.
    String userId = testUserHolder.get().getId();
    for (Tenant tenant : List.of(tenantA, tenantB)) {
      tenantRepository.removeUserFromTenant(userId, tenant.getId());
      tenantMembershipCacheManager.evict(userId, tenant.getId());
    }
    prefix = "organization-export-" + UUID.randomUUID();
  }

  /**
   * Seeds, in {@code ownerTenant}, a team owned by an organization of that tenant, holding a player
   * who is a member of both tenants but belongs to an organization of {@code foreignTenant}.
   */
  private Fixture seedTeam(Tenant ownerTenant, Tenant foreignTenant) {
    Organization ownOrganization = seedOrganization(ownerTenant, "own");
    Organization foreignOrganization = seedOrganization(foreignTenant, "foreign");

    User player = UserFixture.getUser("Player", "Shared", prefix + "@filigran.io");
    player.setOrganization(foreignOrganization);
    player = userComposer.forUser(player).persist().get();
    tenantRepository.addUserToTenant(player.getId(), ownerTenant.getId());
    tenantRepository.addUserToTenant(player.getId(), foreignTenant.getId());

    Team team = TeamFixture.getEmptyTeam();
    team.setName(prefix + "-team");
    team.setTenant(ownerTenant);
    team.setOrganization(ownOrganization);
    team.setUsers(new ArrayList<>(List.of(player)));
    TeamComposer.Composer teamWrapper = teamComposer.forTeam(team).persist();
    return new Fixture(teamWrapper, ownOrganization, foreignOrganization);
  }

  private Organization seedOrganization(Tenant tenant, String suffix) {
    Tag tag = TagFixture.getTagWithText(prefix + "-" + suffix + "-tag");
    tag.setTenant(tenant);
    tag = tagComposer.forTag(tag).persist().get();
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setName(prefix + "-" + suffix + "-organization");
    organization.setTenant(tenant);
    organization.setTags(new HashSet<>(Set.of(tag)));
    return organizationComposer.forOrganization(organization).persist().get();
  }

  /** Detaches everything so the export resolves the organizations lazily, as in production. */
  private void clearPersistenceContext() {
    entityManager.flush();
    entityManager.clear();
  }

  private record Fixture(
      TeamComposer.Composer team, Organization ownOrganization, Organization foreignOrganization) {}

  private String exportEntry(MockHttpServletRequestBuilder request, String entryName)
      throws Exception {
    byte[] zip =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    return ZipUtils.getZipEntry(zip, entryName + ".json", ZipUtils::streamToString);
  }

  private void assertOnlyOwnOrganization(String json, String organizationsKey, Fixture fixture)
      throws Exception {
    JsonNode organizations = mapper.readTree(json).get(organizationsKey);
    assertThat(organizations).as(organizationsKey).isNotNull();
    List<String> exportedIds =
        StreamSupport.stream(organizations.spliterator(), false)
            .map(organization -> organization.get("organization_id").asText())
            .distinct()
            .toList();
    assertThat(exportedIds).containsExactly(fixture.ownOrganization().getId());
    // Neither the foreign organization's name nor its tag may leak into the tenant's export.
    assertThat(json)
        .contains(fixture.ownOrganization().getName())
        .doesNotContain(fixture.foreignOrganization().getName())
        .doesNotContain(prefix + "-foreign-tag");
  }

  private static String tenantUri(Tenant tenant, String uri) {
    return uri.replace("/api/", "/api/tenants/" + tenant.getId() + "/");
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Simulation export")
  class SimulationExport {

    private Exercise seedExercise(Tenant ownerTenant, Fixture fixture) {
      Exercise exercise = ExerciseFixture.createDefaultCrisisExercise();
      exercise.setName(prefix + "-simulation");
      exercise.setTenant(ownerTenant);
      return exerciseComposer
          .forExercise(exercise)
          .withTeam(fixture.team())
          .withTeamUsers()
          .persist()
          .get();
    }

    @Test
    @DisplayName("Caller member of A only: the player's organization of B is left out")
    void given_callerInOwnerTenantOnly_should_excludeForeignOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Fixture fixture = seedTeam(tenantA, tenantB);
      Exercise exercise = seedExercise(tenantA, fixture);
      clearPersistenceContext();

      // Act
      String json =
          exportEntry(
              get(tenantUri(tenantA, EXERCISE_URI) + "/" + exercise.getId() + "/export")
                  .queryParam("isWithPlayers", "true")
                  .queryParam("isWithTeams", "true"),
              exercise.getName());

      // Assert
      assertOnlyOwnOrganization(json, "exercise_organizations", fixture);
    }

    @Test
    @DisplayName(
        "Caller member of both tenants: the export still holds the simulation's tenant only")
    void given_callerInBothTenants_should_exportOnlySimulationTenantOrganizations()
        throws Exception {
      // Arrange: the unprefixed route resolves a scope covering both tenants, so the scope alone
      // would let the foreign organization through. The simulation lives in the default tenant
      // because exercises are still v1-filtered on the ambient (default) tenant on that route.
      Tenant defaultTenant = tenantRepository.findById(DEFAULT_TENANT_UUID).orElseThrow();
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
      tenantHelper.attachCurrentUserToTenant(tenantB.getId());
      Fixture fixture = seedTeam(defaultTenant, tenantB);
      Exercise exercise = seedExercise(defaultTenant, fixture);
      clearPersistenceContext();

      // Act
      String json =
          exportEntry(
              get(EXERCISE_URI + "/" + exercise.getId() + "/export")
                  .queryParam("isWithPlayers", "true")
                  .queryParam("isWithTeams", "true"),
              exercise.getName());

      // Assert
      assertOnlyOwnOrganization(json, "exercise_organizations", fixture);
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Scenario export")
  class ScenarioExport {

    @Test
    @DisplayName("Caller member of A only: the player's organization of B is left out")
    void given_callerInOwnerTenantOnly_should_excludeForeignOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Fixture fixture = seedTeam(tenantA, tenantB);
      Scenario scenario =
          ScenarioFixture.getScenario(new ArrayList<>(List.of(fixture.team().get())), null);
      scenario.setName(prefix + "-scenario");
      scenario.setTenant(tenantA);
      scenario = scenarioComposer.forScenario(scenario).persist().get();
      clearPersistenceContext();

      // Act
      String json =
          exportEntry(
              get(tenantUri(tenantA, SCENARIO_URI) + "/" + scenario.getId() + "/export")
                  .queryParam("isWithPlayers", "true")
                  .queryParam("isWithTeams", "true"),
              scenario.getName());

      // Assert
      assertOnlyOwnOrganization(json, "scenario_organizations", fixture);
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Inject export")
  class InjectExport {

    @Test
    @DisplayName("Caller member of A only: the player's organization of B is left out")
    void given_callerInOwnerTenantOnly_should_excludeForeignOrganization() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(tenantA.getId());
      Fixture fixture = seedTeam(tenantA, tenantB);
      Inject inject = InjectFixture.getInjectWithoutContract();
      inject.setTenant(tenantA);
      inject = injectComposer.forInject(inject).withTeam(fixture.team()).persist().get();
      clearPersistenceContext();

      // Act
      String json =
          exportEntry(
              post(tenantUri(tenantA, INJECT_URI) + "/" + inject.getId() + "/inject_export")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"options\":{\"with_players\":true,\"with_teams\":true,"
                          + "\"with_variable_values\":false}}")
                  .with(csrf()),
              "injects");

      // Assert
      assertOnlyOwnOrganization(json, "inject_organizations", fixture);
    }
  }
}
