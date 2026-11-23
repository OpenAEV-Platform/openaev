package io.openaev.rest.user;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.TokenRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.user.form.login.LoginUserInput;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.TokenFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.Map;
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
 * What the loads of a user with no tenant scope do with the markings of its groups, with {@code
 * marking_definitions} among the active tables.
 *
 * <p>The password login, the token authentication, the password reset and the by-id user read load
 * a user before any tenant is chosen. The user's groups carry their markings in an eager
 * collection, and with the table active that collection comes back empty on such a load: the
 * markings are tenant rows and no tenant is in scope. This class pins both halves of why that is
 * harmless: the load does return no marking (the premise), and nothing the callers return or write
 * depends on it, because the clearance of a request is read by JDBC from the link table and not
 * from the entity, and no response carries the markings of a group.
 *
 * <p>NOT {@code @Transactional}: the data is committed, and each call opens its own transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithoutTenantScope
@DisplayName("Loads of a user with no scope, with marking_definitions active")
class UserLoadUnscopedMarkingsTest extends ProductionTablesIntegrationTest {

  @MockitoBean private UserEventService userEventService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private TokenRepository tokenRepository;
  @Autowired private MarkingDefinitionRepository markingRepository;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String markingId;
  private String groupId;
  private String tokenValue;
  private User member;

  @BeforeAll
  void seed() throws Exception {
    userService.createAdminSession();
    try {
      tenantA = tenantHelper.createTenant("user-load-markings").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    markingId =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () -> {
              MarkingDefinition marking = MarkingDefinitionFixture.createDefaultMarkingDefinition();
              marking.setTenant(new Tenant(tenantA));
              return markingRepository.save(marking).getId();
            });
    member =
        userRepository.save(
            UserFixture.getUser(
                "user-load-markings",
                "user-load-markings",
                "user-load-markings-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    member.setPassword(UserFixture.ENCODED_PASSWORD);
    member = userRepository.save(member);
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    // A platform group (no tenant) is listed in the user_groups of every tenant, so a response that
    // leaked the markings of the groups would show them here
    group.setTenant(null);
    groupId = groupRepository.save(group).getId();
    // The link table is written by JDBC: a group's markings are written by the group service in a
    // scope, which is not what this class is about
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", groupId, markingId);
    jdbc.update(
        "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", member.getId(), groupId);
    tenantTx.execute(
        TxCtx.forTenant(tenantA), () -> tenantRepository.addUserToTenant(member.getId(), tenantA));
    membershipCache.evict(member.getId(), tenantA);
    Token token = TokenFixture.getTokenWithValue("token-" + UUID.randomUUID());
    token.setUser(member);
    tokenValue = tokenRepository.save(token).getValue();
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM tokens WHERE token_user = ?", member.getId());
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM groups_markings WHERE group_id = ?", groupId);
    jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", member.getId());
    tenantHelper.deleteCommittedTenants(tenantA);
    TenantContext.clearCurrentTenant();
  }

  private int storedMarkingLinks() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM groups_markings WHERE group_id = ?", Integer.class, groupId);
  }

  @Nested
  @DisplayName("the load the callers run with no scope")
  class UnscopedLoad {

    @Test
    @DisplayName("given_noScope_should_loadTheGroupOfTheUserByIdWithNoMarking")
    void given_noScope_should_loadTheGroupOfTheUserByIdWithNoMarking() {
      // Act: UserService.user, the read behind the password reset and the by-id user callers
      User loaded = userService.user(member.getId());

      // Assert
      assertThat(loaded.getUnscopedGroups()).extracting(Group::getId).containsExactly(groupId);
      assertThat(loaded.getUnscopedGroups().get(0).getMarkings()).isEmpty();
    }

    @Test
    @DisplayName("given_noScope_should_loadTheGroupOfTheUserByEmailWithNoMarking")
    void given_noScope_should_loadTheGroupOfTheUserByEmailWithNoMarking() {
      // Act: the read of the password login and of the password setup email
      User loaded = userRepository.findByEmailIgnoreCase(member.getEmail()).orElseThrow();

      // Assert
      assertThat(loaded.getUnscopedGroups()).extracting(Group::getId).containsExactly(groupId);
      assertThat(loaded.getUnscopedGroups().get(0).getMarkings()).isEmpty();
    }

    @Test
    @DisplayName("given_noScope_should_loadTheGroupOfTheUserByTokenWithNoMarking")
    void given_noScope_should_loadTheGroupOfTheUserByTokenWithNoMarking() {
      // Act: the read of the token authentication
      User loaded = userService.findByToken(tokenValue).orElseThrow();

      // Assert
      assertThat(loaded.getUnscopedGroups()).extracting(Group::getId).containsExactly(groupId);
      assertThat(loaded.getUnscopedGroups().get(0).getMarkings()).isEmpty();
    }

    @Test
    @DisplayName("given_theScopeOfTheTenant_should_loadTheMarkingOfTheGroup")
    void given_theScopeOfTheTenant_should_loadTheMarkingOfTheGroup() {
      // Act: the control, the same read in the scope of the tenant that owns the marking
      List<String> granted =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  userRepository.findById(member.getId()).orElseThrow().getUnscopedGroups().stream()
                      .flatMap(group -> group.getMarkings().stream())
                      .map(marking -> marking.getId())
                      .toList());

      // Assert: the empty collections above come from the missing scope and not from the data
      assertThat(granted).containsExactly(markingId);
    }
  }

  @Nested
  @DisplayName("what the callers return, write and decide from it")
  class WhatTheCallersDo {

    @Test
    @DisplayName("given_validCredentials_should_notReturnTheMarkingsOfTheGroupsOfTheUser")
    void given_validCredentials_should_notReturnTheMarkingsOfTheGroupsOfTheUser() throws Exception {
      // Arrange: the reset test of this class changes the password, whatever the order of the tests
      jdbc.update(
          "UPDATE users SET user_password = ? WHERE user_id = ?",
          UserFixture.ENCODED_PASSWORD,
          member.getId());
      LoginUserInput input =
          LoginUserInput.builder()
              .login(member.getEmail())
              .password(UserFixture.RAW_PASSWORD)
              .build();

      // Act
      String body =
          mvc.perform(
                  post("/api/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.user_groups[?(@ == '" + groupId + "')]").exists())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: the response names the group and nothing of its markings, with or without scope
      assertThat(body).doesNotContain(markingId).doesNotContain("group_markings");
      assertThat(storedMarkingLinks()).isEqualTo(1);
    }

    @Test
    @DisplayName("given_validResetToken_should_notReturnNorDeleteTheMarkingsOfTheGroupsOfTheUser")
    void given_validResetToken_should_notReturnNorDeleteTheMarkingsOfTheGroupsOfTheUser()
        throws Exception {
      // Arrange: the token a reset mail carries, held by the service for the user
      String token = "reset-" + UUID.randomUUID();
      Object service = AopTestUtils.getUltimateTargetObject(userService);
      @SuppressWarnings("unchecked")
      Map<String, String> resetTokens =
          (Map<String, String>) ReflectionTestUtils.getField(service, "resetTokenMap");
      resetTokens.put(member.getId(), token);

      // Act
      String body =
          mvc.perform(
                  post("/api/reset/" + token)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"password\":\"Passw0rd!x\",\"password_validation\":\"Passw0rd!x\"}")
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: the user is saved after the load with no marking, and the link is still there
      assertThat(body).doesNotContain(markingId).doesNotContain("group_markings");
      assertThat(storedMarkingLinks()).isEqualTo(1);
    }

    @Test
    @DisplayName("given_noScopeLoadsOfTheUser_should_stillGrantTheMarkingOfTheGroupAsClearance")
    void given_noScopeLoadsOfTheUser_should_stillGrantTheMarkingOfTheGroupAsClearance() {
      // Arrange: the loads of the callers have run, the clearance of a request is read afterwards
      userService.user(member.getId());
      userService.findByToken(tokenValue);
      clearanceCache.evict(member.getId(), tenantA);

      // Act: the clearance comes from the link table by JDBC, not from the entity
      boolean allowed =
          clearanceCache.findClearance(member.getId(), tenantA).allows(List.of(markingId));

      // Assert
      assertThat(allowed).isTrue();
    }
  }
}
