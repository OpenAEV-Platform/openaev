package io.openaev.service;

import static io.openaev.utils.fixtures.UserFixture.getUser;
import static io.openaev.utils.fixtures.UserFixture.getUserInputWithTenants;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.WaivesUnscopedReads;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserService#applyUpdate} and the platform create path ({@link
 * UserService#createUser(TxCtx, UserInput, UserCreationScope)}) grant and revoke auto-assign groups
 * for the tenants a platform operator attaches or detaches on {@code PlatformUserApi}, whatever the
 * operator's own tenant memberships are - in particular a platform admin with none, whose resolved
 * {@link TxCtx} is {@link TxCtx#missing()} (passed explicitly here rather than through the API
 * layer, to isolate the scoping fix from the request's own scope resolution).
 *
 * <p>Every ground truth is read by raw JDBC, which the statement inspector does not rewrite, and
 * every group row is seeded by raw JDBC too, so the fixture setup needs no scope of its own (see
 * {@link io.openaev.api.groups.DualScopeGroupsActivationTest}).
 */
@Transactional
@DisplayName("A platform operator's tenant attach grants and revokes groups")
class UserTenantGroupScopeTest extends IntegrationTest {

  @Autowired private UserService userService;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private JdbcTemplate jdbc;
  @PersistenceContext private EntityManager entityManager;

  @Test
  @WaivesUnscopedReads(
      value =
          "the reload after the update runs in the empty scope of an operator of no tenant: the"
              + " tags, teams and organizations of the user read nothing, and the tag replacement"
              + " changes the links in scope only, so no link is written from them",
      reads = {
        "io.openaev.service.UserService.replaceTags tables=tags",
        "io.openaev.service.UserService.loadStreamedAssociations tables=tags,teams",
        "io.openaev.database.model.User.loadOrganizations tables=organizations"
      })
  @DisplayName(
      "given_anOperatorWithNoTenantMembership_should_grantAttachedAndRevokeDetachedGroupsOnUpdate")
  void
      given_anOperatorWithNoTenantMembership_should_grantAttachedAndRevokeDetachedGroupsOnUpdate() {
    // Arrange
    Tenant tenantA =
        tenantComposer
            .forTenant(TenantFixture.getTenant("scope-update-a-" + UUID.randomUUID()))
            .persist()
            .get();
    Tenant tenantB =
        tenantComposer
            .forTenant(TenantFixture.getTenant("scope-update-b-" + UUID.randomUUID()))
            .persist()
            .get();
    entityManager.flush();
    String groupA = seedAutoAssignGroup(tenantA.getId(), "scope-update-auto-a");
    String groupB = seedAutoAssignGroup(tenantB.getId(), "scope-update-auto-b");
    String email = "scope-update-" + UUID.randomUUID() + "@unittests.invalid";
    User user = userComposer.forUser(getUser("Scope", "Update", email)).persist().get();
    tenantRepository.addUserToTenant(user.getId(), tenantB.getId());
    jdbc.update("INSERT INTO users_groups (user_id, group_id) VALUES (?, ?)", user.getId(), groupB);
    entityManager.flush();
    entityManager.clear();
    UserInput input =
        getUserInputWithTenants(email, "Scope", "Update", null, List.of(tenantA.getId()));

    // Act: a platform operator with no tenant membership at all (TxCtx.missing()), attaching
    // tenant A and detaching tenant B.
    userService.updateUser(TxCtx.missing(), user.getId(), input);

    // Assert
    assertThat(groupIdsOf(user.getId())).contains(groupA).doesNotContain(groupB);
  }

  @Test
  @DisplayName("given_anOperatorWithNoTenantMembership_should_grantAttachedGroupsOnCreate")
  void given_anOperatorWithNoTenantMembership_should_grantAttachedGroupsOnCreate() {
    // Arrange
    Tenant tenant =
        tenantComposer
            .forTenant(TenantFixture.getTenant("scope-create-a-" + UUID.randomUUID()))
            .persist()
            .get();
    entityManager.flush();
    String group = seedAutoAssignGroup(tenant.getId(), "scope-create-auto-a");
    String email = "scope-create-" + UUID.randomUUID() + "@unittests.invalid";
    UserInput input =
        getUserInputWithTenants(email, "Scope", "Create", "secureP@ss1", List.of(tenant.getId()));

    // Act: a platform operator with no tenant membership at all (TxCtx.missing()), creating a
    // user attached to tenant A.
    User created = userService.createUser(TxCtx.missing(), input, UserCreationScope.PLATFORM);
    entityManager.flush();

    // Assert: the new user's groups bag is inserted only at flush time, raw JDBC does not see it
    // before that.
    assertThat(groupIdsOf(created.getId())).contains(group);
  }

  private List<String> groupIdsOf(String userId) {
    return jdbc.queryForList(
        "SELECT group_id FROM users_groups WHERE user_id = ?", String.class, userId);
  }

  private String seedAutoAssignGroup(String tenantId, String name) {
    String groupId = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement group =
                  connection.prepareStatement(
                      "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
                          + " group_auto_new_marking, tenant_id) VALUES (?, ?, true, false, ?)")) {
                group.setString(1, groupId);
                group.setString(2, name);
                group.setString(3, tenantId);
                group.executeUpdate();
              }
            });
    return groupId;
  }
}
