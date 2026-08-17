package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.autonomous.AutonomousDirectiveRepository;
import io.openaev.database.repository.autonomous.AutonomousEventRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.security.token.XtmJwksExtractor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with the {@code autonomous_*} tables activated, the tenant scope set from
 * the request isolates the OPERATOR-facing autonomous-run endpoints: the owner tenant sees its run,
 * timeline and directives, a different tenant gets a 404 (the run row itself is invisible, so
 * nothing hangs off it), and a scope-less repository read is fail-closed. This exercises the whole
 * chain (the {@code TxCtx} binding, the transaction aspect, the {@code can_access_tenant} rewrite,
 * and the JPA reads), so it proves the isolation claim rather than the mere presence of a guard -
 * the HTTP complement to the inspector's own SQL-layer tests, mirroring {@code
 * AttackPathHttpIsolationTest}.
 *
 * <p>The orchestrator CALLBACK endpoints are the deliberate exception, and the second half of this
 * suite pins their SERVICE-IDENTITY contract: on the legacy non-prefixed route, and ONLY for the
 * VERIFIED XTM One cross-platform service identity (the server-side marker {@link
 * XtmJwksExtractor#CROSS_PLATFORM_ATTRIBUTE} stamped after full JWT validation - the harness
 * authenticates through {@code @WithMockUser}, so the tests stamp the validated marker directly),
 * they are scoped from the parent run (the {@link io.openaev.config.RunTenantScope} argument), so a
 * service caller whose scope does NOT pin the run's tenant still records the run's events, drives
 * its status and consumes its directives - every write stamped with the run's own tenant. XTM One
 * reaches them with a per-user JWT that carries no tenant claim, so this is what keeps a long run
 * authoring and settling itself once the caller's scope no longer pins the run's tenant. A caller
 * WITHOUT the verified marker keeps caller-authorized resolution on the same handlers - the
 * cross-tenant IDOR case proves a normal EE user cannot reach a foreign tenant's run through a
 * callback even knowing its id. The derivation is also route-scoped: the same handlers on the
 * TENANT-PREFIXED route stay caller-authorized (the URL names the tenant, rights are the boundary),
 * which this suite pins too. Operator isolation is proven unchanged alongside it. The derived scope
 * also respects tenant liveness: a run whose tenant is soft-deleted is refused even for the
 * verified service identity (a grace-period tenant is out of every caller scope, and the
 * run-derived scope must not re-admit it), and reactivating the tenant restores the callbacks.
 *
 * <p>Both scope routes are covered, because the orchestrator's callbacks ride the legacy
 * non-prefixed mapping: the tenant-prefixed path (operator UI) and the plain path where the scope
 * comes from the caller's membership / the {@code X-Tenant-Ids} header (XTM One service account).
 *
 * <p>The write half is covered end-to-end too: creating a run, appending a timeline event and
 * queueing a directive through the real endpoints must stamp the raw {@code tenant_id} of every new
 * row with the selected / parent-run tenant. The inspector only guards reads - INSERT attribution
 * is explicit application code since {@code TenantBaseListener} was removed - so a missing {@code
 * setTenant} would pass any read-only suite and misattribute production data; these cases pin the
 * attribution at the column level and the refusal outside a valid scope. A deliberately
 * multi-tenant header scope is covered too: parent-derived writes must stay deterministic (landing
 * on the run's own tenant) rather than refusing the broad scope or spilling into another selected
 * tenant - only a create without any tenant selector is ambiguous under a broad scope and refused.
 *
 * <p>Each test stays on a single tenant selection: the per-request scope is set once and the aspect
 * refuses to redefine it within one transaction.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=autonomous_runs,autonomous_events,autonomous_directives"
    })
@WithMockUser(isAdmin = true)
@DisplayName("autonomous run isolation through the real HTTP endpoints")
class AutonomousRunHttpIsolationTest extends IntegrationTest {

  // Both mappings the API declares, derived from its own constants rather than retyped: a route
  // renamed on the controller must break this test, not silently stop covering it.
  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs";
  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private AutonomousRunRepository runRepository;
  @Autowired private AutonomousEventRepository eventRepository;
  @Autowired private AutonomousDirectiveRepository directiveRepository;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String runId;
  private String eventId;
  private String directiveId;

  @BeforeEach
  void seedRunUnderTenantA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("auto-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("auto-iso-b").getId();
    runId = seedRun(tenantA);
    eventId = seedEvent(tenantA, runId);
    directiveId = seedDirective(tenantA, runId);
  }

  // The orchestrator's own request shape: in production the callback bearer is an XTM One
  // cross-platform JWT that XtmJwksExtractor fully validates (trusted issuer + JWKS signature +
  // expected audience) before stamping this server-side marker, and TxCtxArgumentResolver grants
  // run-authoritative scope on exactly that attribute. The MockMvc harness authenticates through
  // @WithMockUser rather than the token filter, so the tests stamp the validated marker directly -
  // a request attribute is server-side only and can never be supplied by a real client.
  private static MockHttpServletRequestBuilder asVerifiedServiceIdentity(
      MockHttpServletRequestBuilder request) {
    return request.requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
  }

  @Test
  @DisplayName("under the owner tenant's path: the run is visible")
  void getUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_id").value(runId));
  }

  @Test
  @DisplayName("under another tenant's path: the same run does not exist (404, no leak)")
  void getUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}", tenantB, runId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("the run list under the owner tenant's path carries the run")
  void listUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED, tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.autonomous_run_id=='" + runId + "')]").exists());
  }

  @Test
  @DisplayName("the run list under another tenant's path does not carry the owner's run")
  void listUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED, tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.autonomous_run_id=='" + runId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("under the owner tenant's path: the decision timeline is visible")
  void timelineUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/timeline", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].autonomous_event_id").value(eventId));
  }

  @Test
  @DisplayName("under another tenant's path: the timeline 404s with the hidden run")
  void timelineUnderOtherTenantIsHidden() throws Exception {
    // The timeline resolves the run first; a foreign tenant cannot even see the run row, so the
    // request dies on the lookup instead of leaking an (empty or not) timeline shape.
    mvc.perform(get(SCOPED + "/{runId}/timeline", tenantB, runId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under the owner tenant's path: the steering directives are visible")
  void directivesUnderOwnerTenantIsVisible() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/directives", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].autonomous_directive_id").value(directiveId));
  }

  @Test
  @DisplayName("under another tenant's path: the directives 404 with the hidden run")
  void directivesUnderOtherTenantIsHidden() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/directives", tenantB, runId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (the orchestrator's route): the owner's run is visible")
  void getViaHeaderForOwnerTenantIsVisible() throws Exception {
    // Second scope-carrying route: the same handlers are also mapped without the tenant prefix
    // (the XTM One callbacks ride this one), and the scope then comes from the header instead of
    // the path. Different plumbing, so path coverage does not imply header coverage.
    mvc.perform(get(PLAIN + "/{runId}", runId).header("X-Tenant-Ids", tenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_id").value(runId));
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: another tenant selected, the run does not exist")
  void getViaHeaderForOtherTenantIsHidden() throws Exception {
    mvc.perform(get(PLAIN + "/{runId}", runId).header("X-Tenant-Ids", tenantB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "POST create under the selected tenant stamps the run and its event with that tenant")
  void createRunViaApiStampsSelectedTenant() throws Exception {
    // The real operator write path, end to end: the API provisions the scenario + plan substrate
    // and INSERTs the run row. The inspector cannot attribute an INSERT, so the explicit
    // attributeRunTenant/setTenant chain is what lands tenant_id - assert it at the raw column.
    String response =
        mvc.perform(
                post(SCOPED, tenantA)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective\": \"Own the file server\", \"plan_mode\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_run_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdRunId = JsonPath.read(response, "$.autonomous_run_id");

    assertThat(rawTenantId("autonomous_runs", "autonomous_run_id", createdRunId))
        .isEqualTo(tenantA);
    // The creation narration (AutonomousEventService#doAppend) is attributed to the same tenant.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", createdRunId))
        .isEqualTo(1L);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", createdRunId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST event on the orchestrator's route stamps the parent run's tenant")
  void recordEventViaCallbackRouteStampsParentRunTenant() throws Exception {
    // The exact callback write AutonomousEventService#doAppend serves: the tenant must come from
    // the parent run, never from a thread-local default (the orchestrator rides the non-prefixed
    // route as the verified service identity, where the legacy TenantContext default would
    // misattribute the row).
    String response =
        mvc.perform(
                asVerifiedServiceIdentity(post(PLAIN + "/{runId}/events", runId))
                    .with(csrf())
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\": \"DECISION\", \"title\": \"Pivot to the DC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_event_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdEventId = JsonPath.read(response, "$.autonomous_event_id");

    assertThat(rawTenantId("autonomous_events", "autonomous_event_id", createdEventId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST directive under the owner tenant stamps the run's tenant on both new rows")
  void addDirectiveViaApiStampsParentRunTenant() throws Exception {
    // Steering needs a live run (settled runs refuse directives) with no simulation so the write
    // stays free of chaining machinery. Seeded raw, mutated through the real endpoint.
    String activeRunId = seedActiveRun(tenantA);
    String response =
        mvc.perform(
                post(SCOPED + "/{runId}/directives", tenantA, activeRunId)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\": \"Focus on lateral movement\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_directive_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdDirectiveId = JsonPath.read(response, "$.autonomous_directive_id");

    assertThat(rawTenantId("autonomous_directives", "autonomous_directive_id", createdDirectiveId))
        .isEqualTo(tenantA);
    // The "Operator directive queued" narration carries the same tenant as the directive.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", activeRunId)).isEqualTo(1L);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", activeRunId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST event under a multi-tenant scope lands on the parent run's tenant")
  void recordEventUnderMultiTenantScopeStampsParentRunTenant() throws Exception {
    // A broad, multi-value X-Tenant-Ids header is a legitimate request state on the plain route (a
    // service identity or an administrator can hold several memberships). The verified service
    // callback derives its scope from the run itself (@RunTenantScope), so the multi-value selector
    // is simply ignored: the append must succeed and land on the run's own tenant - never on
    // another selected tenant, never as a refusal. Only a create (an operator write) without a
    // single-tenant selector is ambiguous under a broad scope
    // (createOutsideValidWriteScopeIsRefusedAndWritesNothing).
    String response =
        mvc.perform(
                asVerifiedServiceIdentity(post(PLAIN + "/{runId}/events", runId))
                    .with(csrf())
                    .header("X-Tenant-Ids", tenantA + "," + tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\": \"DECISION\", \"title\": \"Broad-scope append\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_event_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdEventId = JsonPath.read(response, "$.autonomous_event_id");

    assertThat(rawTenantId("autonomous_events", "autonomous_event_id", createdEventId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName("POST directive under a multi-tenant scope stamps the parent run's tenant")
  void addDirectiveUnderMultiTenantScopeStampsParentRunTenant() throws Exception {
    // Same contract as the broad-scope event append, on the operator steering write and through
    // the header route: the directive and its "Operator directive queued" narration both derive
    // from the parent run, whatever the width of the caller's scope.
    String activeRunId = seedActiveRun(tenantA);
    String response =
        mvc.perform(
                post(PLAIN + "/{runId}/directives", activeRunId)
                    .with(csrf())
                    .header("X-Tenant-Ids", tenantA + "," + tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\": \"Broad-scope steering\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_directive_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdDirectiveId = JsonPath.read(response, "$.autonomous_directive_id");

    assertThat(rawTenantId("autonomous_directives", "autonomous_directive_id", createdDirectiveId))
        .isEqualTo(tenantA);
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", activeRunId)).isEqualTo(1L);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", activeRunId))
        .isEqualTo(tenantA);
  }

  @Test
  @DisplayName(
      "POST event from a caller scope that excludes the run's tenant still records it, stamped from"
          + " the parent run (service-identity callback)")
  void recordEventFromForeignCallerScopeStampsParentRunTenant() throws Exception {
    // The regression fix: an orchestrator callback is a SERVICE-IDENTITY operation whose tenant is
    // the parent run's, not the caller's. XTM One rides the non-prefixed route with a per-user JWT
    // that pins no tenant (validated as the cross-platform service identity), so a callback must be
    // able to write the run's own timeline even when the caller's scope does not include the run's
    // tenant. Here the verified service caller deliberately selects ONLY tenantB (which does NOT
    // own the run), yet the event is recorded and stamped with the parent run's tenant (tenantA) -
    // read at the raw column. Before the @RunTenantScope hardening this exact append 404'd on the
    // tenant-filtered run lookup.
    String response =
        mvc.perform(
                asVerifiedServiceIdentity(post(PLAIN + "/{runId}/events", runId))
                    .with(csrf())
                    .header("X-Tenant-Ids", tenantB)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\": \"DECISION\", \"title\": \"service-identity append\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_event_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdEventId = JsonPath.read(response, "$.autonomous_event_id");

    assertThat(rawTenantId("autonomous_events", "autonomous_event_id", createdEventId))
        .isEqualTo(tenantA);
    // The seeded event plus the one recorded through the foreign-scope callback.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(2L);
  }

  @Test
  @DisplayName(
      "POST status from a caller scope that excludes the run's tenant still drives the run"
          + " (service-identity callback)")
  void updateStatusFromForeignCallerScopeDrivesParentRun() throws Exception {
    // The orchestrator settles / parks the run through this callback with the same per-user,
    // no-tenant JWT (validated as the cross-platform service identity). A live run under tenantA is
    // driven to WAITING_INPUT by a verified service caller selecting only tenantB; the transition
    // lands on the run itself (raw status column), proving the write is scoped from the run rather
    // than refused for being outside the caller's scope.
    String activeRunId = seedActiveRun(tenantA);
    mvc.perform(
            asVerifiedServiceIdentity(post(PLAIN + "/{runId}/status", activeRunId))
                .with(csrf())
                .header("X-Tenant-Ids", tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"WAITING_INPUT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_status").value("WAITING_INPUT"));

    assertThat(
            rawColumn("autonomous_runs", "autonomous_run_status", "autonomous_run_id", activeRunId))
        .isEqualTo("WAITING_INPUT");
  }

  @Test
  @DisplayName(
      "POST directives/consume from a caller scope that excludes the run's tenant reads and clears"
          + " the run's directives (service-identity callback)")
  void consumeDirectivesFromForeignCallerScopeReadsParentRun() throws Exception {
    // The orchestrator consumes operator steering at the start of each cycle with the same
    // no-tenant JWT (validated as the cross-platform service identity). A verified service caller
    // selecting only tenantB still receives the run's seeded directive and marks it consumed (its
    // raw status flips under the run's own tenant).
    mvc.perform(
            asVerifiedServiceIdentity(post(PLAIN + "/{runId}/directives/consume", runId))
                .with(csrf())
                .header("X-Tenant-Ids", tenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].autonomous_directive_id").value(directiveId));

    assertThat(
            rawColumn(
                "autonomous_directives",
                "autonomous_directive_status",
                "autonomous_directive_id",
                directiveId))
        .isEqualTo("CONSUMED");
  }

  @Test
  @DisplayName(
      "POST event WITHOUT the verified service identity, on a run outside the caller's tenants, is"
          + " refused (404) and writes nothing - the cross-tenant IDOR is closed")
  void recordEventWithoutServiceIdentityOutsideCallerTenantsIsClosed() throws Exception {
    // The IDOR the service-identity gate closes: a NORMAL authenticated EE user (no validated
    // cross-platform marker on the request) who learned a run id must not reach a run in a tenant
    // they are not a member of through the callback endpoints. Without the marker the
    // @RunTenantScope handler resolves the caller's own memberships exactly like any plain
    // endpoint; the foreign run row stays invisible under that scope, the append dies on the run
    // lookup (404), and nothing is written. Before the gate this exact request appended to the
    // foreign tenant's timeline.
    String foreignTenant = tenantHelper.createTenant("auto-iso-foreign").getId();
    // Tenant creation auto-attaches the creating user (TenantUserService dependency manager);
    // detach it so the tenant is genuinely foreign to the caller.
    String userId = testUserHolder.get().getId();
    tenantRepository.removeUserFromTenant(userId, foreignTenant);
    tenantMembershipCacheManager.evict(userId, foreignTenant);
    String foreignRunId = seedRun(foreignTenant);

    mvc.perform(
            post(PLAIN + "/{runId}/events", foreignRunId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"DECISION\", \"title\": \"cross-tenant idor probe\"}"))
        .andExpect(status().isNotFound());

    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", foreignRunId))
        .isEqualTo(0L);
  }

  @Test
  @DisplayName(
      "POST event on a run whose tenant is soft-deleted is refused (404) even for the verified"
          + " service identity - and works again once the tenant is reactivated")
  void recordEventOnSoftDeletedTenantRunIsFailClosedForServiceIdentity() throws Exception {
    // A soft-deleted tenant is excluded from every caller scope for its whole retention grace
    // period (TenantRepository.findTenantsByUserId and TenantMembershipCacheManager both filter on
    // tenant_deleted_at IS NULL), so no operator can reach the run. The run-derived service scope
    // must not quietly re-admit it: the locator's liveness predicate makes the derivation resolve
    // empty exactly like an unknown run, the verified service caller gets a 404, and the timeline
    // stays untouched.
    softDeleteTenant(tenantA);
    mvc.perform(
            asVerifiedServiceIdentity(post(PLAIN + "/{runId}/events", runId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"DECISION\", \"title\": \"grace-period append\"}"))
        .andExpect(status().isNotFound());
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(1L);

    // The refusal is the tenant's liveness, not a permanent latch on the run: reactivating the
    // tenant within the grace period (TenantService#reactivateTenant's contract) restores the
    // callback path, so an admin undoing a deletion gets the run's autonomy back with the tenant.
    reactivateTenant(tenantA);
    mvc.perform(
            asVerifiedServiceIdentity(post(PLAIN + "/{runId}/events", runId))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"DECISION\", \"title\": \"post-reactivation append\"}"))
        .andExpect(status().isOk());
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(2L);
  }

  @Test
  @DisplayName(
      "POST event via the tenant-prefixed route stays caller-scoped: a foreign tenant's path 404s"
          + " and writes nothing")
  void recordEventViaTenantPrefixedRouteOutsideRunTenantIsRefused() throws Exception {
    // The service-identity derivation exists only on the legacy non-prefixed route. On the
    // tenant-prefixed route the same handler keeps the operator contract: the URL names the
    // tenant, so a caller addressing tenantB cannot reach (or write) tenantA's run through the
    // @RunTenantScope-annotated endpoint - the run row is invisible under the addressed scope and
    // the append dies on the run lookup, leaving the timeline untouched.
    mvc.perform(
            post(SCOPED + "/{runId}/events", tenantB, runId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"DECISION\", \"title\": \"prefixed cross-tenant append\"}"))
        .andExpect(status().isNotFound());

    // Only the seeded event remains: nothing was written through the foreign tenant's path.
    assertThat(rawCount("autonomous_events", "autonomous_event_run_id", runId)).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "GET attack-path state via the owner tenant's path works caller-scoped (operator parity)")
  void attackPathStateViaTenantPrefixedRouteUnderOwnerTenantIsVisible() throws Exception {
    // The UI reads the attack-path state through the tenant-prefixed route (the tenant prefix is
    // added centrally by the frontend's buildUri). With the derivation route-scoped, this read
    // resolves the caller's addressed tenant like every other operator endpoint and must keep
    // working; the seeded run has no simulation and no scenario, so the state is simply empty.
    mvc.perform(get(SCOPED + "/{runId}/attack-path/state", tenantA, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @DisplayName("POST bare create with no selector lands on the default tenant (platform fallback)")
  void createBareRunWithoutSelectorLandsOnDefaultTenantForDefaultMember() throws Exception {
    // The platform-wide rule for a request with no tenant selector (issues #6331 / #6332, same
    // convention as TxCtxArgumentResolver#fallbackSelector and TenantContext#getCurrentTenant):
    // a multi-tenant caller falls back to the DEFAULT tenant - deterministic and well-known,
    // never a "first membership" guess - and only a multi-tenant caller WITHOUT default-tenant
    // access is refused, which createOutsideValidWriteScopeIsRefusedAndWritesNothing pins. The
    // bare autonomous create converges to the same outcome through the provisioned scenario's
    // legacy default-tenant stamping, validated against the caller's scope. This case pins the
    // convention: 200, and every new row lands on the default tenant at the raw column level.
    String userId = testUserHolder.get().getId();
    if (!tenantMembershipCacheManager
        .findTenantIdsByUserId(userId)
        .contains(Tenant.DEFAULT_TENANT_UUID)) {
      tenantRepository.addUserToTenant(userId, Tenant.DEFAULT_TENANT_UUID);
      tenantMembershipCacheManager.evict(userId, Tenant.DEFAULT_TENANT_UUID);
    }

    String response =
        mvc.perform(
                post(PLAIN)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"objective\": \"Bare default-scope run\", \"plan_mode\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autonomous_run_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdRunId = JsonPath.read(response, "$.autonomous_run_id");

    assertThat(rawTenantId("autonomous_runs", "autonomous_run_id", createdRunId))
        .isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    assertThat(rawTenantId("autonomous_events", "autonomous_event_run_id", createdRunId))
        .isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("POST create outside a single valid write scope is refused (400), no run written")
  void createOutsideValidWriteScopeIsRefusedAndWritesNothing() throws Exception {
    // A two-tenant selection on the plain route: the auto-provisioned scenario lands in the
    // caller's legacy default tenant (no path prefix sets TenantContext), which is outside the
    // selected {A, B} scope, so TenantWriteScopeResolver refuses the attribution with 400
    // (TENANT_WRITE_SCOPE) before the run INSERT - same contract as an ambiguous bare create,
    // whose multi-tenant scope cannot pin the row to one tenant either.
    mvc.perform(
            post(PLAIN)
                .with(csrf())
                .header("X-Tenant-Ids", tenantA + "," + tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"objective\": \"ambiguous-scope-objective\", \"plan_mode\": true}"))
        .andExpect(status().isBadRequest());

    assertThat(rawCount("autonomous_runs", "autonomous_run_objective", "ambiguous-scope-objective"))
        .isEqualTo(0L);
  }

  @Test
  @DisplayName("no scope set: every autonomous read is empty although the rows exist (fail-closed)")
  void readWithoutScopeIsFailClosed() {
    // No TxCtx in this test transaction, so the aspect never set app.current_tenants and the
    // inspector denies every row on all three tables. The rows exist, they are only hidden.
    assertThat(runRepository.findById(runId)).isEmpty();
    assertThat(eventRepository.findByRunIdOrderBySequenceAsc(runId)).isEmpty();
    assertThat(directiveRepository.findByRunIdOrderByCreatedAtAsc(runId)).isEmpty();
    assertThat(rawCount("autonomous_runs", "autonomous_run_id", runId)).isEqualTo(1L);
    assertThat(rawCount("autonomous_events", "autonomous_event_id", eventId)).isEqualTo(1L);
    assertThat(rawCount("autonomous_directives", "autonomous_directive_id", directiveId))
        .isEqualTo(1L);
  }

  // Native seed, not the API: the setup seeds two tenants, and an explicit tenant_id lets the rows
  // land without a request scope while keeping the read cases independent of any write endpoint.
  // The run is seeded COMPLETED so the read-path reconcile is a no-op and no simulation is needed.
  private String seedRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'COMPLETED')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedEvent(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_events (autonomous_event_id, tenant_id,"
                + " autonomous_event_run_id, autonomous_event_sequence, autonomous_event_type,"
                + " autonomous_event_title)"
                + " VALUES (:id, :tenant, :run, 1, 'STATUS', 'Run created')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  // A live (steerable) run for the write cases: directives are refused on a settled run, and the
  // run carries no simulation so no reconcile / chaining machinery is dragged into the write.
  private String seedActiveRun(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id,"
                + " autonomous_run_objective, autonomous_run_status)"
                + " VALUES (:id, :tenant, 'Own the domain', 'RUNNING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  // Soft delete / reactivation at the column TenantService drives, on the test's own transaction:
  // the tenants table is not tenant-active, so the native update is not rewritten, and the
  // locator's DataSourceUtils connection joins this transaction and sees the flag flip.
  private void softDeleteTenant(String tenantId) {
    entityManager
        .createNativeQuery("UPDATE tenants SET tenant_deleted_at = now() WHERE tenant_id = :id")
        .setParameter("id", tenantId)
        .executeUpdate();
  }

  private void reactivateTenant(String tenantId) {
    entityManager
        .createNativeQuery("UPDATE tenants SET tenant_deleted_at = NULL WHERE tenant_id = :id")
        .setParameter("id", tenantId)
        .executeUpdate();
  }

  private String seedDirective(String tenantId, String runId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
                + " autonomous_directive_run_id, autonomous_directive_content,"
                + " autonomous_directive_status)"
                + " VALUES (:id, :tenant, :run, 'Focus on the domain controller', 'PENDING')")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
    return id;
  }

  // Ground truth for write attribution: the raw tenant_id column of a row the API just wrote,
  // read over plain JDBC on the test's own connection (sees uncommitted rows, no inspector
  // rewrite). Null when the row does not exist, so a missing row fails the equality loudly.
  private String rawTenantId(String table, String idColumn, String id) {
    return rawColumn(table, "tenant_id", idColumn, id);
  }

  // Ground truth for any string column of a row the API just wrote, over plain JDBC on the test's
  // own connection (sees uncommitted rows, no inspector rewrite). Null when the row does not exist.
  private String rawColumn(String table, String valueColumn, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT " + valueColumn + " FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  // Ground truth, bypassing the scope: raw JDBC on the test's own connection sees the uncommitted
  // seed and the inspector does not rewrite a statement it never generated.
  private long rawCount(String table, String idColumn, String id) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM " + table + " WHERE " + idColumn + " = ?")) {
                statement.setString(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
