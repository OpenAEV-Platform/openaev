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
    @DisplayName(
        "given a grant on their own atomic testing, an out-of-scope atomic testing is not found"
            + " by id")
    void given_grantOnOwnAtomicTesting_should_notFindOutOfScopeAtomicTestingById()
        throws Exception {
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
      // PermissionService#resolveTarget's own by-id load runs before the grant check and refuses
      // an out-of-scope atomic testing with 404, the same answer every other by-id endpoint of
      // this family gives for a row outside the caller's scope.
      assertThat(new int[] {read, readPlainInject}).containsExactly(404, 404);
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
        "given a selector naming another tenant the caller also belongs to, the atomic testing is"
            + " found by id and the request is still refused for lacking the capability there")
    @WithMockUser(
        withCapabilities = {
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.DELETE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT
        },
        autoJoinDefaultTenant = true)
    void given_selectorNamingAnotherTenant_should_beRefusedForLackOfCapabilityThere()
        throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(otherTenant);
      String injectId = seedAtomicTesting(otherTenant);
      Route route = headerRoute(otherTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);

      // Assert: the row lies inside the resolved scope (the caller belongs to otherTenant and the
      // selector names it), so the by-id load now finds it instead of refusing it as out of scope.
      // The request is still refused, 403 rather than 404, because AccessControlAspect checks the
      // capability in every tenant of the scope and this role was only granted in the default
      // tenant: the same final answer the "no selector" case below gives, for the same capability
      // reason.
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
  @DisplayName("The access-control gate's own by-id load, on an endpoint with no body-level guard")
  class AccessControlGateReadsTheInjectInScope {

    @Test
    @DisplayName(
        "given an out-of-scope inject, the capability holder is refused before the handler, not"
            + " let through to an empty result")
    void given_outOfScopeInject_should_refuseBeforeTheHandlerRuns() throws Exception {
      // Arrange
      String callerTenant =
          memberWithEveryTenantCapability("by-id-execution-traces-capability-holder");
      String otherTenantInjectId = seedAtomicTesting(otherTenant);
      Route route = tenantRoute(callerTenant);

      // Act
      int status = injectExecutionTraces(route, otherTenantInjectId, "nonexistent-team", "TEAMS");

      // Assert: the capability is role-level and would otherwise pass regardless of which tenant
      // the inject belongs to (confirmed by the capability-holder's own-tenant case below), so a
      // 404 here is PermissionService#resolveTarget's own by-id load, refusing an inject outside
      // the caller's scope, not the handler's execution-trace query (which runs no scope check at
      // all).
      assertThat(status).isEqualTo(404);
    }

    @Test
    @DisplayName("given the caller's own tenant's inject, the request reaches the handler")
    void given_ownTenantInject_should_reachTheHandler() throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-execution-traces-own");
      String ownInjectId = seedAtomicTesting(callerTenant);
      Route route = tenantRoute(callerTenant);

      // Act
      int status = injectExecutionTraces(route, ownInjectId, "nonexistent-team", "TEAMS");

      // Assert: the capability reaches the caller's own tenant, so the refusal above is the by-id
      // scope and not a setup mistake; 200 with an empty list, since no trace matches the team.
      assertThat(status).isEqualTo(200);
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

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName(
        "given an exercise and inject of the caller's own, non-default tenant named by the"
            + " selector, the update succeeds on the non-prefixed route: requireInjectBelongsToExercise"
            + " does not run under the ambient (default) tenant instead of the selected one")
    void given_ownNonDefaultTenantExerciseAndInject_should_beUpdatedOnNonPrefixedRoute()
        throws Exception {
      // Arrange: admin bypasses AccessControlAspect's own grant/capability resolution entirely, so
      // this isolates requireInjectBelongsToExercise's own by-id load from that separate check.
      String callerTenant =
          tenantHelper.createTenantWithCurrentUser("by-id-cross-simulation-header").getId();
      String exerciseId = seedExercise(callerTenant);
      String injectId = seedInjectForExercise(callerTenant, exerciseId);
      Route route = headerRoute(callerTenant);

      // Act
      int status = updateInject(route, exerciseId, injectId, "updated title");

      // Assert
      assertThat(status).isEqualTo(200);
      assertThat(injectTitle(injectId)).isEqualTo("updated title");
    }
  }
}
