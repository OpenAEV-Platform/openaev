package io.openaev.rest.finding;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Vulnerability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.VulnerabilityFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.VulnerabilityComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
// The test profile declares no active tables, so removing the v1 @Filter would leave this suite
// asserting an isolation nothing enforces. The list is not limited to findings because
// @TestPropertySource REPLACES the property rather than adding to it: naming findings alone would
// deactivate assets and asset_groups, which ARE active in production, and the suite would test less
// than production while looking stricter.
@TestPropertySource(properties = "openaev.tenant.active-tables=findings,assets,asset_groups")
@DisplayName("Finding search tenant isolation tests")
class FindingSearchApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @Autowired private FindingComposer findingComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;

  @BeforeEach
  void setUp() {
    findingComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
    vulnerabilityComposer.reset();
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Global search from tenant Y should not return findings created in tenant X")
    void given_findingInTenantX_should_notAppearInGlobalSearchFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          performSearch("/api/tenants/" + tenantY.getId() + "/findings/search")
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals(Integer.valueOf(0), JsonPath.read(response, "$.totalElements"));
    }

    @Test
    @DisplayName("Scenario findings search from tenant Y should not access tenant X scenario")
    void given_scenarioInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/scenarios/"
                      + data.scenarioId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Simulation findings search from tenant Y should not access tenant X simulation")
    void given_simulationInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/exercises/"
                      + data.simulationId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Inject findings search from tenant Y should not access tenant X inject")
    void given_injectInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/injects/"
                      + data.injectId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    @Test
    @DisplayName("Endpoint findings search from tenant Y should not access tenant X endpoint")
    void given_endpointInTenantX_should_notBeAccessibleFromTenantY() throws Exception {
      // Arrange
      Tenant tenantX = createTenantX();
      Tenant tenantY = createTenantY();
      TestData data = createFindingDataInTenant(tenantX.getId());

      entityManager.flush();
      entityManager.clear();

      // Act
      int response =
          performSearch(
                  "/api/tenants/"
                      + tenantY.getId()
                      + "/findings/endpoints/"
                      + data.endpointId()
                      + "/search")
              .andReturn()
              .getResponse()
              .getContentLength();

      // Assert
      assertThat(response).isZero();
    }

    private Tenant createTenantX() throws Exception {
      return tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant X",
          Set.of(
              Capability.ACCESS_FINDINGS,
              Capability.ACCESS_ASSESSMENT,
              Capability.ACCESS_ASSETS,
              Capability.MANAGE_ASSESSMENT));
    }

    private Tenant createTenantY() throws Exception {
      return tenantIsolationHelper.createTenantWithCapabilities(
          "Tenant Y",
          Set.of(
              Capability.ACCESS_FINDINGS, Capability.ACCESS_ASSESSMENT, Capability.ACCESS_ASSETS));
    }

    private TestData createFindingDataInTenant(String tenantId) {
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);

      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
              .withEndpoint(endpointWrapper);

      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);

      ExerciseComposer.Composer simulationWrapper =
          exerciseComposer
              .forExercise(ExerciseFixture.createFinishedAttackExercise())
              .withInject(injectWrapper);

      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withSimulation(simulationWrapper)
              .persist()
              .get();

      Exercise simulation = scenario.getExercises().iterator().next();
      Inject inject = simulation.getInjects().getFirst();
      String endpointId = inject.getAssets().getFirst().getId();

      return new TestData(scenario.getId(), simulation.getId(), inject.getId(), endpointId);
    }

    private org.springframework.test.web.servlet.ResultActions performSearch(String uri)
        throws Exception {
      SearchPaginationInput input = PaginationFixture.getDefault().build();
      return mvc.perform(
          post(uri)
              .content(asJsonString(input))
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .with(csrf()));
    }
  }

  @Nested
  @DisplayName("Severity filter")
  @WithMockUser
  class SeverityFilter {

    @Test
    @DisplayName(
        "Filtering on a severity should return the CVE findings of that rating, read from the"
            + " vulnerabilities of the same tenant only")
    void given_severityFilter_should_returnTheCveFindingsOfThatRatingInTheSameTenant()
        throws Exception {
      // Arrange
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.ACCESS_FINDINGS, Capability.ACCESS_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_FINDINGS, Capability.ACCESS_ASSESSMENT));
      String criticalCve = VulnerabilityFixture.getRandomExternalVulnerabilityId();
      String mediumCve = VulnerabilityFixture.getRandomExternalVulnerabilityId();

      tenantIsolationHelper.switchToTenant(tenantX.getId(), entityManager);
      persistVulnerability(criticalCve, "9.8");
      persistVulnerability(mediumCve, "4.5");
      persistFindings(
          List.of(
              cveFinding(criticalCve),
              cveFinding(mediumCve),
              FindingFixture.createDefaultTextFindingWithRandomValue()));

      // The same CVE scored critical in another tenant must not make tenant X's finding critical.
      tenantIsolationHelper.switchToTenant(tenantY.getId(), entityManager);
      persistVulnerability(mediumCve, "9.8");

      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/findings/search?distinct=true")
                      .content(
                          asJsonString(
                              PaginationFixture.simpleSearchWithAndOperator(
                                  "finding_severity", "CRITICAL", Filters.FilterOperator.eq)))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertEquals(Integer.valueOf(1), JsonPath.read(response, "$.totalElements"));
      assertEquals(criticalCve, JsonPath.read(response, "$.content[0].finding_value"));
    }

    private Finding cveFinding(String cveId) {
      Finding finding = FindingFixture.createDefaultCveFindingWithRandomTitle();
      finding.setValue(cveId);
      return finding;
    }

    private void persistVulnerability(String cveId, String cvss) {
      Vulnerability vulnerability = VulnerabilityFixture.createVulnerabilityInput(cveId);
      vulnerability.setCvssV31(new BigDecimal(cvss));
      vulnerabilityComposer.forVulnerability(vulnerability).persist();
    }

    private void persistFindings(List<Finding> findings) {
      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject());
      findings.forEach(finding -> injectWrapper.withFinding(findingComposer.forFinding(finding)));
      exerciseComposer
          .forExercise(ExerciseFixture.createFinishedAttackExercise())
          .withInject(injectWrapper)
          .persist();
    }
  }

  private record TestData(
      String scenarioId, String simulationId, String injectId, String endpointId) {}
}
