package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectTeamsInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.rest.inject.form.InjectUpdateStatusInput;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * {@code PermissionService#resolveTarget}'s {@code INJECT} branch resolves the parent of every
 * {@code @AccessControl(resourceType = INJECT)} endpoint, not only the {@code InjectApi} / {@code
 * AtomicTestingApi} ones {@link ByIdRequestScopeGuardCoverageTest} covers: {@code
 * ScenarioInjectApi} and {@code SimulationInjectApi} share the same aspect and never call their own
 * {@code existsInScope} guard, so this by-id load, scoped to the request's {@code TxCtx}, is their
 * only tenant check for a non-admin, non-BYPASS caller.
 */
@WithMockUser
@DisplayName(
    "The AccessControlAspect INJECT parent load covers scenario and simulation inject endpoints")
class ByIdRequestScopeAccessControlAspectCoverageTest extends ByIdRequestScopeNonAdminTestBase {

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;

  private interface Call {
    int status(
        ByIdRequestScopeAccessControlAspectCoverageTest test,
        Route route,
        String exerciseId,
        String scenarioId,
        String injectId)
        throws Exception;
  }

  private static Stream<Arguments> endpoints() {
    return Stream.of(
        Arguments.of(
            "POST /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::duplicateInjectForScenario),
        Arguments.of(
            "PUT /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::updateInjectForScenario),
        Arguments.of(
            "PUT /scenarios/{scenarioId}/injects/{injectId}/activation",
            (Call)
                ByIdRequestScopeAccessControlAspectCoverageTest::updateInjectActivationForScenario),
        Arguments.of(
            "DELETE /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::deleteInjectForScenario),
        Arguments.of(
            "GET /exercises/{exerciseId}/injects/{injectId}/teams",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::exerciseInjectTeams),
        Arguments.of(
            "GET /exercises/{exerciseId}/injects/{injectId}/communications",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::exerciseInjectCommunications),
        Arguments.of(
            "POST /exercises/{exerciseId}/injects/{injectId}",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::duplicateInjectForExercise),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/activation",
            (Call)
                ByIdRequestScopeAccessControlAspectCoverageTest::updateInjectActivationForExercise),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/trigger",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::updateInjectTrigger),
        Arguments.of(
            "POST /exercises/{exerciseId}/injects/{injectId}/status",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::setInjectStatus),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/teams",
            (Call) ByIdRequestScopeAccessControlAspectCoverageTest::updateInjectTeams));
  }

  // -- SEEDING --

  private String seedScenario(String tenantId) {
    return inTenantScope(
        tenantId,
        () -> {
          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setTenant(new Tenant(tenantId));
          return scenarioRepository.save(scenario).getId();
        });
  }

  private String seedInjectForScenario(String tenantId, String scenarioId) {
    return inTenantScope(
        tenantId,
        () -> {
          Inject inject = InjectFixture.getDefaultInject();
          inject.setTenant(new Tenant(tenantId));
          inject.setScenario(scenarioRepository.getReferenceById(scenarioId));
          return injectRepository.save(inject).getId();
        });
  }

  @Nested
  @DisplayName("A tenant member holding every tenant-scoped capability reaches the handler")
  class CapabilityHolderReachesHandler {

    @ParameterizedTest(name = "{0}")
    @MethodSource(
        "io.openaev.rest.inject.ByIdRequestScopeAccessControlAspectCoverageTest#endpoints")
    @DisplayName("given the caller's own tenant's inject on the tenant route, it is not refused")
    void given_ownTenantInjectOnTenantRoute_should_notBeRefused(String name, Call call)
        throws Exception {
      // Arrange
      String callerTenant = memberWithEveryTenantCapability("by-id-aca-own-" + name.hashCode());
      String exerciseId = seedExercise(callerTenant);
      String scenarioId = seedScenario(callerTenant);
      String exerciseInjectId = seedInjectForExercise(callerTenant, exerciseId);
      String scenarioInjectId = seedInjectForScenario(callerTenant, scenarioId);
      String injectId = pick(name, exerciseInjectId, scenarioInjectId);
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
    @MethodSource(
        "io.openaev.rest.inject.ByIdRequestScopeAccessControlAspectCoverageTest#endpoints")
    @DisplayName(
        "given an inject of another tenant linked to the caller's own, writable exercise or"
            + " scenario, the request is refused and writes nothing")
    void given_otherTenantInject_should_refuseAndWriteNothing(String name, Call call)
        throws Exception {
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
      String injectId = pick(name, exerciseInjectId, scenarioInjectId);
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

  /** Scenario endpoints are named with "/scenarios/", exercise endpoints with "/exercises/". */
  private static String pick(String name, String exerciseInjectId, String scenarioInjectId) {
    return name.contains("/scenarios/") ? scenarioInjectId : exerciseInjectId;
  }

  // -- REQUESTS, one per endpoint under coverage --

  private String scenarioInjectUri(Route route, String scenarioId, String injectId) {
    String base =
        route.pathTenant() == null
            ? SCENARIO_URI
            : TENANT_SCENARIO_URI.replace("{tenantId}", route.pathTenant());
    return base + "/" + scenarioId + "/injects/" + injectId;
  }

  private String exerciseInjectUri(Route route, String exerciseId, String injectId) {
    String base =
        route.pathTenant() == null
            ? EXERCISE_URI
            : TENANT_EXERCISE_URI.replace("{tenantId}", route.pathTenant());
    return base + "/" + exerciseId + "/injects/" + injectId;
  }

  private int duplicateInjectForScenario(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(route.with(post(scenarioInjectUri(route, scenarioId, injectId))));
  }

  private int updateInjectForScenario(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    InjectInput input = new InjectInput();
    input.setTitle("updated by aspect coverage test");
    return status(
        route.with(
            put(scenarioInjectUri(route, scenarioId, injectId))
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int updateInjectActivationForScenario(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    InjectUpdateActivationInput input = new InjectUpdateActivationInput();
    input.setEnabled(false);
    return status(
        route.with(
            put(scenarioInjectUri(route, scenarioId, injectId) + "/activation")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int deleteInjectForScenario(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(route.with(delete(scenarioInjectUri(route, scenarioId, injectId))));
  }

  private int exerciseInjectTeams(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(route.with(get(exerciseInjectUri(route, exerciseId, injectId) + "/teams")));
  }

  private int exerciseInjectCommunications(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(
        route.with(get(exerciseInjectUri(route, exerciseId, injectId) + "/communications")));
  }

  private int duplicateInjectForExercise(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(route.with(post(exerciseInjectUri(route, exerciseId, injectId))));
  }

  private int updateInjectActivationForExercise(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    InjectUpdateActivationInput input = new InjectUpdateActivationInput();
    input.setEnabled(false);
    return status(
        route.with(
            put(exerciseInjectUri(route, exerciseId, injectId) + "/activation")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int updateInjectTrigger(
      Route route, String exerciseId, String scenarioId, String injectId) throws Exception {
    return status(route.with(put(exerciseInjectUri(route, exerciseId, injectId) + "/trigger")));
  }

  private int setInjectStatus(Route route, String exerciseId, String scenarioId, String injectId)
      throws Exception {
    InjectUpdateStatusInput input = new InjectUpdateStatusInput();
    input.setStatus("EXECUTED");
    return status(
        route.with(
            post(exerciseInjectUri(route, exerciseId, injectId) + "/status")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int updateInjectTeams(Route route, String exerciseId, String scenarioId, String injectId)
      throws Exception {
    InjectTeamsInput input = new InjectTeamsInput();
    input.setTeamIds(List.of());
    return status(
        route.with(
            put(exerciseInjectUri(route, exerciseId, injectId) + "/teams")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }
}
