package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proof that autonomous-run isolation does NOT depend on the admin bypass: a plain user who is a
 * member of BOTH tenants, holding only the minimal simulation capability ({@code
 * ACCESS_ASSESSMENT}, which grants SIMULATION READ - the capability {@code
 * AutonomousRunAccessControl} checks for a run bound to neither a simulation nor a scenario), still
 * cannot see the owner tenant's run when the request selects the other tenant. The 404s here come
 * from the statement inspector hiding the row under the selected scope, not from RBAC: the same
 * user, same capability, same membership reads the run fine under the owner tenant's path (the
 * control case).
 *
 * <p>The admin-scoped complement lives in {@link AutonomousRunHttpIsolationTest}; this class exists
 * because every case there runs with {@code isAdmin = true}, which short-circuits the capability
 * checks and could in principle mask an isolation model that only held for admins.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=autonomous_runs,autonomous_events,autonomous_directives"
    })
@WithMockUser(isAdmin = false)
@DisplayName("autonomous run isolation holds for a non-admin spanning two tenants")
class AutonomousRunNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> SIMULATION_READ = Set.of(Capability.ACCESS_ASSESSMENT);

  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String ownerTenant;
  private String otherTenant;
  private String runId;

  @BeforeEach
  void seedRunUnderOwnerTenantForATwoTenantNonAdmin() throws Exception {
    // The non-admin belongs to BOTH tenants and holds the same minimal read capability in each:
    // if isolation were membership- or capability-driven, every case below would leak.
    ownerTenant =
        tenantHelper.createTenantWithCapabilities("auto-nonadmin-owner", SIMULATION_READ).getId();
    otherTenant =
        tenantHelper.createTenantWithCapabilities("auto-nonadmin-other", SIMULATION_READ).getId();
    runId = seedRun(ownerTenant);
    seedEvent(ownerTenant, runId);
    seedDirective(ownerTenant, runId);
  }

  @Test
  @DisplayName("control: the capability-only member reads the run under the owner tenant")
  void getUnderOwnerTenantIsVisibleToNonAdmin() throws Exception {
    // Without this case the 404s below could be RBAC refusals in disguise; a 200 here pins them
    // to the tenant scope.
    mvc.perform(get(SCOPED + "/{runId}", ownerTenant, runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autonomous_run_id").value(runId));
  }

  @Test
  @DisplayName("under the other tenant the same member gets a 404 for the run")
  void getUnderOtherTenantIsHiddenFromNonAdmin() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}", otherTenant, runId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("the run list under the other tenant does not carry the owner's run")
  void listUnderOtherTenantIsHiddenFromNonAdmin() throws Exception {
    mvc.perform(get(SCOPED, otherTenant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.autonomous_run_id=='" + runId + "')]").doesNotExist());
  }

  @Test
  @DisplayName("under the other tenant the timeline 404s with the hidden run")
  void timelineUnderOtherTenantIsHiddenFromNonAdmin() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/timeline", otherTenant, runId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under the other tenant the directives 404 with the hidden run")
  void directivesUnderOtherTenantIsHiddenFromNonAdmin() throws Exception {
    mvc.perform(get(SCOPED + "/{runId}/directives", otherTenant, runId))
        .andExpect(status().isNotFound());
  }

  // Same native seeding as the admin suite: an explicit tenant_id lands the rows without a
  // request scope, and COMPLETED keeps the read-path reconcile a no-op (no simulation needed).
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

  private void seedEvent(String tenantId, String runId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_events (autonomous_event_id, tenant_id,"
                + " autonomous_event_run_id, autonomous_event_sequence, autonomous_event_type,"
                + " autonomous_event_title)"
                + " VALUES (:id, :tenant, :run, 1, 'STATUS', 'Run created')")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
  }

  private void seedDirective(String tenantId, String runId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
                + " autonomous_directive_run_id, autonomous_directive_content,"
                + " autonomous_directive_status)"
                + " VALUES (:id, :tenant, :run, 'Focus on the domain controller', 'PENDING')")
        .setParameter("id", UUID.randomUUID().toString())
        .setParameter("tenant", tenantId)
        .setParameter("run", runId)
        .executeUpdate();
  }
}
