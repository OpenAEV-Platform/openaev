package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * T0.10, verification only: does a create land in the right tenant on both the tenant-prefixed
 * route ({@code /api/tenants/{id}/...}) and the non-prefixed route with {@code X-Tenant-Ids}?
 *
 * <p>{@code TenantInterceptor} sets {@code TenantContext} only from the {@code {tenantId}} path
 * variable; on the header route it stays unset and {@code TenantContext.getCurrentTenant()} returns
 * the default tenant. {@code TenantBaseListener} stamps a new {@code TenantBase} row's tenant from
 * {@code TenantContext} when the entity carries none. So an endpoint that resolves its read scope
 * from the request ({@code TxCtx}) but saves the entity without setting its tenant writes to the
 * default tenant on the header route while reading in the selected tenant: a cross-tenant write.
 *
 * <p>Ground truth is read with a native query: {@code scenarios} and {@code exercises} are still on
 * the v1 {@code @Filter}, native SQL never reaches that filter nor the v2 statement inspector, so
 * it sees every tenant's rows regardless of the scope in effect. The test transaction rolls back,
 * so a misattributed row never survives the method.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Write attribution on the prefixed and header routes (T0.10, verification only)")
class WriteAttributionRouteTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantB;

  @BeforeEach
  void seedTenantB() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("t010-b").getId();
  }

  @AfterEach
  void clearContext() {
    TenantContext.clearCurrentTenant();
  }

  // region scenarios (ScenarioApi.createScenario, POST {/api/scenarios |
  // /api/tenants/{id}/scenarios})

  @Nested
  @DisplayName("POST /api/scenarios")
  class Scenarios {

    @Test
    @DisplayName("prefixed route: the scenario is attributed to the path tenant (control)")
    void scenarioPrefixedRouteAttributesToPathTenant() throws Exception {
      String id = postScenario(post("/api/tenants/{t}/scenarios", tenantB).with(csrf()), null);
      assertEquals(
          tenantB,
          scenarioTenant(id),
          "the scenario created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the scenario must be attributed to X-Tenant-Ids, not to the default")
    void scenarioHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postScenario(post("/api/scenarios").header("X-Tenant-Ids", tenantB).with(csrf()), null);
      assertEquals(
          tenantB,
          scenarioTenant(id),
          "the scenario created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }

    @Test
    @DisplayName(
        "no selector, user of B and the default tenant: characterisation, the request is refused")
    void noSelectorIsRefusedForAMultiTenantUser() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // Empty selector resolves to the caller's full authorized set (B + default); createScenario
      // has no @RequireTenantSelector, so fallbackSelector never runs and the write-scope resolver
      // rejects the 2-tenant scope for the dashboard lookup (400), rather than defaulting.
      mvc.perform(
              post("/api/scenarios")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(scenarioBody(null))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // endregion

  // region exercises (ExerciseApi.createExercise, POST {/api/exercises |
  // /api/tenants/{id}/exercises})

  @Nested
  @DisplayName("POST /api/exercises")
  class Exercises {

    @Test
    @DisplayName("prefixed route: the exercise is attributed to the path tenant (control)")
    void exercisePrefixedRouteAttributesToPathTenant() throws Exception {
      String id = postExercise(post("/api/tenants/{t}/exercises", tenantB).with(csrf()));
      assertEquals(
          tenantB,
          exerciseTenant(id),
          "the exercise created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the exercise must be attributed to X-Tenant-Ids, not to the default")
    void exerciseHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id = postExercise(post("/api/exercises").header("X-Tenant-Ids", tenantB).with(csrf()));
      assertEquals(
          tenantB,
          exerciseTenant(id),
          "the exercise created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region D1 audit query, seen to fire

  @Nested
  @DisplayName("D1 parent/child tenant-mismatch audit query")
  class D1Audit {

    @Test
    @DisplayName(
        "a scenario in the default tenant with a B-owned dashboard: D1 finds the tenant split")
    void d1FindsTheMisattributedScenario() {
      String dashboardId = seedCustomDashboard(tenantB, "t010-dash-b");
      // The fix stops the header route from producing this row; seed one directly so the audit
      // query is exercised against a row of the shape the pre-fix route already wrote (scenario
      // stamped with the default tenant while its dashboard belongs to B).
      String scenarioId = seedScenarioInDefaultTenant("t014-legacy-scenario", dashboardId);

      assertEquals(
          DEFAULT_TENANT, scenarioTenant(scenarioId), "precondition: the row is misattributed");
      assertNotEquals(
          scenarioTenant(scenarioId),
          dashboardTenant(dashboardId),
          "precondition: scenario and dashboard sit in different tenants");

      Object found =
          entityManager
              .createNativeQuery(
                  "SELECT s.scenario_id FROM scenarios s"
                      + " JOIN custom_dashboards d ON d.custom_dashboard_id = s.scenario_custom_dashboard"
                      + " WHERE s.tenant_id <> d.tenant_id AND s.scenario_id = ?1")
              .setParameter(1, scenarioId)
              .getSingleResult();
      assertEquals(
          scenarioId,
          found,
          "D1 must surface the scenario whose tenant differs from its dashboard's");
    }
  }

  // endregion

  // region organizations (OrganizationApi.createOrganization, POST {/api/organizations |
  // /api/tenants/{id}/organizations})

  @Nested
  @DisplayName("POST /api/organizations")
  class Organizations {

    @Test
    @DisplayName("prefixed route: the organization is attributed to the path tenant (control)")
    void organizationPrefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/organizations", tenantB).with(csrf()),
              "{\"organization_name\":\"t014-org\"}",
              "$.organization_id");
      assertEquals(
          tenantB,
          rowTenant("organizations", "organization_id", id),
          "the organization created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the organization must be attributed to X-Tenant-Ids, not to the default")
    void organizationHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postJson(
              post("/api/organizations").header("X-Tenant-Ids", tenantB).with(csrf()),
              "{\"organization_name\":\"t014-org\"}",
              "$.organization_id");
      assertEquals(
          tenantB,
          rowTenant("organizations", "organization_id", id),
          "the organization created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region teams (TeamApi.createTeam and TeamApi.upsertTeam, POST {/api/teams[/upsert] |
  // /api/tenants/{id}/teams[/upsert]})

  @Nested
  @DisplayName("POST /api/teams")
  class Teams {

    @Test
    @DisplayName("prefixed route: the team is attributed to the path tenant (control)")
    void teamPrefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/teams", tenantB).with(csrf()),
              "{\"team_name\":\"t014-team-" + UUID.randomUUID() + "\"}",
              "$.team_id");
      assertEquals(
          tenantB,
          rowTenant("teams", "team_id", id),
          "the team created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName("header route: the team must be attributed to X-Tenant-Ids, not to the default")
    void teamHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postJson(
              post("/api/teams").header("X-Tenant-Ids", tenantB).with(csrf()),
              "{\"team_name\":\"t014-team-" + UUID.randomUUID() + "\"}",
              "$.team_id");
      assertEquals(
          tenantB,
          rowTenant("teams", "team_id", id),
          "the team created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }

    @Test
    @DisplayName(
        "upsert header route (new team): the team must be attributed to X-Tenant-Ids, not to the default")
    void teamUpsertHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      // A name that no team carries, so upsertTeam takes its new-team branch rather than updating.
      String id =
          postJson(
              post("/api/teams/upsert").header("X-Tenant-Ids", tenantB).with(csrf()),
              "{\"team_name\":\"t014-team-upsert-" + UUID.randomUUID() + "\"}",
              "$.team_id");
      assertEquals(
          tenantB,
          rowTenant("teams", "team_id", id),
          "the upserted new team created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region reportings (ReportingApi.createReporting, POST {/api/reportings |
  // /api/tenants/{id}/reportings})

  @Nested
  @DisplayName("POST /api/reportings")
  class Reportings {

    @Test
    @DisplayName("prefixed route: the reporting is attributed to the path tenant (control)")
    void reportingPrefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/reportings", tenantB).with(csrf()),
              "{\"reporting_name\":\"t014-reporting\",\"reporting_context_type\":\"PLATFORM\"}",
              "$.reporting_id");
      assertEquals(
          tenantB,
          rowTenant("reportings", "reporting_id", id),
          "the reporting created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the reporting must be attributed to X-Tenant-Ids, not to the default")
    void reportingHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postJson(
              post("/api/reportings").header("X-Tenant-Ids", tenantB).with(csrf()),
              "{\"reporting_name\":\"t014-reporting\",\"reporting_context_type\":\"PLATFORM\"}",
              "$.reporting_id");
      assertEquals(
          tenantB,
          rowTenant("reportings", "reporting_id", id),
          "the reporting created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region documents (DocumentApi.uploadDocument, new-document branch, POST {/api/documents |
  // /api/tenants/{id}/documents})

  @Nested
  @DisplayName("POST /api/documents")
  class Documents {

    @Test
    @DisplayName("prefixed route: the document is attributed to the path tenant (control)")
    void documentPrefixedRouteAttributesToPathTenant() throws Exception {
      String id = uploadDocument(multipart("/api/tenants/{t}/documents", tenantB), null);
      assertEquals(
          tenantB,
          rowTenant("documents", "document_id", id),
          "the document uploaded under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the document must be attributed to X-Tenant-Ids, not to the default")
    void documentHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id = uploadDocument(multipart("/api/documents"), tenantB);
      assertEquals(
          tenantB,
          rowTenant("documents", "document_id", id),
          "the document uploaded with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region helpers

  private String postScenario(MockHttpServletRequestBuilder request, String dashboardId)
      throws Exception {
    String response =
        mvc.perform(
                request.contentType(MediaType.APPLICATION_JSON).content(scenarioBody(dashboardId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }

  private String postExercise(MockHttpServletRequestBuilder request) throws Exception {
    String response =
        mvc.perform(
                request
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"exercise_name\":\"t010-exercise\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.exercise_id");
  }

  private String postJson(MockHttpServletRequestBuilder request, String body, String idPath)
      throws Exception {
    String response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, idPath);
  }

  private String uploadDocument(MockMultipartHttpServletRequestBuilder request, String tenantHeader)
      throws Exception {
    if (tenantHeader != null) {
      request.header("X-Tenant-Ids", tenantHeader);
    }
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    // A unique payload per call so the content-hash lookup misses and the new-document branch runs,
    // rather than the dedup branch that reuses an existing row's tenant.
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file",
            "t014-" + UUID.randomUUID() + ".txt",
            MediaType.TEXT_PLAIN_VALUE,
            ("t014-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    String response =
        mvc.perform(request.part(inputPart).file(filePart).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.document_id");
  }

  private String rowTenant(String table, String idColumn, String id) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?1")
            .setParameter(1, id)
            .getSingleResult();
  }

  private String scenarioBody(String dashboardId) {
    if (dashboardId == null) {
      return "{\"scenario_name\":\"t010-scenario\"}";
    }
    return "{\"scenario_name\":\"t010-scenario\",\"scenario_custom_dashboard\":\""
        + dashboardId
        + "\"}";
  }

  private String seedCustomDashboard(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO custom_dashboards"
                + " (custom_dashboard_id, custom_dashboard_name, tenant_id,"
                + "  custom_dashboard_created_at, custom_dashboard_updated_at)"
                + " VALUES (?1, ?2, ?3, now(), now())")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, tenantId)
        .executeUpdate();
    return id;
  }

  private String seedScenarioInDefaultTenant(String name, String dashboardId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO scenarios"
                + " (scenario_id, scenario_name, scenario_mail_from, tenant_id,"
                + "  scenario_custom_dashboard)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "planner@openaev.io")
        .setParameter(4, DEFAULT_TENANT)
        .setParameter(5, dashboardId)
        .executeUpdate();
    return id;
  }

  private String scenarioTenant(String scenarioId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM scenarios WHERE scenario_id = ?1")
            .setParameter(1, scenarioId)
            .getSingleResult();
  }

  private String exerciseTenant(String exerciseId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM exercises WHERE exercise_id = ?1")
            .setParameter(1, exerciseId)
            .getSingleResult();
  }

  private String dashboardTenant(String dashboardId) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT tenant_id FROM custom_dashboards WHERE custom_dashboard_id = ?1")
            .setParameter(1, dashboardId)
            .getSingleResult();
  }

  // endregion
}
