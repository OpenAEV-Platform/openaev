package io.openaev.rest.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code PermissionService#resolveTarget}'s {@code INJECT} branch resolves the parent of every
 * {@code @AccessControl(resourceType = INJECT)} endpoint, including the {@code ScenarioInjectApi}
 * and {@code SimulationInjectApi} ones. For a non-admin, non-BYPASS caller it is one of two
 * independent refusals of an inject outside the request's scope: the handlers also resolve the
 * inject themselves ({@code requireInjectInScenario} / {@code requireInjectInSimulation}), the only
 * check an admin or BYPASS caller meets, which {@link ByIdRequestScopeGuardCoverageTest} covers.
 */
@WithMockUser
@DisplayName(
    "A non-admin caller reaches the inject endpoints of its own tenant's scenarios and simulations,"
        + " and not those of another tenant's")
class ByIdRequestScopeAccessControlAspectCoverageTest extends ByIdRequestScopeNonAdminTestBase {

  @Nested
  @DisplayName("A tenant member holding every tenant-scoped capability reaches the handler")
  class CapabilityHolderReachesHandler {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.inject.ParentedInjectEndpoints#endpoints")
    @DisplayName("given the caller's own tenant's inject on the tenant route, it is not refused")
    void given_ownTenantInjectOnTenantRoute_should_notBeRefused(
        String name, ParentedInjectEndpoints.Call call) throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-aca-own-" + name.hashCode());
      String exerciseId = seedExercise(callerTenant);
      String scenarioId = seedScenario(callerTenant);
      String exerciseInjectId = seedInjectForExercise(callerTenant, exerciseId);
      String scenarioInjectId = seedInjectForScenario(callerTenant, scenarioId);
      String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);
      Route route = tenantRoute(callerTenant);

      // Act
      int status =
          call.status(
              ByIdRequestScopeAccessControlAspectCoverageTest.this,
              route,
              exerciseId,
              scenarioId,
              injectId);

      // Assert: the capability reaches the caller's own tenant, so the gate admits the request to
      // the handler regardless of that handler's own business-logic status code.
      assertThat(status).as(name).isNotIn(403, 404);
    }
  }

  @Nested
  @DisplayName("An inject of another tenant is refused before the handler runs")
  class OtherTenantInjectRefused {

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.openaev.rest.inject.ParentedInjectEndpoints#endpoints")
    @DisplayName(
        "given an inject of another tenant linked to the caller's own, writable exercise or"
            + " scenario, the request is refused and writes nothing")
    void given_otherTenantInject_should_refuseAndWriteNothing(
        String name, ParentedInjectEndpoints.Call call) throws Exception {
      // Arrange: the exercise/scenario named by the path belongs to the caller's own tenant, so a
      // handler-level findInScope(ctx, exerciseId/scenarioId) check alone would let this through.
      // The inject's own row is persisted under otherTenant while its exercise/scenario foreign
      // key still points at the caller's own parent, isolating PermissionService#resolveTarget's
      // own by-id load of the inject (keyed on the inject's own tenant, not its parent's) from that
      // other check.
      String callerTenant = memberWithEveryTenantCapability("by-id-aca-other-" + name.hashCode());
      String exerciseId = seedExercise(callerTenant);
      String scenarioId = seedScenario(callerTenant);
      String exerciseInjectId = seedInjectForExercise(otherTenant, exerciseId);
      String scenarioInjectId = seedInjectForScenario(otherTenant, scenarioId);
      String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);
      String originalTitle = injectTitle(injectId);
      Route route = tenantRoute(callerTenant);

      // Act
      int status =
          call.status(
              ByIdRequestScopeAccessControlAspectCoverageTest.this,
              route,
              exerciseId,
              scenarioId,
              injectId);

      // Assert
      assertThat(status).as(name).isEqualTo(404);
      assertThat(injectTitle(injectId)).as(name + " title").isEqualTo(originalTitle);
      assertThat(atomicTestingExists(injectId)).as(name + " still exists").isTrue();
    }
  }
}
