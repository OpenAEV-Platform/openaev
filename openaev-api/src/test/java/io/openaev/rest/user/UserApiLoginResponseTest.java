package io.openaev.rest.user;

import static io.openaev.rest.user.MeApi.ME_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.UserFixture.RAW_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The endpoints that answer with the user before a request scope exists (the password login and the
 * password reset) describe the user as {@code GET /api/me} does on the plain route: with its teams
 * and tags of every tenant it is a member of. The lazy collections are initialized inside the
 * transaction, in the scope of the user's memberships, because they are otherwise read after the
 * commit with no scope and a tenant-scoped table answers no row.
 *
 * <p>NOT {@code @Transactional}: the data is committed, and the endpoints open their own
 * transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Login and password reset responses describe the user's teams and tags")
class UserApiLoginResponseTest extends ProductionTablesIntegrationTest {

  @MockitoBean private UserEventService userEventService;

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String tenantC;
  private User member;
  private String organizationOfDefault;
  private String organizationOfA;
  private final List<String> tagIds = new ArrayList<>();
  private final List<String> teamIds = new ArrayList<>();
  // Linked to the member, in a tenant the member does not belong to
  private final List<String> foreignTagIds = new ArrayList<>();
  private final List<String> foreignTeamIds = new ArrayList<>();

  @BeforeAll
  void seed() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("login-response-a").getId();
      tenantC = tenantHelper.createTenant("login-response-c").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    member =
        userRepository.save(
            UserFixture.getUser(
                "login-response",
                "login-response",
                "login-response-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    member.setPassword(UserFixture.ENCODED_PASSWORD);
    member = userRepository.save(member);
    for (String tenantId : List.of(Tenant.DEFAULT_TENANT_UUID, tenantA)) {
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> tenantRepository.addUserToTenant(member.getId(), tenantId));
      membershipCache.evict(member.getId(), tenantId);
    }
    // Two tags and a team in tenant A, one tag and one team in the default tenant
    seedTagsAndTeam(tenantA, 2, tagIds, teamIds);
    seedTagsAndTeam(Tenant.DEFAULT_TENANT_UUID, 1, tagIds, teamIds);
    // One tag and one team of a tenant the member does not belong to
    seedTagsAndTeam(tenantC, 1, foreignTagIds, foreignTeamIds);
    organizationOfDefault = seedOrganization(Tenant.DEFAULT_TENANT_UUID);
    organizationOfA = seedOrganization(tenantA);
    TenantContext.clearCurrentTenant();
  }

  private String seedOrganization(String tenantId) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantId));
    String organizationId =
        tenantTx.execute(
            TxCtx.forTenant(tenantId), () -> organizationRepository.save(organization).getId());
    jdbc.update(
        "INSERT INTO users_organizations (user_id, organization_id) VALUES (?, ?)",
        member.getId(),
        organizationId);
    return organizationId;
  }

  private void seedTagsAndTeam(
      String tenantId, int tags, List<String> tagSink, List<String> teamSink) {
    List<String[]> created = new ArrayList<>();
    TenantContext.setCurrentTenant(tenantId);
    try {
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () -> {
            for (int i = 0; i < tags; i++) {
              Tag tag = TagFixture.getTagWithText("login-response-" + UUID.randomUUID());
              tag.setTenant(new Tenant(tenantId));
              String tagId = tagRepository.save(tag).getId();
              tagSink.add(tagId);
              created.add(new String[] {"users_tags", "tag_id", tagId});
            }
            Team team = TeamFixture.createTeamWithName("login-response-" + UUID.randomUUID());
            team.setTenant(new Tenant(tenantId));
            String teamId = teamRepository.save(team).getId();
            teamSink.add(teamId);
            created.add(new String[] {"users_teams", "team_id", teamId});
          });
      // The links go in once the rows are committed: they reference them from another connection
      created.forEach(
          link ->
              jdbc.update(
                  "INSERT INTO %s (user_id, %s) VALUES (?, ?)".formatted(link[0], link[1]),
                  member.getId(),
                  link[2]));
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users_organizations WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_teams WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_tags WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", member.getId());
    List.of(teamIds, foreignTeamIds)
        .forEach(ids -> ids.forEach(id -> jdbc.update("DELETE FROM teams WHERE team_id = ?", id)));
    List.of(tagIds, foreignTagIds)
        .forEach(ids -> ids.forEach(id -> jdbc.update("DELETE FROM tags WHERE tag_id = ?", id)));
    jdbc.update("DELETE FROM organizations WHERE organization_id = ?", organizationOfDefault);
    tenantHelper.deleteCommittedTenants(tenantA, tenantC);
    TenantContext.clearCurrentTenant();
  }

  private JsonNode meOfMember() throws Exception {
    String body =
        mvc.perform(get(ME_URI).with(authentication(buildAuthenticationToken(member))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return mapper.readTree(body);
  }

  private static List<String> ids(JsonNode user, String field) {
    List<String> ids = new ArrayList<>();
    user.get(field).forEach(node -> ids.add(node.asText()));
    return new ArrayList<>(new TreeSet<>(ids));
  }

  private void assertDescribesTheUserAsMeDoes(JsonNode response) throws Exception {
    JsonNode me = meOfMember();
    assertThat(ids(me, "user_teams")).containsExactlyInAnyOrderElementsOf(teamIds);
    assertThat(ids(me, "user_tags")).containsExactlyInAnyOrderElementsOf(tagIds);
    assertThat(ids(response, "user_teams")).isEqualTo(ids(me, "user_teams"));
    assertThat(ids(response, "user_tags")).isEqualTo(ids(me, "user_tags"));
    assertThat(response.get("user_organization")).isEqualTo(me.get("user_organization"));
    // The ambient tenant of the plain route is the default one: its organization, not the one of A
    assertThat(me.get("user_organization").asText()).isEqualTo(organizationOfDefault);
    assertThat(response.get("user_organization").asText())
        .isEqualTo(organizationOfDefault)
        .isNotEqualTo(organizationOfA);
    // Linked to the user, in a tenant it does not belong to: read by neither
    assertThat(ids(me, "user_teams")).doesNotContainAnyElementsOf(foreignTeamIds);
    assertThat(ids(me, "user_tags")).doesNotContainAnyElementsOf(foreignTagIds);
    assertThat(ids(response, "user_teams")).doesNotContainAnyElementsOf(foreignTeamIds);
    assertThat(ids(response, "user_tags")).doesNotContainAnyElementsOf(foreignTagIds);
  }

  private int linkRowsOfMember(String table) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM %s WHERE user_id = ?".formatted(table),
        Integer.class,
        member.getId());
  }

  @Nested
  @DisplayName("the password login of a member of two tenants")
  class Login {

    @Test
    @DisplayName("given_validCredentials_should_sendTheTeamsAndTagsOfEveryTenantAsMeDoes")
    void given_validCredentials_should_sendTheTeamsAndTagsOfEveryTenantAsMeDoes() throws Exception {
      // Arrange
      LoginUserInput input =
          LoginUserInput.builder().login(member.getEmail()).password(RAW_PASSWORD).build();

      // Act
      String body =
          mvc.perform(
                  post("/api/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertDescribesTheUserAsMeDoes(mapper.readTree(body));
    }
  }

  @Nested
  @DisplayName("the password reset of a member of two tenants")
  class PasswordReset {

    @Test
    @DisplayName("given_aValidResetToken_should_sendTheTeamsAndTagsOfEveryTenantAsMeDoes")
    void given_aValidResetToken_should_sendTheTeamsAndTagsOfEveryTenantAsMeDoes() throws Exception {
      // Arrange: the token a reset mail carries, held by the service for the user
      String token = "reset-" + UUID.randomUUID();
      Object service = AopTestUtils.getUltimateTargetObject(userService);
      @SuppressWarnings("unchecked")
      Map<String, String> resetTokens =
          (Map<String, String>) ReflectionTestUtils.getField(service, "resetTokenMap");
      resetTokens.put(member.getId(), token);
      String password =
          "{\"password\":\"%s\",\"password_validation\":\"%s\"}"
              .formatted(RAW_PASSWORD, RAW_PASSWORD);

      // Act
      String body =
          mvc.perform(
                  post("/api/reset/" + token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(password)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertDescribesTheUserAsMeDoes(mapper.readTree(body));
      assertThat(linkRowsOfMember("users_tags")).isEqualTo(tagIds.size() + foreignTagIds.size());
      assertThat(linkRowsOfMember("users_teams")).isEqualTo(teamIds.size() + foreignTeamIds.size());
    }
  }

  @Nested
  @DisplayName("the password login of an administrator")
  class Administrator {

    @Test
    @DisplayName("given_anAdministratorMemberOfTwoTenants_should_sendTheOrganizationAsMeDoes")
    void given_anAdministratorMemberOfTwoTenants_should_sendTheOrganizationAsMeDoes()
        throws Exception {
      // Arrange: no roles view is built for an administrator, so the response serializes the
      // entity itself
      User admin =
          userRepository.save(
              UserFixture.getUser(
                  "login-admin",
                  "login-admin",
                  "login-admin-" + UUID.randomUUID() + "@unittests.invalid",
                  true));
      admin.setPassword(UserFixture.ENCODED_PASSWORD);
      User saved = userRepository.save(admin);
      for (String tenantId : List.of(Tenant.DEFAULT_TENANT_UUID, tenantA)) {
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () -> tenantRepository.addUserToTenant(saved.getId(), tenantId));
        membershipCache.evict(saved.getId(), tenantId);
      }
      jdbc.update(
          "INSERT INTO users_organizations (user_id, organization_id) VALUES (?, ?)",
          saved.getId(),
          organizationOfDefault);
      LoginUserInput input =
          LoginUserInput.builder().login(saved.getEmail()).password(RAW_PASSWORD).build();
      try {
        // Act
        String body =
            mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String me =
            mvc.perform(get(ME_URI).with(authentication(buildAuthenticationToken(saved))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert
        assertThat(mapper.readTree(me).get("user_organization").asText())
            .isEqualTo(organizationOfDefault);
        assertThat(mapper.readTree(body).get("user_organization").asText())
            .isEqualTo(organizationOfDefault);
      } finally {
        jdbc.update("DELETE FROM users_organizations WHERE user_id = ?", saved.getId());
        jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", saved.getId());
        jdbc.update("DELETE FROM users WHERE user_id = ?", saved.getId());
      }
    }
  }

  @Nested
  @DisplayName("the password login of a user who is a member of no tenant")
  class NoMembership {

    @Test
    @DisplayName("given_aUserWithNoTenantMembership_should_answerWithEmptyTeamsAndTags")
    void given_aUserWithNoTenantMembership_should_answerWithEmptyTeamsAndTags() throws Exception {
      // Arrange: linked to a tag of tenant A, which the user cannot read
      User loner =
          userRepository.save(
              UserFixture.getUser(
                  "login-loner",
                  "login-loner",
                  "login-loner-" + UUID.randomUUID() + "@unittests.invalid",
                  false));
      loner.setPassword(UserFixture.ENCODED_PASSWORD);
      loner = userRepository.save(loner);
      jdbc.update(
          "INSERT INTO users_tags (user_id, tag_id) VALUES (?, ?)", loner.getId(), tagIds.get(0));
      LoginUserInput input =
          LoginUserInput.builder().login(loner.getEmail()).password(RAW_PASSWORD).build();
      try {
        // Act
        String body =
            mvc.perform(
                    post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Assert
        JsonNode response = mapper.readTree(body);
        assertThat(ids(response, "user_teams")).isEmpty();
        assertThat(ids(response, "user_tags")).isEmpty();
      } finally {
        jdbc.update("DELETE FROM users_tags WHERE user_id = ?", loner.getId());
        jdbc.update("DELETE FROM users WHERE user_id = ?", loner.getId());
      }
    }
  }
}
