package io.openaev.config;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
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
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The gate must see a write that production code asks for but that is flushed only later, from a
 * test frame, which is the common shape of a {@code @Transactional} test: the insert joins the test
 * transaction and is not flushed until the test reads the data back. If the detector reads the
 * entry frame off the stack at that flush, it attributes the write to the test and waives it,
 * blinding the gate to exactly the class it exists to catch.
 *
 * <p>The first test persists a scenario through {@code ScenarioService.createScenario}, stamped
 * with the default tenant while the scope is tenant B. No controller writes a scenario outside the
 * request scope any more, so the service is driven directly, as the composite-key test drives the
 * executor registration. The test does no explicit {@code flush}; the pending insert is forced out
 * by a native read issued from the test method, so the flush stack carries no production frame. The
 * write must still be attributed to the service, captured when it asked for the write at {@code
 * persist}, and so the gate must report it.
 *
 * <p>Non-vacuity is shown by a deliberate break: neutralising the ask-time capture so the entry
 * frame is read from the flush stack turns this green assertion red, because the deferred write is
 * then attributed to the test and waived. The last test is the control on the real request: both
 * scenario creates on the header route take the tenant of the request scope and report nothing.
 */
@Transactional
@Import(WriteAttrDetectorTestConfig.class)
@WithMockUser(isAdmin = true)
@DisplayName("The gate sees a production write flushed later from a test frame")
class WriteAttrDeferredAttributionTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String SERVICE =
      "io.openaev.service.scenario.ScenarioService.createScenario";
  private static final String SCENARIO_BODY = "{\"scenario_name\":\"wattr-deferred\"}";
  private static final String SCENARIO_WITH_CONTRACTS_BODY =
      "{\"locale\":\"en\",\"scenario_input\":"
          + SCENARIO_BODY
          + ",\"injector_contract_search_pagination_input\":{\"injector_contract_ids_to_process\":"
          + "[\"00000000-0000-0000-0000-000000000000\"]}}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioService scenarioService;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("wattr-def-b").getId();
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
      "a production persist flushed at a later test-frame read is attributed to the service")
  void given_persistFlushedFromTestFrame_should_attributeToTheService() {
    // Arrange
    TenantContext.clearCurrentTenant();
    WriteAttrDetectorRecorder.start();
    setScope(tenantB);
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(DEFAULT_TENANT));

    // Act: the service persists a scenario stamped with the default tenant while the scope is B.
    String id = scenarioService.createScenario(scenario).getId();
    // No explicit flush. The pending insert is forced out here, by a native read from the test
    // method, so the flush stack holds no production frame at all.
    String writtenTenant = readTenantFromTestFrame(id);
    WriteAttrDetectorRecorder.stop();

    // Assert
    assertEquals(DEFAULT_TENANT, writtenTenant, "precondition: the row carries the default tenant");
    List<Violation> scenarioViolations =
        WriteAttrDetectorRecorder.violations().stream()
            .filter(v -> "scenarios".equals(v.table()))
            .toList();
    assertFalse(
        scenarioViolations.isEmpty(),
        "the detector must record the scenario write; violations=" + all());
    // Against an empty waiver set, not the frozen baseline: the proof is that the deferred write is
    // keyed to the service, not to the test that flushed it, independent of the waiver policy.
    List<String> attributed =
        WriteAttrGateExtension.offendingSignatures(
            WriteAttrDetectorRecorder.violations(), Set.of());
    assertTrue(
        attributed.contains("scenarios DEFAULT " + SERVICE),
        "the deferred write must be attributed to the service, not the test frame; attributed="
            + attributed
            + " violations="
            + all());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "/with-injector-contracts"})
  @DisplayName(
      "a header-route scenario create attributed to the request scope is not reported, deferred or"
          + " not")
  void given_headerRouteCreateInScope_should_reportNoScenarioViolation(String path)
      throws Exception {
    // Arrange
    WriteAttrDetectorRecorder.start();

    // Act
    String id =
        postScenario(
            post(SCENARIO_URI + path).header("X-Tenant-Ids", tenantB).with(csrf()),
            path.isEmpty() ? SCENARIO_BODY : SCENARIO_WITH_CONTRACTS_BODY);
    String writtenTenant = readTenantFromTestFrame(id);
    WriteAttrDetectorRecorder.stop();

    // Assert
    assertEquals(tenantB, writtenTenant, "the create takes the tenant of the request scope");
    assertTrue(
        WriteAttrDetectorRecorder.violations().stream()
            .noneMatch(v -> "scenarios".equals(v.table())),
        "a scenario written inside the request scope must not be flagged; violations=" + all());
  }

  private void setScope(String scope) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :scope, true)")
        .setParameter("scope", scope)
        .getSingleResult();
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

  private String postScenario(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      String body)
      throws Exception {
    String response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }
}
