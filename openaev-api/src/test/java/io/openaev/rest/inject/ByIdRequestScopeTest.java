package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Atomic testing and plain-inject by-id reads resolve the row inside the request's tenant scope,
 * with the production list of isolated tables ({@code injects} is not in it). On the tenant route
 * the scope is the path tenant, so nothing changes there. On the non-prefixed route the scope is
 * the {@code X-Tenant-Ids} selector, or the caller's memberships when it names none: a row outside
 * that scope is not found by id.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Atomic testing and inject by-id reads in the request scope, production tables")
class ByIdRequestScopeTest extends ByIdRequestScopeProductionTablesTestBase {

  @Nested
  @DisplayName("Tenant route")
  class TenantRoute {

    @Test
    @DisplayName("given an atomic testing of a member tenant, it is read, updated and deleted")
    void given_atomicTestingOfMemberTenant_should_beReadUpdatedAndDeleted() throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(memberTenant);
      Route route = tenantRoute(memberTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPayload = getAtomicTestingPayload(route, injectId);
      int readPlainInject = getInject(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, readPayload, readPlainInject, update, delete})
          .containsExactly(200, 200, 200, 200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant atomic testing on the route of another tenant, it is not found"
            + " by id")
    void given_defaultTenantAtomicTestingOnRouteOfAnotherTenant_should_notBeFound()
        throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route route = tenantRoute(memberTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(404, 404, 404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant inject on the route of another tenant, the plain inject read is"
            + " not found by id")
    void given_defaultTenantInjectOnRouteOfAnotherTenant_should_notBeFoundByPlainInjectRead()
        throws Exception {
      // Arrange: InjectApi#inject now keys its read on the v1 ambient tenant (the path tenant,
      // here), the same scope AtomicTestingApi already used.
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route route = tenantRoute(memberTenant);

      // Act
      int read = getInject(route, injectId);

      // Assert
      assertThat(read).isEqualTo(404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }
  }

  @Nested
  @DisplayName("Non-prefixed route")
  class NonPrefixedRoute {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant atomic testing and a selector naming another tenant, it is not"
            + " found by id")
    void given_defaultTenantAtomicTestingAndSelectorNamingAnotherTenant_should_notBeFound()
        throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route route = headerRoute(memberTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(404, 404, 404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a default tenant atomic testing and no selector, a member of the default tenant"
            + " still reads, updates and deletes it")
    void given_defaultTenantAtomicTestingAndNoSelector_should_stayReachableForDefaultMember()
        throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      Route route = noSelectorRoute();

      // Act
      int read = getAtomicTesting(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a caller with no membership and no selector, a default tenant atomic testing stays"
            + " reachable by id while the table keeps the v1 filter")
    void given_callerWithNoMembershipAndNoSelector_should_reachDefaultTenantRowsUntilIsolation()
        throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      leaveMemberTenant();
      Route route = noSelectorRoute();

      // Act
      int update = updateAtomicTestingTags(route, injectId);

      // Assert
      assertThat(update).isEqualTo(200);
    }

    @Test
    @DisplayName(
        "given a selector that does not name the row's tenant, execution traces are refused by the"
            + " by-id scope guard")
    void given_selectorNotNamingRowTenant_should_refuseExecutionTracesAtTheScopeGuard()
        throws Exception {
      // Arrange: the row lives in otherTenant, the selector names memberTenant instead, so it is
      // out of the request's scope even though the caller belongs to memberTenant.
      String injectId = seedAtomicTesting(otherTenant);
      Route route = headerRoute(memberTenant);

      // Act
      int status = injectExecutionTraces(route, injectId, "nonexistent-team", "TEAMS");

      // Assert
      assertThat(status).isEqualTo(404);
    }

    @Test
    @DisplayName("given a selector naming a tenant the caller is not a member of, it is refused")
    void given_selectorNamingNonMemberTenant_should_beRefused() throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(otherTenant);
      Route route = headerRoute(otherTenant);

      // Act
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(delete).isEqualTo(403);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @DisplayName(
        "given an atomic testing of a member tenant and a selector naming it, it and its plain"
            + " inject read are found, updated and deleted")
    void given_memberTenantAtomicTestingAndSelectorNamingIt_should_beReadUpdatedAndDeleted()
        throws Exception {
      // Arrange: memberTenant is not the default tenant, so this exercises the by-id load's own
      // scope check rather than the ambient tenant the non-prefixed route falls back to.
      String injectId = seedAtomicTesting(memberTenant);
      Route route = headerRoute(memberTenant);

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, readPlainInject, update, delete})
          .containsExactly(200, 200, 200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }

    @Test
    @DisplayName(
        "given an atomic testing of the caller's only membership and no selector, it and its plain"
            + " inject read are found, updated and deleted")
    void given_onlyMembershipAtomicTestingAndNoSelector_should_beReadUpdatedAndDeleted()
        throws Exception {
      // Arrange: this class's admin caller is not joined to the default tenant, so memberTenant is
      // its only membership and the no-selector scope resolves to it alone.
      String injectId = seedAtomicTesting(memberTenant);
      Route route = noSelectorRoute();

      // Act
      int read = getAtomicTesting(route, injectId);
      int readPlainInject = getInject(route, injectId);
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, readPlainInject, update, delete})
          .containsExactly(200, 200, 200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }
  }
}
