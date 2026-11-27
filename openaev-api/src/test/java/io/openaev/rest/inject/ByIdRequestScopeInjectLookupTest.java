package io.openaev.rest.inject;

import static io.openaev.api.detection_remediation.DetectionRemediationApi.DETECTION_REMEDIATION_URI;
import static io.openaev.api.detection_remediation.DetectionRemediationApi.TENANT_DETECTION_REMEDIATION_URI;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.rest.inject.form.InjectDependencyIdInput;
import io.openaev.rest.inject.form.InjectDependencyInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Endpoints that name an inject without it being their resource: the inject a new inject depends
 * on, and the inject a detection remediation is generated for. An admin or BYPASS caller meets no
 * other tenant check on them, and a primary-key load is not narrowed by the v1 tenant filter, so
 * each loads that inject inside the request's scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("An inject named by id in a request body or a sub-resource path is read in scope")
class ByIdRequestScopeInjectLookupTest extends ByIdRequestScopeProductionTablesTestBase {

  private static final String TITLE = "depends on an inject of another tenant";

  @Autowired private DataSource dataSource;

  /** The route of the request, built from the tenant it names. */
  private static Stream<Function<String, Route>> routes() {
    return Stream.of(ByIdRequestScopeTestBase::tenantRoute, ByIdRequestScopeTestBase::headerRoute);
  }

  private interface CreateCall {
    int status(
        ByIdRequestScopeInjectLookupTest test,
        Route route,
        String exerciseId,
        String scenarioId,
        InjectInput input)
        throws Exception;
  }

  static Stream<Arguments> createEndpointsOnBothRoutes() {
    return Stream.of(
            Arguments.of(
                "POST /scenarios/{scenarioId}/injects",
                (CreateCall)
                    (test, route, exerciseId, scenarioId, input) ->
                        test.postCreate(
                            scenarioUri(route) + "/" + scenarioId + "/injects",
                            route,
                            asJsonString(input))),
            Arguments.of(
                "POST /scenarios/{scenarioId}/injects/bulk",
                (CreateCall)
                    (test, route, exerciseId, scenarioId, input) ->
                        test.postCreate(
                            scenarioUri(route) + "/" + scenarioId + "/injects/bulk",
                            route,
                            asJsonString(List.of(input)))),
            Arguments.of(
                "POST /exercises/{exerciseId}/injects",
                (CreateCall)
                    (test, route, exerciseId, scenarioId, input) ->
                        test.postCreate(
                            exerciseUri(route) + "/" + exerciseId + "/injects",
                            route,
                            asJsonString(input))),
            Arguments.of(
                "POST /exercises/{exerciseId}/injects/bulk",
                (CreateCall)
                    (test, route, exerciseId, scenarioId, input) ->
                        test.postCreate(
                            exerciseUri(route) + "/" + exerciseId + "/injects/bulk",
                            route,
                            asJsonString(List.of(input)))))
        .flatMap(
            arguments ->
                routes()
                    .map(
                        routeOf ->
                            Arguments.of(
                                arguments.get()[0]
                                    + (routeOf.apply("t").pathTenant() == null
                                        ? " (header route)"
                                        : " (tenant route)"),
                                arguments.get()[1],
                                routeOf)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("createEndpointsOnBothRoutes")
  @DisplayName(
      "given an inject depending on an inject of another tenant, every create endpoint answers"
          + " 404 and creates nothing")
  void given_dependencyOnOtherTenantInject_should_refuseEveryCreateEndpoint(
      String name, CreateCall call, Function<String, Route> routeOf) throws Exception {
    // Arrange
    String exerciseId = seedExercise(memberTenant);
    String scenarioId = seedScenario(memberTenant);
    String foreignParentId = seedAtomicTesting(otherTenant);
    InjectInput input = new InjectInput();
    input.setTitle(TITLE);
    input.setInjectorContract(EMAIL_DEFAULT);
    input.setDependsDuration(0L);
    InjectDependencyIdInput relationship = new InjectDependencyIdInput();
    relationship.setInjectParentId(foreignParentId);
    InjectDependencyInput dependency = new InjectDependencyInput();
    dependency.setRelationship(relationship);
    input.setDependsOn(List.of(dependency));

    // Act
    int status = call.status(this, routeOf.apply(memberTenant), exerciseId, scenarioId, input);

    // Assert
    assertThat(status).as(name).isEqualTo(404);
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT count(*) FROM injects WHERE inject_title = ?", Integer.class, TITLE))
        .as(name + " created injects")
        .isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.rest.inject.ByIdRequestScopeInjectLookupTest#routes")
  @DisplayName(
      "given an inject of another tenant, generating its detection remediation answers 404")
  void given_otherTenantInject_should_refuseDetectionRemediation(Function<String, Route> routeOf)
      throws Exception {
    // Arrange
    String injectId = seedAtomicTesting(otherTenant);
    Route route = routeOf.apply(memberTenant);
    String base =
        route.pathTenant() == null
            ? DETECTION_REMEDIATION_URI
            : TENANT_DETECTION_REMEDIATION_URI.replace("{tenantId}", route.pathTenant());

    // Act
    int status =
        status(
            route.with(
                post(
                    base
                        + "/rules/inject/"
                        + injectId
                        + "/security-platform/nonexistent-platform")));

    // Assert
    assertThat(status).isEqualTo(404);
  }

  private int postCreate(String uri, Route route, String body) throws Exception {
    return status(route.with(post(uri).content(body).contentType(MediaType.APPLICATION_JSON)));
  }

  private static String scenarioUri(Route route) {
    return route.pathTenant() == null
        ? SCENARIO_URI
        : TENANT_SCENARIO_URI.replace("{tenantId}", route.pathTenant());
  }

  private static String exerciseUri(Route route) {
    return route.pathTenant() == null
        ? EXERCISE_URI
        : TENANT_EXERCISE_URI.replace("{tenantId}", route.pathTenant());
  }
}
