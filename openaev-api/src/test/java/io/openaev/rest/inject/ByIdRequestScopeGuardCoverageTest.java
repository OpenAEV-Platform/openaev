package io.openaev.rest.inject;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.openaev.api.expectations.dto.ExpectationsDriftDismissInput;
import io.openaev.rest.inject.form.InjectAttachmentInput;
import io.openaev.utils.mockUser.WithMockUser;
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
