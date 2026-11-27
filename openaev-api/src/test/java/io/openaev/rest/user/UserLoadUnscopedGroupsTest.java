package io.openaev.rest.user;

import static io.openaev.utils.fixtures.UserFixture.getUserInputWithTenants;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ExpectsEmptyScopeReads;
import io.openaev.config.WaivesUnscopedReads;
import io.openaev.config.cache.TenantMembershipCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.service.UserService;
import io.openaev.service.user_events.UserEventService;
import io.openaev.utils.AdminSessionTestHelper;
import io.openaev.utils.TenantIsolationTestHelper;
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
 * What the loads of a user with no tenant scope do with the groups of its tenant, with {@code
 * groups} among the active tables.
 *
 * <p>The password reset and the platform user update load a user while the transaction's scope is
 * empty: the reset is pre-auth, and an operator who belongs to no tenant resolves {@link
 * TxCtx#missing()}. {@code groups} is dual-scope, so such a load returns the platform groups of the
 * user and none of its tenant groups. This class pins both halves of why that is harmless: the load
 * does return no tenant group (the premise), and neither caller writes the missing groups back,
 * because the user owns the {@code users_groups} link table and neither caller touches the
 * collection it loaded.
 *
 * <p>The reads the list endpoints make for a caller of an empty scope (simulations and scenarios by
 * id, the platform group search) are pinned by their own tests, named in the baseline reason.
 *
 * <p>NOT {@code @Transactional}: the data is committed, and each call opens its own transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithoutTenantScope
@DisplayName("Loads of a user with no scope, with groups active")
class UserLoadUnscopedGroupsTest extends IntegrationTest {

  @MockitoBean private UserEventService userEventService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private AdminSessionTestHelper adminSession;
  @Autowired private UserRepository userRepository;
  @Autowired private TenantMembershipCacheManager membershipCache;
  @Autowired private JdbcTemplate jdbc;

  private String tenantA;
  private String platformGroupId;
  private String tenantGroupId;
  private User member;

  @BeforeAll
  void seed() throws Exception {
    adminSession.open();
    try {
      tenantA = tenantHelper.createTenant("user-load-groups").getId();
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clearCurrentTenant();
    }
    member =
        userRepository.save(
            UserFixture.getUser(
                "user-load-groups",
                "user-load-groups",
                "user-load-groups-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    member.setPassword(UserFixture.ENCODED_PASSWORD);
    member = userRepository.save(member);
    // Both groups are written by JDBC, which the statement inspector does not rewrite: seeding
    // them needs no scope of its own
    platformGroupId = insertGroup(null);
    tenantGroupId = insertGroup(tenantA);
    jdbc.update(
        "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
        member.getId(),
        platformGroupId);
    jdbc.update(
        "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)",
        member.getId(),
        tenantGroupId);
    jdbc.update(
        "INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?)", member.getId(), tenantA);
    membershipCache.evict(member.getId(), tenantA);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM groups WHERE group_id IN (?, ?)", platformGroupId, tenantGroupId);
    jdbc.update("DELETE FROM users_tenants WHERE user_id = ?", member.getId());
    jdbc.update("DELETE FROM users WHERE user_id = ?", member.getId());
    tenantHelper.deleteCommittedTenants(tenantA);
    TenantContext.clearCurrentTenant();
  }

  private String insertGroup(String tenantId) {
    String groupId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
            + " group_auto_new_marking, tenant_id) VALUES (?, ?, false, false, ?)",
        groupId,
        "group-" + UUID.randomUUID(),
        tenantId);
    return groupId;
  }

  private List<String> storedGroupIds() {
    return jdbc.queryForList(
        "SELECT group_id FROM users_groups WHERE user_id = ?", String.class, member.getId());
  }

  @Nested
  @DisplayName("the load the callers run with no scope")
  class UnscopedLoad {

    @ExpectsEmptyScopeReads("the unscoped load is the subject: only the platform group is loaded")
    @Test
    @DisplayName("given_noScope_should_loadThePlatformGroupOfTheUserAndNotItsTenantGroup")
    void given_noScope_should_loadThePlatformGroupOfTheUserAndNotItsTenantGroup() {
      // Act: the read behind the password reset and the password setup email, from this class so
      // that no production frame owns it
      User loaded = userRepository.findByEmailIgnoreCase(member.getEmail()).orElseThrow();

      // Assert
      assertThat(loaded.getUnscopedGroups())
          .extracting(Group::getId)
          .containsExactly(platformGroupId);
    }

    @Test
    @DisplayName("given_theScopeOfTheTenant_should_loadBothGroupsOfTheUser")
    void given_theScopeOfTheTenant_should_loadBothGroupsOfTheUser() {
      // Act: the control, the same read in the scope of the tenant that owns the group
      List<String> loaded =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  userRepository.findById(member.getId()).orElseThrow().getUnscopedGroups().stream()
                      .map(Group::getId)
                      .toList());

      // Assert: the missing group above comes from the missing scope and not from the data
      assertThat(loaded).containsExactlyInAnyOrder(platformGroupId, tenantGroupId);
    }
  }

  @Nested
  @DisplayName("what the callers write from it")
  class WhatTheCallersWrite {

    @Test
    @DisplayName("given_validResetToken_should_notDeleteTheTenantGroupOfTheUser")
    void given_validResetToken_should_notDeleteTheTenantGroupOfTheUser() throws Exception {
      // Arrange: the token a reset mail carries, held by the service for the user
      String token = "reset-" + UUID.randomUUID();
      Object service = AopTestUtils.getUltimateTargetObject(userService);
      @SuppressWarnings("unchecked")
      Map<String, String> resetTokens =
          (Map<String, String>) ReflectionTestUtils.getField(service, "resetTokenMap");
      resetTokens.put(member.getId(), token);

      // Act: the user is loaded with no tenant group, then saved
      mvc.perform(
              post("/api/reset/" + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"password\":\"Passw0rd!x\",\"password_validation\":\"Passw0rd!x\"}")
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(storedGroupIds()).containsExactlyInAnyOrder(platformGroupId, tenantGroupId);
    }

    @Test
    @WaivesUnscopedReads(
        value =
            "an operator of no tenant updates the profile with an empty scope: the reload of the"
                + " user reads none of its tags, teams and organizations, nor its tenant group in the"
                + " tag replacement, and the test asserts that the tenant group stays linked",
        reads = {
          "io.openaev.service.UserService.replaceTags tables=groups,tags",
          "io.openaev.service.UserService.loadStreamedAssociations tables=tags,teams",
          "io.openaev.database.model.User.loadOrganizations tables=organizations",
          "io.openaev.database.model.User.organizationOfTenant tables=organizations",
          "io.openaev.helper.MultiIdListSerializer.serialize tables=teams"
        })
    @DisplayName("given_anOperatorOfNoTenant_should_keepTheTenantGroupWhenOnlyTheProfileChanges")
    void given_anOperatorOfNoTenant_should_keepTheTenantGroupWhenOnlyTheProfileChanges() {
      // Arrange: the user stays in its tenant, so no group is attached or detached
      var input =
          getUserInputWithTenants(member.getEmail(), "renamed", "renamed", null, List.of(tenantA));

      // Act: an operator with no tenant membership, whose scope is empty; the tag replacement
      // reloads the user under it
      userService.updateUser(TxCtx.missing(), member.getId(), input);

      // Assert
      assertThat(storedGroupIds()).containsExactlyInAnyOrder(platformGroupId, tenantGroupId);
    }
  }
}
