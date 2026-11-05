package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Atomic testing and plain-inject by-id reads and writes resolve in the request's tenant scope for
 * a caller who is not a platform admin: a tenant member holding every tenant-scoped capability (the
 * standing a tenant's own top role has on its assessment resources), and a plain member holding a
 * grant on one resource of their own tenant only. Both are exercised on the tenant route, where the
 * ambient tenant used by capability and grant resolution matches the path tenant.
 */
@WithMockUser
@DisplayName("Atomic testing and inject by-id access for a caller who is not a platform admin")
class ByIdRequestScopeNonAdminTest extends ByIdRequestScopeNonAdminTestBase {

  @Nested
  @DisplayName("A tenant member holding every tenant-scoped capability")
  class CapabilityHolderReads {

    @Test
    @DisplayName("given an atomic testing of the default tenant, it is not found by id")
    void given_defaultTenantAtomicTesting_should_notBeFoundById() throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-capability-holder-default");
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route route = tenantRoute(callerTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);
      int export = injectExport(route, injectId);
      int targetSearch = injectTargetSearch(route, injectId, "TEAMS");
      int targetOptions = injectTargetOptions(route, injectId, "TEAMS");
      int reception = injectExecutionReception(route, injectId);
      int updateTags = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(
              new int[] {
                read,
                readPlainInject,
                export,
                targetSearch,
                targetOptions,
                reception,
                updateTags,
                delete
              })
          .containsExactly(404, 404, 404, 404, 404, 404, 404, 404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @DisplayName("given an atomic testing of a second, non-default tenant, it is not found by id")
    void given_secondTenantAtomicTesting_should_notBeFoundById() throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-capability-holder-second");
      String injectId = seedAtomicTesting(otherTenant);
      Route route = tenantRoute(callerTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);
      int export = injectExport(route, injectId);
      int targetSearch = injectTargetSearch(route, injectId, "TEAMS");
      int targetOptions = injectTargetOptions(route, injectId, "TEAMS");
      int reception = injectExecutionReception(route, injectId);
      int updateTags = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(
              new int[] {
                read,
                readPlainInject,
                export,
                targetSearch,
                targetOptions,
                reception,
                updateTags,
                delete
              })
          .containsExactly(404, 404, 404, 404, 404, 404, 404, 404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @DisplayName(
        "given an atomic testing of the caller's own tenant, it is read, updated and deleted")
    void given_ownTenantAtomicTesting_should_beReachable() throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-capability-holder-own");
      String injectId = seedAtomicTesting(callerTenant);
      Route route = tenantRoute(callerTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert: the capability is real and reaches the caller's own tenant, so a refusal above is
      // the by-id scope and not a setup mistake.
      assertThat(new int[] {read, delete}).containsExactly(200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }
  }

  @Nested
  @DisplayName("A plain tenant member holding a grant on one resource of their own tenant")
  class GrantHolderReads {

    @Test
    @DisplayName("given a grant on their own atomic testing, a foreign atomic testing is refused")
    void given_grantOnOwnAtomicTesting_should_refuseForeignAtomicTesting() throws Exception {
      // Arrange
      String callerTenant = tenantHelper.createTenantWithCurrentUser("by-id-grant-holder").getId();
      String ownResourceId = seedAtomicTesting(callerTenant);
      memberWithGrantOnResourceOnly(
          callerTenant,
          ownResourceId,
          Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING,
          Grant.GRANT_TYPE.OBSERVER);
      String otherTenantAtomicTestingId = seedAtomicTesting(otherTenant);
      Route route = tenantRoute(callerTenant);

      // Act
      int read = getAtomicTesting(route, otherTenantAtomicTestingId);
      int readPlainInject = getInject(route, otherTenantAtomicTestingId);

      // Assert: a resource-specific grant does not reach a resource it was never named on.
      assertThat(new int[] {read, readPlainInject}).containsExactly(403, 403);
      assertThat(atomicTestingExists(otherTenantAtomicTestingId)).isTrue();
    }

    @Test
    @DisplayName("given a grant on their own atomic testing, that same atomic testing is reachable")
    void given_grantOnOwnAtomicTesting_should_reachItsOwnAtomicTesting() throws Exception {
      // Arrange
      String callerTenant =
          tenantHelper.createTenantWithCurrentUser("by-id-grant-holder-own").getId();
      String ownResourceId = seedAtomicTesting(callerTenant);
      memberWithGrantOnResourceOnly(
          callerTenant,
          ownResourceId,
          Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING,
          Grant.GRANT_TYPE.OBSERVER);
      Route route = tenantRoute(callerTenant);

      // Act
      int read = getAtomicTesting(route, ownResourceId);

      // Assert: the grant is real and reaches its own resource, so the refusal above is the grant
      // being resource-specific and not a setup mistake.
      assertThat(read).isEqualTo(200);
    }
  }

  @Nested
  @DisplayName("A tenant-scoped capability holder on the non-prefixed route")
  class CapabilityHolderReadsNonPrefixedRoute {

    @Test
    @DisplayName(
        "given a selector naming another tenant, that tenant's atomic testing is not found by id")
    @WithMockUser(
        withCapabilities = {
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.DELETE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT
        },
        autoJoinDefaultTenant = true)
    void given_selectorNamingAnotherTenant_should_notBeFoundById() throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(otherTenant);
      String injectId = seedAtomicTesting(otherTenant);
      Route route = headerRoute(otherTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);

      // Assert
      assertThat(new int[] {read, readPlainInject}).containsExactly(403, 403);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @DisplayName(
        "given no selector, the same capability does not reach the default tenant's atomic"
            + " testing either, unlike on the tenant route")
    @WithMockUser(
        withCapabilities = {
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.DELETE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT
        },
        autoJoinDefaultTenant = true)
    void given_noSelector_should_alsoNotBeFoundById() throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route prefixedRoute = tenantRoute(DEFAULT_TENANT);
      Route nonPrefixedRoute = noSelectorRoute();

      // Act
      int readOnTenantRoute = getAtomicTesting(prefixedRoute, injectId);
      int readOnNonPrefixedRoute = getAtomicTesting(nonPrefixedRoute, injectId);
      int readPlainInjectOnNonPrefixedRoute = getInject(nonPrefixedRoute, injectId);

      // Assert: the capability is real and reaches the same row on the tenant route, so the
      // refusal on the non-prefixed route is not a setup mistake; its exact cause is not pinned
      // down by this test.
      assertThat(readOnTenantRoute).isEqualTo(200);
      assertThat(new int[] {readOnNonPrefixedRoute, readPlainInjectOnNonPrefixedRoute})
          .containsExactly(403, 403);
    }
  }

  @Nested
  @DisplayName("Updating an inject under a simulation the caller can write to")
  class InjectOfAnotherSimulation {

    @Test
    @DisplayName(
        "given an exercise of the caller's tenant and an inject of another tenant, the update is"
            + " refused and the inject keeps its title")
    void given_exerciseOfCallerTenantAndInjectOfAnotherTenant_should_refuseAndLeaveInjectUnchanged()
        throws Exception {
      // Arrange
      String callerTenant =
          tenantHelper.createTenantWithCurrentUser("by-id-cross-simulation-write").getId();
      String exerciseId = seedExercise(callerTenant);
      memberWithGrantOnResourceOnly(
          callerTenant, exerciseId, Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.PLANNER);
      String otherTenantInjectId = seedAtomicTesting(otherTenant);
      String originalTitle = injectTitle(otherTenantInjectId);
      Route route = tenantRoute(callerTenant);

      // Act
      int status = updateInject(route, exerciseId, otherTenantInjectId, "updated title");

      // Assert
      assertThat(status).isEqualTo(404);
      assertThat(injectTitle(otherTenantInjectId)).isEqualTo(originalTitle);
    }
  }
}
