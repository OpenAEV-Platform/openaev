package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proof for the write-attribution detector. The detector must record a signature for every
 * INSERT/UPDATE that stamps a {@code tenant_id} outside the transaction's v2 scope, and stay silent
 * when the write is correctly attributed. This class proves it on a real statement, the
 * header-route scenario create, which stamps the row with the default tenant through the ambient
 * {@code TenantContext} while the request scope is tenant B.
 *
 * <p>It wires the detector for this context only ({@link WriteAttrDetectorTestConfig}) and installs
 * the trigger in a rolled-back transaction, so nothing here changes the shared schema. The
 * detection signal (table, written tenant, scope) comes from the DB trigger, read back through the
 * datasource-proxy listener into {@link WriteAttrDetectorRecorder}.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("Write-attribution detector fires on a wrong-tenant write, is silent on a correct one")
class WriteAttributionDetectorSpikeTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-b").getId();
    entityManager
        .unwrap(Session.class)
        .doWork(c -> WriteAttrDetectorTrigger.install(c, TenantTables.selfIsolatedTables()));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.stop();
  }

  @Test
  @DisplayName(
      "header route: the scenario stamped with the default tenant under scope B is detected")
  void headerRouteWriteOutsideScopeIsDetected() throws Exception {
    WriteAttrDetectorRecorder.start();
    String id = postScenario(post("/api/scenarios").header("X-Tenant-Ids", tenantB).with(csrf()));
    entityManager.flush();
    WriteAttrDetectorRecorder.stop();

    // Ground truth: the row really landed in the default tenant, not in B (the live defect).
    assertEquals(DEFAULT_TENANT, scenarioTenant(id), "precondition: the row is misattributed");

    List<WriteAttrDetectorRecorder.Violation> scenarioViolations =
        WriteAttrDetectorRecorder.violations().stream()
            .filter(v -> "scenarios".equals(v.table()))
            .toList();
    assertFalse(
        scenarioViolations.isEmpty(),
        "the detector must record a signature for the scenario stamped outside the request scope");
    WriteAttrDetectorRecorder.Violation violation = scenarioViolations.get(0);
    assertEquals(
        DEFAULT_TENANT, violation.writtenTenant(), "the recorded written tenant is the default");
    assertEquals(tenantB, violation.scope(), "the recorded scope is the request tenant B");
  }

  @Test
  @DisplayName("prefixed route: the scenario correctly attributed to B is not detected (control)")
  void prefixedRouteCorrectlyAttributedIsNotDetected() throws Exception {
    WriteAttrDetectorRecorder.start();
    String id = postScenario(post("/api/tenants/{t}/scenarios", tenantB).with(csrf()));
    entityManager.flush();
    WriteAttrDetectorRecorder.stop();

    assertEquals(tenantB, scenarioTenant(id), "precondition: the row is attributed to B");
    assertTrue(
        WriteAttrDetectorRecorder.violations().stream()
            .noneMatch(v -> "scenarios".equals(v.table())),
        "a scenario written inside the request scope must not be flagged");
  }

  @Test
  @DisplayName("controlled write: out of scope is detected, in scope (incl. fallback) is silent")
  void rawWriteAsymmetryIsDetectedBothWays() {
    // A write whose tenant is inside the scope, including the default-tenant fallback shape (scope
    // is
    // the default tenant, write is the default tenant), must be silent: it is a correct
    // attribution.
    WriteAttrDetectorRecorder.start();
    setScope(DEFAULT_TENANT);
    seedScenario(DEFAULT_TENANT);
    entityManager.flush();
    WriteAttrDetectorRecorder.stop();
    assertTrue(
        WriteAttrDetectorRecorder.violations().stream()
            .noneMatch(v -> "scenarios".equals(v.table())),
        "a write whose tenant is inside the scope must not be flagged");

    // The same write, but stamped with a tenant outside the scope, must be detected.
    WriteAttrDetectorRecorder.start();
    setScope(tenantB);
    seedScenario(DEFAULT_TENANT);
    entityManager.flush();
    WriteAttrDetectorRecorder.stop();
    assertTrue(
        WriteAttrDetectorRecorder.violations().stream()
            .anyMatch(
                v ->
                    "scenarios".equals(v.table())
                        && DEFAULT_TENANT.equals(v.writtenTenant())
                        && tenantB.equals(v.scope())),
        "a write stamped with a tenant outside the scope must be flagged");
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
  }

  private String seedScenario(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios (scenario_id, scenario_name, scenario_mail_from, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, "wattr-raw-" + id)
        .setParameter(3, "planner@openaev.io")
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }

  private String postScenario(MockHttpServletRequestBuilder request) throws Exception {
    String response =
        mvc.perform(
                request
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"scenario_name\":\"wattr-scenario\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }

  private String scenarioTenant(String scenarioId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM scenarios WHERE scenario_id = ?1")
            .setParameter(1, scenarioId)
            .getSingleResult();
  }
}
