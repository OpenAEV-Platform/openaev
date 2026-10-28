package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.WriteAttrDetectorRecorder.Violation;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CustomDashboardFixture;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The gate must see a write that a request asks for but flushes only later, from a test frame,
 * which is the common shape of a {@code @Transactional} REST test: the controller's insert joins
 * the test transaction and is not flushed until the test reads the data back. If the detector reads
 * the entry frame off the stack at that flush, it attributes the write to the test and waives it,
 * blinding the gate to exactly the class it exists to catch.
 *
 * <p>This exercises the real scenario create on the non-prefixed route with an {@code X-Tenant-Ids}
 * header naming tenants B and C: a scope naming several tenants keeps the ambient tenant, the
 * default one on that route, and the row is stamped from it while the request scope is B and C. A
 * named dashboard skips the single-tenant write resolution, so the create is accepted under that
 * scope. The test does no explicit {@code flush}; the pending insert is forced out by a native read
 * issued from the test method, so the flush stack carries no production frame. The write must still
 * be attributed to {@code ScenarioApi.createScenario}, captured when the application asked for it
 * at {@code persist}, and so the gate must report it.
 *
 * <p>Non-vacuity is shown by a deliberate break (evidence in the task report): neutralising the
 * ask-time capture so the entry frame is read from the flush stack turns this green assertion red,
 * because the deferred write is then attributed to the test and waived.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("The gate sees a request write flushed later from a test frame")
class WriteAttrDeferredAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String CONTROLLER = "io.openaev.rest.scenario.ScenarioApi.createScenario";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;

  private String tenantB;
  private String tenantC;

  @BeforeEach
  void setUp() throws Exception {
    customDashboardComposer.reset();
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-def-b").getId();
    tenantC = tenantHelper.createTenantWithCurrentUser("wattr-def-c").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clear() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Test
  @DisplayName(
      "a header-route write flushed at a later test-frame read is attributed to the controller")
  void deferredHeaderRouteWriteIsAttributedToTheController() throws Exception {
    String dashboardId = dashboardInTenantB();
    WriteAttrDetectorRecorder.start();
    String id =
        postScenario(
            post("/api/scenarios").header("X-Tenant-Ids", tenantB + "," + tenantC).with(csrf()),
            dashboardId);

    // No explicit flush. The pending insert is forced out here, by a native read from the test
    // method, so the flush stack holds no production frame at all.
    String writtenTenant = readTenantFromTestFrame(id);
    WriteAttrDetectorRecorder.stop();

    assertEquals(
        DEFAULT_TENANT,
        writtenTenant,
        "precondition: the create stamps the row with the default tenant, outside the scope");

    List<Violation> scenarioViolations =
        WriteAttrDetectorRecorder.violations().stream()
            .filter(v -> "scenarios".equals(v.table()))
            .toList();
    assertFalse(
        scenarioViolations.isEmpty(),
        "the detector must record the scenario write; violations=" + all());

    // Assert attribution against an empty waiver set, not the frozen baseline: this signature is
    // waived in writeattr-baseline.txt (a not-yet-active table), so the baseline-aware form would
    // hide it. The proof is that the deferred write is keyed to the controller, not to the test
    // that
    // flushed it - independent of the waiver policy.
    List<String> attributed =
        WriteAttrGateExtension.offendingSignatures(
            WriteAttrDetectorRecorder.violations(), Set.of());
    assertTrue(
        attributed.contains("scenarios DEFAULT " + CONTROLLER),
        "the deferred write must be attributed to the controller, not the test frame; attributed="
            + attributed
            + " violations="
            + all());
  }

  private List<Violation> all() {
    return WriteAttrDetectorRecorder.violations();
  }

  /** Native read from the test method: triggers Hibernate autoflush with no production frame. */
  private String readTenantFromTestFrame(String scenarioId) {
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM scenarios WHERE scenario_id = ?1")
            .setParameter(1, scenarioId)
            .getSingleResult();
  }

  private String dashboardInTenantB() {
    CustomDashboard dashboard = CustomDashboardFixture.createDefaultCustomDashboard();
    dashboard.setTenant(new Tenant(tenantB));
    return customDashboardComposer.forCustomDashboard(dashboard).persist().get().getId();
  }

  private String postScenario(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      String dashboardId)
      throws Exception {
    String response =
        mvc.perform(
                request
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"scenario_name\":\"wattr-deferred\",\"scenario_custom_dashboard\":\""
                            + dashboardId
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }
}
