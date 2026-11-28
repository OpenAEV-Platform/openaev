package io.openaev.service.account;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Grant.GRANT_TYPE;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.rest.exception.PrivilegeGrantException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PrivilegeEscalationValidatorTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String OTHER_TENANT_ID = "tenant-2";
  private static final String RESOURCE_ID = "scenario-1";

  @AfterEach
  void clearTenantContext() {
    TenantContext.clearCurrentTenant();
  }

  /** {@code tenantId == null} builds a platform-scoped group (tenant IS NULL). */
  private static User userInGroup(
      String tenantId, Set<Capability> roleCapabilities, List<Grant> grants) {
    Role role = new Role();
    role.setCapabilities(roleCapabilities);
    Group group = new Group();
    group.setTenant(tenantId == null ? null : new Tenant(tenantId));
    group.setRoles(List.of(role));
    group.setGrants(grants);
    User user = new User();
    user.setGroups(List.of(group));
    return user;
  }

  /**
   * Backs both {@code TenantGroupService.addGrant} and {@code .removeGrant}: both resolve to this
   * one static check, under the same {@code permissionService.inTenant(tenantId, ...)} wrapper, so
   * exercising the guard here covers granting and revoking alike.
   */
  @Nested
  @DisplayName("assertCanAssignGrant")
  class AssertCanAssignGrant {

    @Test
    @DisplayName(
        "given_userHoldingTenantBypassThroughAGroup_should_notThrow_evenWithoutAnyGrantOnTheResource")
    void
        given_userHoldingTenantBypassThroughAGroup_should_notThrow_evenWithoutAnyGrantOnTheResource() {
      // Arrange
      TenantContext.setCurrentTenant(TENANT_ID);
      User user = userInGroup(TENANT_ID, Set.of(Capability.BYPASS), List.of());

      // Act / Assert
      assertThatCode(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.LAUNCHER, RESOURCE_ID))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("given_userHoldingPlatformBypass_should_notThrow_regardlessOfTheAmbientTenant")
    void given_userHoldingPlatformBypass_should_notThrow_regardlessOfTheAmbientTenant() {
      // Arrange: a platform group (tenant IS NULL) stays visible under any ambient tenant
      TenantContext.setCurrentTenant(OTHER_TENANT_ID);
      User user = userInGroup(null, Set.of(Capability.BYPASS), List.of());

      // Act / Assert
      assertThatCode(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.LAUNCHER, RESOURCE_ID))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName(
        "given_userHoldingBypassOnlyInADifferentTenant_should_throw_evenWithNoOwnGrantRequired")
    void given_userHoldingBypassOnlyInADifferentTenant_should_throw_evenWithNoOwnGrantRequired() {
      // Arrange: the BYPASS group belongs to OTHER_TENANT_ID, the assignment happens in TENANT_ID
      TenantContext.setCurrentTenant(TENANT_ID);
      User user = userInGroup(OTHER_TENANT_ID, Set.of(Capability.BYPASS), List.of());

      // Act / Assert
      assertThatThrownBy(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.OBSERVER, RESOURCE_ID))
          .isInstanceOf(PrivilegeGrantException.class);
    }

    @Test
    @DisplayName("given_userWithNoGrantAndNoBypass_should_throw")
    void given_userWithNoGrantAndNoBypass_should_throw() {
      // Arrange
      TenantContext.setCurrentTenant(TENANT_ID);
      User user = userInGroup(TENANT_ID, Set.of(Capability.ACCESS_ASSESSMENT), List.of());

      // Act / Assert
      assertThatThrownBy(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.OBSERVER, RESOURCE_ID))
          .isInstanceOf(PrivilegeGrantException.class);
    }

    @Test
    @DisplayName("given_userHoldingALowerPriorityGrant_should_throw_whenAssigningAHigherGrant")
    void given_userHoldingALowerPriorityGrant_should_throw_whenAssigningAHigherGrant() {
      // Arrange
      TenantContext.setCurrentTenant(TENANT_ID);
      Grant ownGrant = new Grant();
      ownGrant.setName(GRANT_TYPE.OBSERVER);
      ownGrant.setResourceId(RESOURCE_ID);
      User user = userInGroup(TENANT_ID, Set.of(), List.of(ownGrant));

      // Act / Assert
      assertThatThrownBy(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.LAUNCHER, RESOURCE_ID))
          .isInstanceOf(PrivilegeGrantException.class);
    }

    @Test
    @DisplayName("given_adminUser_should_notThrow")
    void given_adminUser_should_notThrow() {
      // Arrange
      User user = new User();
      user.setAdmin(true);

      // Act / Assert
      assertThatCode(
              () ->
                  PrivilegeEscalationValidator.assertCanAssignGrant(
                      user, GRANT_TYPE.LAUNCHER, RESOURCE_ID))
          .doesNotThrowAnyException();
    }
  }
}
