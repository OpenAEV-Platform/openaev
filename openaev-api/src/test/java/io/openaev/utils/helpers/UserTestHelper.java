package io.openaev.utils.helpers;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Helper to create test users with different RBAC configurations (roles and/or grants) for
 * integration tests.
 */
@Component
public class UserTestHelper {

  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private GrantComposer grantComposer;

  /** User types for parameterized RBAC tests. */
  public enum UserType {
    NO_GROUPS,
    ADMIN,
    WITH_BYPASS,
    WITH_ACCESS_THREAT_ARSENALS,
  }

  /**
   * Creates a test user with the given type and optional grants on the specified resource IDs.
   *
   * @param userType the type of user to create (determines role/capabilities)
   * @param grantedResourceIds resource IDs to grant OBSERVER access on (THREAT_ARSENAL type)
   * @return a {@link UserComposer.Composer} ready to be persisted
   */
  public UserComposer.Composer createTestUser(UserType userType, List<String> grantedResourceIds) {
    UserComposer.Composer user =
        switch (userType) {
          case NO_GROUPS ->
              userComposer.forUser(
                  UserFixture.getUser(
                      "NoGroups", "User", UUID.randomUUID() + "@unittests.invalid"));
          case ADMIN ->
              userComposer.forUser(
                  UserFixture.getAdminUser(
                      "Admin", "User", UUID.randomUUID() + "@unittests.invalid"));
          case WITH_BYPASS -> {
            TenantGroupComposer.Composer bypassGroup =
                tenantGroupComposer
                    .forGroup(TenantGroupFixture.getGroup())
                    .withRole(
                        tenantRoleComposer.forRole(
                            TenantRoleFixture.getRole(new HashSet<>(Set.of(Capability.BYPASS)))));

            yield userComposer
                .forUser(
                    UserFixture.getUser("Bypass", "User", UUID.randomUUID() + "@unittests.invalid"))
                .withGroup(bypassGroup);
          }
          case WITH_ACCESS_THREAT_ARSENALS -> {
            TenantGroupComposer.Composer threatArsenalGroup =
                tenantGroupComposer
                    .forGroup(TenantGroupFixture.getGroup())
                    .withRole(
                        tenantRoleComposer.forRole(
                            TenantRoleFixture.getRole(
                                new HashSet<>(Set.of(Capability.ACCESS_THREAT_ARSENALS)))));

            yield userComposer
                .forUser(
                    UserFixture.getUser(
                        "AccessThreatArsenals", "User", UUID.randomUUID() + "@unittests.invalid"))
                .withGroup(threatArsenalGroup);
          }
          default -> throw new IllegalArgumentException("Unknown user type: " + userType);
        };

    List<Grant> grants =
        grantedResourceIds.stream()
            .map(
                id -> {
                  Grant grant = new Grant();
                  grant.setGrantResourceType(Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL);
                  grant.setName(Grant.GRANT_TYPE.OBSERVER);
                  grant.setResourceId(id);
                  return grant;
                })
            .toList();

    if (!grants.isEmpty()) {
      TenantGroupComposer.Composer grantedGroup =
          tenantGroupComposer
              .forGroup(TenantGroupFixture.getGroup())
              .withRole(tenantRoleComposer.forRole(TenantRoleFixture.getRole(new HashSet<>())));

      grants.forEach(grant -> grantedGroup.withGrant(grantComposer.forGrant(grant)));

      return user.withGroup(grantedGroup);
    }
    return user;
  }
}
