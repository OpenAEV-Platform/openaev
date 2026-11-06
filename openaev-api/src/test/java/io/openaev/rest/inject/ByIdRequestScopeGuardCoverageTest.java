package io.openaev.rest.inject;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.api.expectations.dto.ExpectationsDriftDismissInput;
import io.openaev.database.model.Capability;
import io.openaev.rest.inject.form.InjectAttachmentInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

/**
 * For an admin caller, the explicit by-id scope guard ({@code requireInjectInScope} / {@code
 * requireAtomicTestingInScope} / {@code injectService#existsInScope}) is the sole tenant check on
 * every one of these endpoints, since {@code PermissionService#checkPermission} lets an admin or a
 * BYPASS-capability caller through without ever resolving the resource's parent. One case per
 * endpoint, all against the same out-of-scope row, proves the guard runs on every one of them
 * rather than only on the few exercised elsewhere in this family.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The by-id scope guard covers every endpoint an admin reaches with no parent load")
class ByIdRequestScopeGuardCoverageTest extends ByIdRequestScopeProductionTablesTestBase {

  private interface Call {
    int status(ByIdRequestScopeGuardCoverageTest test, Route route, String injectId)
        throws Exception;
  }

  static Stream<Arguments> endpoints() {
    return Stream.of(
        Arguments.of(
            "GET /injects/status", (Call) ByIdRequestScopeGuardCoverageTest::getInjectStatus),
        Arguments.of(
            "GET /injects/status-all-traces",
            (Call) ByIdRequestScopeGuardCoverageTest::getInjectStatusAllTraces),
        Arguments.of(
            "GET /injects/execution-traces",
            (Call) ByIdRequestScopeGuardCoverageTest::getInjectExecutionTraces),
        Arguments.of(
            "GET /injects/detection-remediations/{injectId}",
            (Call) ByIdRequestScopeGuardCoverageTest::getDetectionRemediations),
        Arguments.of(
            "GET /injects/{injectId}/payload/{payloadId}/documents",
            (Call) ByIdRequestScopeGuardCoverageTest::getPayloadDocuments),
        Arguments.of(
            "POST /injects/{injectId}/attachment/secret",
            (Call) ByIdRequestScopeGuardCoverageTest::resolveAttachmentSecret),
        Arguments.of(
            "GET /injects/{injectId}/execution-result",
            (Call) ByIdRequestScopeGuardCoverageTest::getExecutionResultPayload),
        Arguments.of(
            "GET /atomic-testings/{injectId}/payload",
            (Call) ByIdRequestScopeGuardCoverageTest::getAtomicTestingPayloadCall),
        Arguments.of(
            "GET /atomic-testings/{injectId}/expectations-drift",
            (Call) ByIdRequestScopeGuardCoverageTest::getExpectationsDrift),
        Arguments.of(
            "POST /atomic-testings/{injectId}/expectations-drift/realign",
            (Call) ByIdRequestScopeGuardCoverageTest::realignExpectationsDrift),
        Arguments.of(
            "PUT /atomic-testings/{injectId}/expectations-drift/dismiss",
            (Call) ByIdRequestScopeGuardCoverageTest::dismissExpectationsDrift),
        Arguments.of(
            "GET /atomic-testings/{injectId}/target_results/{targetId}/types/{targetType}",
            (Call) ByIdRequestScopeGuardCoverageTest::findTargetResult),
        Arguments.of(
            "GET /atomic-testings/{injectId}/target_results/{targetId}/asset_with_agents",
            (Call) ByIdRequestScopeGuardCoverageTest::findTargetResultAssetWithAgents),
        Arguments.of(
            "GET /atomic-testings/{injectId}/target_results/{targetId}/types/{targetType}/merged",
            (Call) ByIdRequestScopeGuardCoverageTest::findTargetResultMerged),
        Arguments.of(
            "GET /atomic-testings/{injectId}/security-platforms",
            (Call) ByIdRequestScopeGuardCoverageTest::getSecurityPlatforms));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("endpoints")
  @DisplayName("given a selector not naming the row's tenant, every endpoint answers 404")
  void given_selectorNotNamingRowTenant_should_refuseEveryEndpoint(String name, Call call)
      throws Exception {
    // Arrange
    String injectId = seedAtomicTesting(otherTenant);
    Route route = headerRoute(memberTenant);

    // Act
    int status = call.status(this, route, injectId);

    // Assert
    assertThat(status).as(name).isEqualTo(404);
  }

  // -- SCENARIO AND SIMULATION INJECT ENDPOINTS --

  /**
   * Every {@code @AccessControl(resourceType = INJECT)} endpoint of {@code ScenarioInjectApi} and
   * {@code SimulationInjectApi}, on the tenant route and on the non-prefixed route with a selector.
   */
  static Stream<Arguments> parentedEndpointsOnBothRoutes() {
    return ParentedInjectEndpoints.endpoints()
        .flatMap(
            arguments -> {
              String name = (String) arguments.get()[0];
              Object call = arguments.get()[1];
              return Stream.of(
                  Arguments.of(
                      name + " (tenant route)",
                      call,
                      (Function<String, Route>) ByIdRequestScopeTestBase::tenantRoute),
                  Arguments.of(
                      name + " (header route)",
                      call,
                      (Function<String, Route>) ByIdRequestScopeTestBase::headerRoute));
            });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("parentedEndpointsOnBothRoutes")
  @DisplayName(
      "given an admin and an inject of another tenant linked to the caller's own exercise or"
          + " scenario, every parented endpoint answers 404 and writes nothing")
  void given_adminAndOtherTenantInject_should_refuseEveryParentedEndpoint(
      String name, ParentedInjectEndpoints.Call call, Function<String, Route> routeOf)
      throws Exception {
    // Arrange: the exercise and scenario of the path belong to the caller's tenant, so only a
    // check of the inject's own tenant can refuse. The inject row lies in another tenant while its
    // parent foreign key still points at the caller's own parent.
    String exerciseId = seedExercise(memberTenant);
    String scenarioId = seedScenario(memberTenant);
    String exerciseInjectId = seedInjectForExercise(otherTenant, exerciseId);
    String scenarioInjectId = seedInjectForScenario(otherTenant, scenarioId);
    String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);
    String originalTitle = injectTitle(injectId);

    // Act
    int status = call.status(this, routeOf.apply(memberTenant), exerciseId, scenarioId, injectId);

    // Assert
    assertThat(status).as(name).isEqualTo(404);
    assertThat(injectTitle(injectId)).as(name + " title").isEqualTo(originalTitle);
    assertThat(atomicTestingExists(injectId)).as(name + " still exists").isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.rest.inject.ParentedInjectEndpoints#endpoints")
  @DisplayName(
      "given an admin and an inject of the caller's own tenant that belongs to another exercise or"
          + " scenario than the path's, every parented endpoint answers 404")
  void given_adminAndInjectOfAnotherParent_should_refuseEveryParentedEndpoint(
      String name, ParentedInjectEndpoints.Call call) throws Exception {
    // Arrange: the inject is in the caller's tenant but under a different parent than the path's.
    String exerciseId = seedExercise(memberTenant);
    String scenarioId = seedScenario(memberTenant);
    String otherExerciseId = seedExercise(memberTenant);
    String otherScenarioId = seedScenario(memberTenant);
    String exerciseInjectId = seedInjectForExercise(memberTenant, otherExerciseId);
    String scenarioInjectId = seedInjectForScenario(memberTenant, otherScenarioId);
    String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);

    // Act
    int status = call.status(this, tenantRoute(memberTenant), exerciseId, scenarioId, injectId);

    // Assert
    assertThat(status).as(name).isEqualTo(404);
    assertThat(atomicTestingExists(injectId)).as(name + " still exists").isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.rest.inject.ParentedInjectEndpoints#endpoints")
  @DisplayName(
      "given an admin and the caller's own tenant's inject under the path's exercise or scenario,"
          + " no parented endpoint refuses it")
  void given_adminAndOwnInject_should_reachEveryParentedHandler(
      String name, ParentedInjectEndpoints.Call call) throws Exception {
    // Arrange
    String exerciseId = seedExercise(memberTenant);
    String scenarioId = seedScenario(memberTenant);
    String exerciseInjectId = seedInjectForExercise(memberTenant, exerciseId);
    String scenarioInjectId = seedInjectForScenario(memberTenant, scenarioId);
    String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);

    // Act
    int status = call.status(this, tenantRoute(memberTenant), exerciseId, scenarioId, injectId);

    // Assert
    assertThat(status).as(name).isNotIn(403, 404);
  }

  /**
   * A tenant BYPASS holds in the tenant of its group, so it skips the parent load on the tenant
   * route only: on the non-prefixed route the ambient tenant is the default one.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.rest.inject.ParentedInjectEndpoints#endpoints")
  @WithMockUser
  @DisplayName(
      "given a BYPASS-capability caller and an inject of another tenant, every parented endpoint"
          + " answers 404 and writes nothing")
  void given_bypassCallerAndOtherTenantInject_should_refuseEveryParentedEndpoint(
      String name, ParentedInjectEndpoints.Call call) throws Exception {
    // Arrange
    tenantHelper.grantCapabilitiesInTenant(memberTenant, Set.of(Capability.BYPASS));
    String exerciseId = seedExercise(memberTenant);
    String scenarioId = seedScenario(memberTenant);
    String exerciseInjectId = seedInjectForExercise(otherTenant, exerciseId);
    String scenarioInjectId = seedInjectForScenario(otherTenant, scenarioId);
    String injectId = ParentedInjectEndpoints.pick(name, exerciseInjectId, scenarioInjectId);
    String originalTitle = injectTitle(injectId);

    // Act
    int status = call.status(this, tenantRoute(memberTenant), exerciseId, scenarioId, injectId);

    // Assert
    assertThat(status).as(name).isEqualTo(404);
    assertThat(injectTitle(injectId)).as(name + " title").isEqualTo(originalTitle);
    assertThat(atomicTestingExists(injectId)).as(name + " still exists").isTrue();
  }

  // -- REQUESTS, one per endpoint under coverage --

  private int getInjectStatus(Route route, String injectId) throws Exception {
    return status(route.with(get(injectsBaseUri(route) + "/status").param("injectId", injectId)));
  }

  private int getInjectStatusAllTraces(Route route, String injectId) throws Exception {
    return status(
        route.with(get(injectsBaseUri(route) + "/status-all-traces").param("injectId", injectId)));
  }

  private int getInjectExecutionTraces(Route route, String injectId) throws Exception {
    return injectExecutionTraces(route, injectId, "nonexistent-team", "TEAMS");
  }

  private int getDetectionRemediations(Route route, String injectId) throws Exception {
    return status(route.with(get(injectsBaseUri(route) + "/detection-remediations/" + injectId)));
  }

  private int getPayloadDocuments(Route route, String injectId) throws Exception {
    return status(
        route.with(
            get(
                injectsBaseUri(route)
                    + "/"
                    + injectId
                    + "/payload/nonexistent-payload/documents")));
  }

  private int resolveAttachmentSecret(Route route, String injectId) throws Exception {
    InjectAttachmentInput input = new InjectAttachmentInput();
    input.setAttachmentId("nonexistent-attachment");
    input.setAuthorisation("Bearer nonexistent");
    return status(
        route.with(
            post(injectsBaseUri(route) + "/" + injectId + "/attachment/secret")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int getExecutionResultPayload(Route route, String injectId) throws Exception {
    return status(
        route.with(
            get(injectsBaseUri(route) + "/" + injectId + "/execution-result")
                .param("targetId", "nonexistent-target")
                .param("targetType", "TEAMS")));
  }

  private int getAtomicTestingPayloadCall(Route route, String injectId) throws Exception {
    return getAtomicTestingPayload(route, injectId);
  }

  private int getExpectationsDrift(Route route, String injectId) throws Exception {
    return status(route.with(get(route.atomicTesting(injectId) + "/expectations-drift")));
  }

  private int realignExpectationsDrift(Route route, String injectId) throws Exception {
    return status(route.with(post(route.atomicTesting(injectId) + "/expectations-drift/realign")));
  }

  private int dismissExpectationsDrift(Route route, String injectId) throws Exception {
    ExpectationsDriftDismissInput input = new ExpectationsDriftDismissInput(true);
    return status(
        route.with(
            put(route.atomicTesting(injectId) + "/expectations-drift/dismiss")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)));
  }

  private int findTargetResult(Route route, String injectId) throws Exception {
    return status(
        route.with(
            get(route.atomicTesting(injectId) + "/target_results/nonexistent-target/types/TEAMS")));
  }

  private int findTargetResultAssetWithAgents(Route route, String injectId) throws Exception {
    return status(
        route.with(
            get(route.atomicTesting(injectId)
                    + "/target_results/nonexistent-target/asset_with_agents")
                .param("expectationType", "PREVENTION")));
  }

  private int findTargetResultMerged(Route route, String injectId) throws Exception {
    return status(
        route.with(
            get(
                route.atomicTesting(injectId)
                    + "/target_results/nonexistent-target/types/TEAMS/merged")));
  }

  private int getSecurityPlatforms(Route route, String injectId) throws Exception {
    return status(route.with(get(route.atomicTesting(injectId) + "/security-platforms")));
  }
}
