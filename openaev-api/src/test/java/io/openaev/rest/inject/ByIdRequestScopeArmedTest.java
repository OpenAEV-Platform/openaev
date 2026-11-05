package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.config.ProductionActiveTables;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Atomic testing by-id reads with {@code injects} isolated on top of the production list. The
 * tenant route keeps working, a selector naming a tenant the caller is not a member of is refused,
 * and a row outside the selector is not found by id.
 *
 * <p>The inject entity still carries the v1 tenant filter, but the by-id guards ({@code
 * InjectService#findInScope} / {@code #existsInScope}) load by primary key, which that filter does
 * not apply to, and keep the row only when its tenant lies in the request's own scope. So a member
 * tenant's row named by the selector is already reachable, updatable and deletable on the
 * non-prefixed route, independently of the table's own activation, which still has to remove the
 * filter from the entity for the reads that stay on the filtered JPQL path (list, search).
 */
@WithMockUser(isAdmin = true)
@DisplayName("Atomic testing by-id reads in the request scope, injects isolated")
class ByIdRequestScopeArmedTest extends ByIdRequestScopeTestBase {

  @DynamicPropertySource
  static void productionActiveTablesWithInjects(DynamicPropertyRegistry registry) {
    Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.add("injects");
    registry.add("openaev.tenant.active-tables", () -> String.join(",", tables));
  }

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
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {read, update, delete}).containsExactly(200, 200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
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
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {update, delete}).containsExactly(404, 404);
      assertThat(atomicTestingExists(injectId)).isTrue();
    }

    @Test
    @DisplayName(
        "given a member tenant atomic testing named by the selector, it is found by id, updated"
            + " and deleted despite the inject entity keeping the v1 filter")
    void given_memberTenantAtomicTestingNamedBySelector_should_beFoundDespiteTheFilter()
        throws Exception {
      // Arrange: the guard loads by primary key, which the v1 filter does not reach, so this does
      // not wait on the table's own activation.
      String injectId = seedAtomicTesting(memberTenant);
      Route route = headerRoute(memberTenant);

      // Act
      int update = updateAtomicTestingTags(route, injectId);
      int delete = deleteAtomicTesting(route, injectId);

      // Assert
      assertThat(new int[] {update, delete}).containsExactly(200, 200);
      assertThat(atomicTestingExists(injectId)).isFalse();
    }

    @Test
    @DisplayName(
        "given a caller with no membership and no selector, a default tenant atomic testing is"
            + " not found by id")
    void given_callerWithNoMembershipAndNoSelector_should_notFindDefaultTenantRow()
        throws Exception {
      // Arrange
      String injectId = seedAtomicTesting(DEFAULT_TENANT);
      leaveMemberTenant();
      Route route = noSelectorRoute();

      // Act
      int update = updateAtomicTestingTags(route, injectId);

      // Assert
      assertThat(update).isEqualTo(404);
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
  }
}
