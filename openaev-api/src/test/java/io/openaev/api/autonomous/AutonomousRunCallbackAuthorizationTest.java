package io.openaev.api.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.RunTenantScope;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.security.token.XtmJwksExtractor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression suite for #326: the orchestrator callbacks skip the declarative RBAC, so a caller that
 * is NOT the verified XTM One service identity must hold the run's LAUNCH to mutate through them
 * and its READ to read them, exactly like on the operator surface. Runs on the non-prefixed route
 * the advisory used, with a non-admin whose authority comes from real tenant capabilities.
 */
@Transactional
@WithMockUser(isAdmin = false)
@DisplayName("orchestrator callbacks enforce the run's READ / LAUNCH for a non-service caller")
class AutonomousRunCallbackAuthorizationTest extends IntegrationTest {

  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI;

  // SIMULATION READ without LAUNCH: the observer of the advisory.
  private static final Set<Capability> OBSERVER = Set.of(Capability.ACCESS_ASSESSMENT);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String runId;
  private String directiveId;

  @BeforeEach
  void seedLiveRunInDefaultTenant() {
    // Membership only: each test grants the capabilities it needs.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    runId = seedActiveRun();
    directiveId = seedPendingDirective(runId);
  }

  static Stream<Arguments> mutatingCallbacks() {
    return Stream.of(
        callback("setScope", id -> json(put(PLAIN + "/{runId}/scope", id), "{\"scope\": []}")),
        callback(
            "recordEvent",
            id ->
                json(
                    post(PLAIN + "/{runId}/events", id),
                    "{\"type\": \"DECISION\", \"title\": \"probe\"}")),
        callback(
            "updateStatus",
            id -> json(post(PLAIN + "/{runId}/status", id), "{\"status\": \"COMPLETED\"}")),
        callback("consumeDirectives", id -> post(PLAIN + "/{runId}/directives/consume", id)),
        callback(
            "appendAttackPathStep",
            id ->
                json(
                    post(PLAIN + "/{runId}/attack-path/steps", id),
                    "{\"inject\": {\"inject_title\": \"probe\"}}")),
        callback(
            "updateAttackPathStep",
            id ->
                json(
                    put(PLAIN + "/{runId}/attack-path/steps/{stepId}", id, UUID.randomUUID()),
                    "{\"inject\": {\"inject_title\": \"probe\"}}")),
        callback(
            "deleteAttackPathStep",
            id -> delete(PLAIN + "/{runId}/attack-path/steps/{stepId}", id, UUID.randomUUID())),
        callback("evaluateAttackPath", id -> post(PLAIN + "/{runId}/attack-path/evaluate", id)),
        callback(
            "promoteFindingToAsset",
            id ->
                post(
                    PLAIN + "/{runId}/findings/{findingId}/promote-to-asset",
                    id,
                    UUID.randomUUID())),
        callback(
            "ensureTargetTeam",
            id ->
                json(
                    post(PLAIN + "/{runId}/target-teams", id),
                    "{\"player_ids\": [\"" + UUID.randomUUID() + "\"]}")));
  }

  static Stream<Arguments> readCallbacks() {
    return Stream.of(
        callback("getScope", id -> get(PLAIN + "/{runId}/scope", id)),
        callback("attackPathState", id -> get(PLAIN + "/{runId}/attack-path/state", id)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("mutatingCallbacks")
  @DisplayName("an observer (READ, no LAUNCH) is refused with 403 and changes nothing")
  void observerCannotMutateThroughCallback(
      String handler, Function<String, MockHttpServletRequestBuilder> request) throws Exception {
    tenantHelper.grantCapabilitiesInTenant(Tenant.DEFAULT_TENANT_UUID, OBSERVER);

    mvc.perform(request.apply(runId).with(csrf())).andExpect(status().isForbidden());

    assertThat(rawString("autonomous_runs", "autonomous_run_status", "autonomous_run_id", runId))
        .isEqualTo("RUNNING");
    assertThat(
            rawString(
                "autonomous_directives",
                "autonomous_directive_status",
                "autonomous_directive_id",
                directiveId))
        .isEqualTo("PENDING");
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readCallbacks")
  @DisplayName("a caller without READ on the run is refused with 403")
  void callerWithoutReadCannotReadThroughCallback(
      String handler, Function<String, MockHttpServletRequestBuilder> request) throws Exception {
    mvc.perform(request.apply(runId)).andExpect(status().isForbidden());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("readCallbacks")
  @DisplayName("an observer keeps reading through the callback (the cockpit polls the state)")
  void observerCanReadThroughCallback(
      String handler, Function<String, MockHttpServletRequestBuilder> request) throws Exception {
    tenantHelper.grantCapabilitiesInTenant(Tenant.DEFAULT_TENANT_UUID, OBSERVER);

    mvc.perform(request.apply(runId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("a LAUNCH holder still drives the run through a callback")
  void launchHolderCanMutateThroughCallback() throws Exception {
    tenantHelper.grantCapabilitiesInTenant(
        Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.LAUNCH_ASSESSMENT));

    mvc.perform(
            json(
                    post(PLAIN + "/{runId}/events", runId),
                    "{\"type\": \"DECISION\", \"title\": \"operator\"}")
                .with(csrf()))
        .andExpect(status().isOk());

    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("the verified XTM One service identity is authorized by the run, whatever its owner")
  void verifiedServiceIdentityIsAuthorizedByTheRun() throws Exception {
    // XTM One mints per-user JWTs whose owner can differ from the run operator: the marker alone
    // authorizes it, here on a user holding nothing but READ.
    tenantHelper.grantCapabilitiesInTenant(Tenant.DEFAULT_TENANT_UUID, OBSERVER);

    mvc.perform(
            json(
                    post(PLAIN + "/{runId}/events", runId),
                    "{\"type\": \"DECISION\", \"title\": \"orchestrator\"}")
                .requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE)
                .with(csrf()))
        .andExpect(status().isOk());
    mvc.perform(
            post(PLAIN + "/{runId}/attack-path/evaluate", runId)
                .requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE)
                .with(csrf()))
        .andExpect(status().isOk());

    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(1L);
  }

  @Test
  @DisplayName("every orchestrator callback is covered by this suite")
  void everyCallbackIsCovered() {
    // A new callback must be added to one of the sources above, which fails until it is gated.
    Set<String> covered =
        Stream.concat(mutatingCallbacks(), readCallbacks())
            .map(arguments -> (String) arguments.get()[0])
            .collect(Collectors.toSet());
    Set<String> callbacks =
        Arrays.stream(AutonomousRunApi.class.getDeclaredMethods())
            .filter(
                method ->
                    Arrays.stream(method.getParameters())
                        .anyMatch(parameter -> parameter.isAnnotationPresent(RunTenantScope.class)))
            .map(Method::getName)
            .collect(Collectors.toSet());

    assertThat(covered).containsExactlyInAnyOrderElementsOf(callbacks);
  }

  private static Arguments callback(
      String handler, Function<String, MockHttpServletRequestBuilder> request) {
    return Arguments.of(handler, request);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  // Live and bound to neither a simulation nor a scenario: the run's authority is then the
  // caller's SIMULATION capability, and no chaining machinery is involved.
  private String seedActiveRun() {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'RUNNING')")
        .setParameter("id", id)
        .setParameter("tenant", Tenant.DEFAULT_TENANT_UUID)
        .executeUpdate();
    return id;
  }

  private String seedPendingDirective(String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
                + " autonomous_directive_run_id, autonomous_directive_content,"
                + " autonomous_directive_status)"
                + " VALUES (:id, :tenant, :run, 'Focus on the domain controller', 'PENDING')")
        .setParameter("id", id)
        .setParameter("tenant", Tenant.DEFAULT_TENANT_UUID)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  // Plain JDBC on the test's own connection: sees uncommitted rows, no inspector rewrite.
  private String rawString(String table, String valueColumn, String idColumn, String id) {
    return rawQuery(
        "SELECT " + valueColumn + " FROM " + table + " WHERE " + idColumn + " = ?",
        id,
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private long rawCount(String table, String idColumn, String id) {
    return rawQuery(
        "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?",
        id,
        rows -> rows.next() ? rows.getLong(1) : 0L);
  }

  private <T> T rawQuery(String sql, String id, ResultSetReader<T> reader) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return reader.read(rows);
                }
              }
            });
  }

  @FunctionalInterface
  private interface ResultSetReader<T> {
    T read(ResultSet rows) throws SQLException;
  }
}
