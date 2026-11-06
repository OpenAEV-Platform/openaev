package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.rest.inject.ByIdRequestScopeTestBase.Route;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.rest.inject.form.InjectTeamsInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.rest.inject.form.InjectUpdateStatusInput;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.http.MediaType;

/**
 * The {@code @AccessControl(resourceType = INJECT)} endpoints of {@code ScenarioInjectApi} and
 * {@code SimulationInjectApi}, one request each. Shared by the suites that prove their by-id scope
 * checks, so that the list of endpoints under proof is written once.
 */
final class ParentedInjectEndpoints {

  private ParentedInjectEndpoints() {}

  interface Call {
    int status(
        ByIdRequestScopeTestBase test,
        Route route,
        String exerciseId,
        String scenarioId,
        String injectId)
        throws Exception;
  }

  static Stream<Arguments> endpoints() {
    return Stream.of(
        Arguments.of(
            "POST /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ParentedInjectEndpoints::duplicateInjectForScenario),
        Arguments.of(
            "PUT /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ParentedInjectEndpoints::updateInjectForScenario),
        Arguments.of(
            "PUT /scenarios/{scenarioId}/injects/{injectId}/activation",
            (Call) ParentedInjectEndpoints::updateInjectActivationForScenario),
        Arguments.of(
            "DELETE /scenarios/{scenarioId}/injects/{injectId}",
            (Call) ParentedInjectEndpoints::deleteInjectForScenario),
        Arguments.of(
            "GET /exercises/{exerciseId}/injects/{injectId}/teams",
            (Call) ParentedInjectEndpoints::exerciseInjectTeams),
        Arguments.of(
            "GET /exercises/{exerciseId}/injects/{injectId}/communications",
            (Call) ParentedInjectEndpoints::exerciseInjectCommunications),
        Arguments.of(
            "POST /exercises/{exerciseId}/injects/{injectId}",
            (Call) ParentedInjectEndpoints::duplicateInjectForExercise),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/activation",
            (Call) ParentedInjectEndpoints::updateInjectActivationForExercise),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/trigger",
            (Call) ParentedInjectEndpoints::updateInjectTrigger),
        Arguments.of(
            "POST /exercises/{exerciseId}/injects/{injectId}/status",
            (Call) ParentedInjectEndpoints::setInjectStatus),
        Arguments.of(
            "PUT /exercises/{exerciseId}/injects/{injectId}/teams",
            (Call) ParentedInjectEndpoints::updateInjectTeams));
  }

  /** Scenario endpoints are named with "/scenarios/", exercise endpoints with "/exercises/". */
  static String pick(String name, String exerciseInjectId, String scenarioInjectId) {
    return name.contains("/scenarios/") ? scenarioInjectId : exerciseInjectId;
  }

  // -- REQUESTS, one per endpoint under coverage --

  private static String scenarioInjectUri(Route route, String scenarioId, String injectId) {
    String base =
        route.pathTenant() == null
            ? SCENARIO_URI
            : TENANT_SCENARIO_URI.replace("{tenantId}", route.pathTenant());
    return base + "/" + scenarioId + "/injects/" + injectId;
  }

  private static String exerciseInjectUri(Route route, String exerciseId, String injectId) {
    String base =
        route.pathTenant() == null
            ? EXERCISE_URI
            : TENANT_EXERCISE_URI.replace("{tenantId}", route.pathTenant());
    return base + "/" + exerciseId + "/injects/" + injectId;
  }

  private static int duplicateInjectForScenario(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(route.with(post(scenarioInjectUri(route, scenarioId, injectId))));
  }

  private static int updateInjectForScenario(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    InjectInput input = new InjectInput();
    input.setTitle("updated by aspect coverage test");
    return test.status(
        route.with(
            put(scenarioInjectUri(route, scenarioId, injectId))
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private static int updateInjectActivationForScenario(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    InjectUpdateActivationInput input = new InjectUpdateActivationInput();
    input.setEnabled(false);
    return test.status(
        route.with(
            put(scenarioInjectUri(route, scenarioId, injectId) + "/activation")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private static int deleteInjectForScenario(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(route.with(delete(scenarioInjectUri(route, scenarioId, injectId))));
  }

  private static int exerciseInjectTeams(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(route.with(get(exerciseInjectUri(route, exerciseId, injectId) + "/teams")));
  }

  private static int exerciseInjectCommunications(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(
        route.with(get(exerciseInjectUri(route, exerciseId, injectId) + "/communications")));
  }

  private static int duplicateInjectForExercise(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(route.with(post(exerciseInjectUri(route, exerciseId, injectId))));
  }

  private static int updateInjectActivationForExercise(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    InjectUpdateActivationInput input = new InjectUpdateActivationInput();
    input.setEnabled(false);
    return test.status(
        route.with(
            put(exerciseInjectUri(route, exerciseId, injectId) + "/activation")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private static int updateInjectTrigger(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    return test.status(
        route.with(put(exerciseInjectUri(route, exerciseId, injectId) + "/trigger")));
  }

  private static int setInjectStatus(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    InjectUpdateStatusInput input = new InjectUpdateStatusInput();
    input.setStatus("EXECUTED");
    return test.status(
        route.with(
            post(exerciseInjectUri(route, exerciseId, injectId) + "/status")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private static int updateInjectTeams(
      ByIdRequestScopeTestBase test,
      Route route,
      String exerciseId,
      String scenarioId,
      String injectId)
      throws Exception {
    InjectTeamsInput input = new InjectTeamsInput();
    input.setTeamIds(List.of());
    return test.status(
        route.with(
            put(exerciseInjectUri(route, exerciseId, injectId) + "/teams")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }
}
