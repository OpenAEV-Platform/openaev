package io.openaev.rest.team;

import static io.openaev.rest.team.TeamApi.TENANT_TEAM_URI;
import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.OrganizationFixture.createDefaultOrganisation;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static io.openaev.utils.fixtures.TeamFixture.createTeamWithName;
import static io.openaev.utils.fixtures.UserFixture.getUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.team.form.TeamUpdateInput;
import io.openaev.rest.user.form.player.PlayerInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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

/**
 * With {@code tags} and {@code teams} on v2 tenant isolation, {@code GET /teams/{id}} and {@code
 * GET /teams/{id}/players} return raw {@code Team} and {@code User} entities and would serialize
 * their lazy associations open-in-view, AFTER the controller transaction has committed and {@code
 * app.current_tenants} is cleared: a lazy load on an isolated table then fails closed to an empty
 * array. The team and player edit forms are filled from these responses, so saving a team or a
 * player erased its tags. {@link io.openaev.service.TeamService} loads them inside the scoped
 * transaction to prevent it.
 *
 * <p>The tests cover every lazy association these responses serialize that can be seeded without an
 * inject: team tags, users, organization, simulations, scenarios and simulation players; player
 * tags, teams, organization and simulation players. They run with the production list of isolated
 * tables, read from {@code application.properties}: activating another table that one of these
 * associations reaches makes them fail until the association is loaded inside the scope too.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding, checks and cleanup therefore run in committed transactions opened through
 * {@link TenantScopedTransaction}, scoped to the test tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Team and team player endpoints serialize every lazy association")
class TeamTagsSerializationTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String activeTables = props.getProperty("openaev.tenant.active-tables", "");
    registry.add("openaev.tenant.active-tables", () -> activeTables);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TagComposer tagComposer;
  @Autowired private OrganizationComposer organizationComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;

  private String tenant;
  private final List<String> seededScenarios = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    organizationComposer.reset();
    userComposer.reset();
    teamComposer.reset();
    exerciseComposer.reset();
    seededScenarios.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("team-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The composers' rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          scenarioRepository.deleteAllById(seededScenarios);
          exerciseRepository.deleteAllById(
              exerciseComposer.generatedItems.stream().map(Exercise::getId).toList());
          teamRepository.deleteAllById(
              teamComposer.generatedItems.stream().map(Team::getId).toList());
          userRepository.deleteAllById(
              userComposer.generatedItems.stream().map(User::getId).toList());
          organizationRepository.deleteAllById(
              organizationComposer.generatedItems.stream().map(Organization::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Get a team")
  class GetTeam {

    @Test
    @DisplayName(
        "given a team carrying every lazy association when it is read by id then all of them are"
            + " serialized")
    void
        given_a_team_carrying_every_lazy_association_when_it_is_read_by_id_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededTeam seeded = seedTeamWithEveryLazyAssociation();

      // -- Act & Assert --
      // Without the in-scope load the lazy team_tags serializes as [] here: the post-commit
      // open-in-view load runs with app.current_tenants cleared and fails closed.
      expectEveryTeamLazyAssociation(getTeam(seeded.team()), seeded);
    }
  }

  @Nested
  @DisplayName("Get the players of a team")
  class GetTeamPlayers {

    @Test
    @DisplayName(
        "given a player carrying every lazy association when the team players are read then all of"
            + " them are serialized")
    void
        given_a_player_carrying_every_lazy_association_when_the_team_players_are_read_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededTeam seeded = seedTeamWithEveryLazyAssociation();

      // -- Act & Assert --
      // Without the in-scope load user_tags and user_teams serialize as [] here: tags and teams
      // are both isolated tables.
      expectEveryPlayerLazyAssociation(getTeamPlayers(seeded.team()), seeded);
    }
  }

  @Nested
  @DisplayName("Edit a team")
  class EditTeam {

    @Test
    @DisplayName(
        "given a tagged team when a tag is added through the edit form then the existing tag is"
            + " kept")
    void
        given_a_tagged_team_when_a_tag_is_added_through_the_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      SeededTeam seeded = seedTeamWithEveryLazyAssociation();
      Team team = seeded.team();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(team.getTags()));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the get-by-id response, then the new tag is
      // added. The response must therefore show every existing link: an empty tag list here is
      // what erased the tags before the fix.
      expectEveryTeamLazyAssociation(getTeam(team), seeded);
      mvc.perform(
              put(TENANT_TEAM_URI + "/{teamId}", tenant, team.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(teamEditInput(team, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            Team saved = teamRepository.findById(team.getId()).orElseThrow();
            assertThat(tagIds(saved.getTags())).containsExactlyInAnyOrderElementsOf(editedTags);
            assertThat(saved.getOrganization().getId()).isEqualTo(seeded.organizationId());
            return null;
          });
    }

    private TeamUpdateInput teamEditInput(Team team, List<String> tagIds) {
      TeamUpdateInput input = new TeamUpdateInput();
      input.setName(team.getName());
      input.setDescription(team.getDescription());
      input.setOrganizationId(team.getOrganization().getId());
      input.setTagIds(tagIds);
      return input;
    }
  }

  @Nested
  @DisplayName("Edit a player")
  class EditPlayer {

    @Test
    @DisplayName(
        "given a tagged player when a tag is added through the edit form then the existing tag is"
            + " kept")
    void
        given_a_tagged_player_when_a_tag_is_added_through_the_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      SeededTeam seeded = seedTeamWithEveryLazyAssociation();
      User player = seeded.player();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(player.getTags()));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the team players page: the player edit form is filled from the team players
      // response, then the new tag is added.
      expectEveryPlayerLazyAssociation(getTeamPlayers(seeded.team()), seeded);
      mvc.perform(
              put(PLAYER_URI + "/{userId}", player.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(playerEditInput(player, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            User saved = userRepository.findById(player.getId()).orElseThrow();
            assertThat(tagIds(saved.getTags())).containsExactlyInAnyOrderElementsOf(editedTags);
            assertThat(saved.getOrganization().getId()).isEqualTo(seeded.organizationId());
            return null;
          });
    }

    private PlayerInput playerEditInput(User player, List<String> tagIds) {
      PlayerInput input = new PlayerInput();
      input.setEmail(player.getEmail());
      input.setFirstname(player.getFirstname());
      input.setLastname(player.getLastname());
      input.setOrganizationId(player.getOrganization().getId());
      input.setTagIds(tagIds);
      return input;
    }
  }

  // -- SEEDING --

  /**
   * A team with a tag, an organization and one player, linked to a simulation (with its simulation
   * player) and to a scenario. The player has its own tag and the same organization. Team inject
   * expectations and player communications are left out: they need an inject and are not part of
   * any edit form.
   */
  private record SeededTeam(
      Team team, User player, String organizationId, String exerciseId, String scenarioId) {}

  private SeededTeam seedTeamWithEveryLazyAssociation() {
    return inTenantScope(
        () -> {
          Organization organization = createDefaultOrganisation();
          organization.setTenant(new Tenant(tenant));
          OrganizationComposer.Composer organizationWrapper =
              organizationComposer.forOrganization(organization);

          String email = "team-player-" + UUID.randomUUID() + "@example.com";
          UserComposer.Composer playerWrapper =
              userComposer
                  .forUser(getUser("Team", "Player", email))
                  .withTag(newTag())
                  .withOrganization(organizationWrapper);

          Team team = createTeamWithName("team-" + UUID.randomUUID());
          team.setTenant(new Tenant(tenant));
          TeamComposer.Composer teamWrapper =
              teamComposer
                  .forTeam(team)
                  .withTag(newTag())
                  .withUser(playerWrapper)
                  .withOrganisation(organizationWrapper);

          Exercise exercise = createDefaultExercise();
          exercise.setTenant(new Tenant(tenant));
          exerciseComposer.forExercise(exercise).withTeam(teamWrapper).withTeamUsers().persist();

          Scenario scenario = createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenant));
          scenario.setTeams(new ArrayList<>(List.of(team)));
          String scenarioId = scenarioRepository.save(scenario).getId();
          seededScenarios.add(scenarioId);

          return new SeededTeam(
              team, playerWrapper.get(), organization.getId(), exercise.getId(), scenarioId);
        });
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("team-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The composers still read
   * the v1 thread-local tenant to attribute some rows, so it is set too.
   */
  private <T> T inTenantScope(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- CALLS AND ASSERTIONS --

  private ResultActions getTeam(Team team) throws Exception {
    return mvc.perform(
            get(TENANT_TEAM_URI + "/{teamId}", tenant, team.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  /** Reads the players of the team: the seeded team holds only one. */
  private ResultActions getTeamPlayers(Team team) throws Exception {
    return mvc.perform(
            get(TENANT_TEAM_URI + "/{teamId}/players", tenant, team.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private static void expectEveryTeamLazyAssociation(ResultActions response, SeededTeam seeded)
      throws Exception {
    response
        .andExpect(
            jsonPath("$.team_tags", containsInAnyOrder(tagIds(seeded.team().getTags()).toArray())))
        .andExpect(jsonPath("$.team_users", contains(seeded.player().getId())))
        .andExpect(jsonPath("$.team_organization").value(seeded.organizationId()))
        .andExpect(jsonPath("$.team_exercises", contains(seeded.exerciseId())))
        .andExpect(jsonPath("$.team_scenarios", contains(seeded.scenarioId())))
        .andExpect(jsonPath("$.team_exercises_users", hasSize(1)));
  }

  private static void expectEveryPlayerLazyAssociation(ResultActions response, SeededTeam seeded)
      throws Exception {
    response
        .andExpect(
            jsonPath(
                "$[0].user_tags", containsInAnyOrder(tagIds(seeded.player().getTags()).toArray())))
        .andExpect(jsonPath("$[0].user_teams", contains(seeded.team().getId())))
        .andExpect(jsonPath("$[0].user_organization").value(seeded.organizationId()))
        .andExpect(jsonPath("$[0].team_exercises_users", hasSize(1)));
  }

  private static List<String> tagIds(Set<Tag> tags) {
    return tags.stream().map(Tag::getId).toList();
  }
}
