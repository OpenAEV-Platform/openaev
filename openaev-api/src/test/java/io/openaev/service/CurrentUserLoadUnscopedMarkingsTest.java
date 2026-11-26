package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.AdminSessionTestHelper;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.MarkingDefinitionFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the user load of the access check does with the markings of the user's groups, with {@code
 * marking_definitions} among the active tables.
 *
 * <p>{@code UserService.findWithRoles} loads the caller's groups with their roles, and a group's
 * markings are an eager collection read with it. A caller that belongs to no tenant has an empty
 * scope, so the markings, which are tenant rows, come back empty. This class pins both halves of
 * why that is harmless: the load does return no marking (the premise, in both branches of the load:
 * no transaction open, and a transaction with an empty scope), and the clearance of the user does
 * not depend on it, because it is read by JDBC from the link table and neither load writes the link
 * table. The capabilities of the same caller are pinned by {@code ScopedCurrentUserLoadTest}
 * (no-membership cases).
 *
 * <p>NOT {@code @Transactional}: the data is committed, and each call opens its own transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithoutTenantScope
@DisplayName("User load of the access check with no scope, with marking_definitions active")
class CurrentUserLoadUnscopedMarkingsTest extends ProductionTablesIntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private UserService userService;
  @Autowired private AdminSessionTestHelper adminSession;
  @Autowired private UserRepository userRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private MarkingDefinitionRepository markingRepository;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  private String tenantA;
  private String markingId;
  private String groupId;
  private User noMember;

  @BeforeAll
  void seed() throws Exception {
    adminSession.open();
    try {
      tenantA = tenantHelper.createTenant("find-with-roles-markings").getId();
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
    // A user with no membership: no row of users_tenants, so the scope of its requests is empty
    noMember =
        userRepository.save(
            UserFixture.getUser(
                "find-with-roles-markings",
                "find-with-roles-markings",
                "find-with-roles-markings-" + UUID.randomUUID() + "@unittests.invalid",
                false));
    Group group = new Group();
    group.setName("group-" + UUID.randomUUID());
    // A platform group (no tenant): the only kind of group a user of no tenant holds
    group.setTenant(null);
    groupId =
        tenantTx.executeAsPlatformWrite(
            TxCtx.forTenant(tenantA), () -> groupRepository.save(group).getId());
    // The link tables are written by JDBC: a group's markings are written by the group service in
    // a scope, which is not what this class is about
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", groupId, markingId);
    jdbc.update(
        "INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", noMember.getId(), groupId);
    TenantContext.clearCurrentTenant();
  }

  @AfterAll
  void cleanup() {
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", noMember.getId());
    jdbc.update("DELETE FROM groups_markings WHERE group_id = ?", groupId);
    jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", noMember.getId());
    tenantHelper.deleteCommittedTenants(tenantA);
    TenantContext.clearCurrentTenant();
  }

  /**
   * A transaction no scope was set on: what the request's transaction of a caller of no tenant is,
   * since {@code TenantScopedTransaction} refuses to open one with an empty scope.
   */
  private <T> T inTransactionWithNoScope(Supplier<T> work) {
    return new TransactionTemplate(transactionManager).execute(status -> work.get());
  }

  private int storedMarkingLinks() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM groups_markings WHERE group_id = ?", Integer.class, groupId);
  }

  private boolean clearedForTheMarking() {
    clearanceCache.evict(noMember.getId(), tenantA);
    return clearanceCache.findClearance(noMember.getId(), tenantA).allows(List.of(markingId));
  }

  @Nested
  @DisplayName("the load of a user with no membership")
  class UnscopedLoad {

    @Test
    @DisplayName("given_noTransaction_should_loadThePlatformGroupWithNoMarking")
    void given_noTransaction_should_loadThePlatformGroupWithNoMarking() {
      // Act: no transaction open, no membership: the user is loaded as is (second branch)
      User loaded = userService.userWithRoles(noMember.getId());

      // Assert
      assertThat(loaded.getUnscopedGroups()).extracting(Group::getId).containsExactly(groupId);
      assertThat(loaded.getUnscopedGroups().get(0).getMarkings()).isEmpty();
    }

    @Test
    @DisplayName("given_aTransactionWithAnEmptyScope_should_loadThePlatformGroupWithNoMarking")
    void given_aTransactionWithAnEmptyScope_should_loadThePlatformGroupWithNoMarking() {
      // Act: a transaction with no scope, as a caller of no tenant has (first branch)
      List<String> markings =
          inTransactionWithNoScope(
              () ->
                  userService.userWithRoles(noMember.getId()).getUnscopedGroups().stream()
                      .flatMap(group -> group.getMarkings().stream())
                      .map(MarkingDefinition::getId)
                      .toList());

      // Assert
      assertThat(markings).isEmpty();
    }

    @Test
    @DisplayName("given_theScopeOfTheTenant_should_loadTheMarkingOfTheGroup")
    void given_theScopeOfTheTenant_should_loadTheMarkingOfTheGroup() {
      // Act: the control, the same group read in the scope of the tenant that owns the marking
      List<String> markings =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  userService.userWithRoles(noMember.getId()).getUnscopedGroups().stream()
                      .flatMap(group -> group.getMarkings().stream())
                      .map(MarkingDefinition::getId)
                      .toList());

      // Assert: the empty collections above come from the missing scope and not from the data
      assertThat(markings).containsExactly(markingId);
    }
  }

  @Nested
  @DisplayName("what the clearance of that user depends on")
  class Clearance {

    @Test
    @DisplayName("given_theLoadsOfTheAccessCheck_should_leaveTheClearanceOfTheUserUnchanged")
    void given_theLoadsOfTheAccessCheck_should_leaveTheClearanceOfTheUserUnchanged() {
      // Arrange
      boolean before = clearedForTheMarking();

      // Act: both branches of the load
      userService.userWithRoles(noMember.getId());
      inTransactionWithNoScope(() -> userService.userWithRoles(noMember.getId()));
      boolean after = clearedForTheMarking();

      // Assert: the clearance comes from the link table by JDBC, not from the loaded entity
      assertThat(before).isTrue();
      assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("given_theLoadsOfTheAccessCheck_should_notRewriteTheMarkingsOfTheGroup")
    void given_theLoadsOfTheAccessCheck_should_notRewriteTheMarkingsOfTheGroup() {
      // Act: the load returns a group with an empty markings collection, then the transaction ends
      userService.userWithRoles(noMember.getId());
      inTransactionWithNoScope(() -> userService.userWithRoles(noMember.getId()));

      // Assert: nothing wrote the empty collection back to the link table
      assertThat(storedMarkingLinks()).isEqualTo(1);
    }
  }
}
