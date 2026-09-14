package io.openaev.rest.tenancy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Document;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.FileService;
import io.openaev.service.MinioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * A create handler that receives a {@code TxCtx} attributes the new row from the request's write
 * scope ({@code TenantWriteScopeResolver.tenantForWrite}), set on the entity, so the row lands in
 * the same tenant on the tenant-prefixed route ({@code /api/tenants/{id}/...}) and on the
 * non-prefixed route carrying {@code X-Tenant-Ids}. This class pins that rule per site on both
 * routes.
 *
 * <p>Ground truth is read with a native query. This class arms no table (no
 * {@code @TestPropertySource} overriding {@code openaev.tenant.active-tables}), so the statement
 * inspector is inert and rewrites nothing: a native {@code SELECT tenant_id} reads the row's real
 * value regardless of the scope in effect, on every table it touches (including {@code
 * custom_dashboards}, which is armed only where a test explicitly overrides the list). The test
 * transaction rolls back, so nothing it writes survives the method.
 *
 * <p>The create handlers carry {@code @RequireTenantSelector}, so a request with no selector still
 * resolves a single-tenant write scope for a single-tenant caller and for a multi-tenant caller
 * with access to the default tenant (which falls back to it), keeping tenant-unaware clients
 * working (#6331, #6332); only a genuinely ambiguous request (several ids in {@code X-Tenant-Ids},
 * or a multi-tenant caller without access to the default tenant) is refused with 400.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Write attribution and selector fallback on the prefixed and header routes")
class WriteAttributionRouteTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractFixture injectorContractFixture;

  // Chaining create handlers are Enterprise-Edition gated; the license check is stubbed active.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  // Spied (real behaviour preserved) to assert the object upload is not reached on a refused scope.
  @MockitoSpyBean private FileService fileService;

  @Autowired private MinioService minioService;

  private String tenantB;

  // (tenantId, objectTarget) of every object an upload writes to real MinIO, so teardown can remove
  // them: uploads are a real side effect and are not rolled back with the test transaction.
  private final List<String[]> uploadedObjects = new ArrayList<>();

  @BeforeEach
  void seedTenantB() throws Exception {
    tenantB = tenantHelper.createTenantWithCurrentUser("t010-b").getId();
    when(enterpriseEditionService.isEnterpriseLicenseInactive(any())).thenReturn(false);
  }

  @AfterEach
  void clearContext() {
    // Remove the objects each test uploaded to real MinIO so repeated or local runs leave no orphan
    // under a tenant prefix; the test transaction only rolls back the database rows.
    for (String[] object : uploadedObjects) {
      try {
        minioService.deleteFileForTenant(object[0], object[1]);
      } catch (Exception e) {
        // best-effort cleanup: a missing object must not fail teardown
      }
    }
    uploadedObjects.clear();
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
        "no selector, caller of B and the default tenant: attributed to the default tenant (fallback)")
    void noSelectorMultiTenantWithDefaultAccessAttributesToDefault() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // Empty selector, createScenario is @RequireTenantSelector: a multi-tenant caller with access
      // to the default tenant falls back to it, so tenant-unaware clients keep working (#6331,
      // #6332).
      String id = postScenario(post("/api/scenarios").with(csrf()), null);
      assertEquals(
          DEFAULT_TENANT,
          scenarioTenant(id),
          "a tenant-unaware create by a caller of the default tenant lands in the default tenant");
    }

    @Test
    @DisplayName("several ids in X-Tenant-Ids: the create is refused (ambiguous write scope)")
    void severalHeaderIdsAreRefused() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // A non-empty selector never triggers the fallback; two tenants cannot attribute one row.
      mvc.perform(
              post("/api/scenarios")
                  .header("X-Tenant-Ids", tenantB + "," + DEFAULT_TENANT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(scenarioBody(null))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "no selector, caller of two non-default tenants: the create is refused (no safe fallback)")
    void noSelectorMultiTenantWithoutDefaultAccessIsRefused() throws Exception {
      // The caller belongs to B and a second non-default tenant, with no access to the default
      // tenant, so the fallback has no single tenant to pick and the request is refused.
      tenantHelper.createTenantWithCurrentUser("t014b-c");
      mvc.perform(
              post("/api/scenarios")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(scenarioBody(null))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // endregion

  // region scenarios with injector contracts
  // (ScenarioApi.createScenarioWithInjectorContracts, POST
  // {/api/scenarios/with-injector-contracts | /api/tenants/{id}/scenarios/with-injector-contracts})

  @Nested
  @DisplayName("POST /api/scenarios/with-injector-contracts")
  class ScenariosWithInjectorContracts {

    @Test
    @DisplayName("prefixed route: the scenario is attributed to the path tenant (control)")
    void prefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          postScenarioWithContracts(
              post("/api/tenants/{t}/scenarios/with-injector-contracts", tenantB).with(csrf()));
      assertEquals(
          tenantB,
          scenarioTenant(id),
          "the scenario created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the scenario must be attributed to X-Tenant-Ids, not to the default")
    void headerRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postScenarioWithContracts(
              post("/api/scenarios/with-injector-contracts")
                  .header("X-Tenant-Ids", tenantB)
                  .with(csrf()));
      assertEquals(
          tenantB,
          scenarioTenant(id),
          "the scenario created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }

    @Test
    @DisplayName(
        "prefixed route: the generated injects are attributed to the path tenant (control)")
    void prefixedRouteInjectsAttributedToPathTenant() throws Exception {
      String contractId = injectorContractFixture.getWellKnownSingleEmailContract().getId();
      String id =
          postScenarioSelectingContract(
              post("/api/tenants/{t}/scenarios/with-injector-contracts", tenantB).with(csrf()),
              contractId);
      assertEquals(1, injectCount(id), "the selected contract must have produced one inject");
      assertEquals(
          tenantB,
          injectScenarioTenant(id),
          "the injects generated under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the generated injects must be attributed to X-Tenant-Ids, not to the default")
    void headerRouteInjectsMustAttributeToHeaderTenant() throws Exception {
      String contractId = injectorContractFixture.getWellKnownSingleEmailContract().getId();
      String id =
          postScenarioSelectingContract(
              post("/api/scenarios/with-injector-contracts")
                  .header("X-Tenant-Ids", tenantB)
                  .with(csrf()),
              contractId);
      assertEquals(1, injectCount(id), "the selected contract must have produced one inject");
      assertEquals(
          tenantB,
          injectScenarioTenant(id),
          "the injects generated with X-Tenant-Ids: B must belong to B, not the default tenant");
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

    @Test
    @DisplayName(
        "no selector, caller of B and the default tenant: the team is attributed to the default tenant")
    void teamNoSelectorMultiTenantWithDefaultAccessAttributesToDefault() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String id =
          postJson(
              post("/api/teams").with(csrf()),
              "{\"team_name\":\"t014b-team-" + UUID.randomUUID() + "\"}",
              "$.team_id");
      assertEquals(
          DEFAULT_TENANT,
          rowTenant("teams", "team_id", id),
          "a tenant-unaware team create by a caller of the default tenant lands in the default tenant");
    }

    @Test
    @DisplayName("several ids in X-Tenant-Ids: the team create is refused (ambiguous write scope)")
    void teamSeveralHeaderIdsAreRefused() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      mvc.perform(
              post("/api/teams")
                  .header("X-Tenant-Ids", tenantB + "," + DEFAULT_TENANT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"team_name\":\"t014b-team-" + UUID.randomUUID() + "\"}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "no selector, caller of two non-default tenants: the team create is refused (no safe fallback)")
    void teamNoSelectorMultiTenantWithoutDefaultAccessIsRefused() throws Exception {
      tenantHelper.createTenantWithCurrentUser("t014b-team-c");
      mvc.perform(
              post("/api/teams")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"team_name\":\"t014b-team-" + UUID.randomUUID() + "\"}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "header route: a same-named team in the default tenant does not block a create for B")
    void teamHeaderRouteCreateNotBlockedByDefaultTenantDuplicate() throws Exception {
      String name = "t014f-team-" + UUID.randomUUID();
      // A team with this name already exists in the default tenant (seeded directly so the create
      // below is the only scoped write in this transaction).
      seedTeam(name, DEFAULT_TENANT);
      // The duplicate check must be scoped to the write tenant (B), not the thread-local default,
      // so the create for B succeeds and is attributed to B.
      String id =
          postJson(
              post("/api/teams").header("X-Tenant-Ids", tenantB).with(csrf()),
              "{\"team_name\":\"" + name + "\"}",
              "$.team_id");
      assertEquals(
          tenantB,
          rowTenant("teams", "team_id", id),
          "a team with the same name in the default tenant must not block a create for B");
    }

    @Test
    @DisplayName(
        "upsert several ids with a contextual multi-exercise input: refused with 400, not 500")
    void teamUpsertAmbiguousScopeIsRefusedWithBadRequestNotServerError() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      // Two ids in X-Tenant-Ids is an ambiguous write scope. The body is also a contextual team
      // with
      // more than one exercise, whose guard throws an unmapped 500. The write-scope refusal must be
      // resolved first, so the request returns the documented 400 rather than that 500.
      mvc.perform(
              post("/api/teams/upsert")
                  .header("X-Tenant-Ids", tenantB + "," + DEFAULT_TENANT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"team_name\":\"t014i-team-"
                          + UUID.randomUUID()
                          + "\",\"team_contextual\":true,"
                          + "\"team_exercises\":[\"exercise-a\",\"exercise-b\"]}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("header route: a same-named team in B still blocks a create for B (near miss)")
    void teamHeaderRouteCreateBlockedBySameTenantDuplicate() throws Exception {
      String name = "t014f-team-" + UUID.randomUUID();
      // A team with this name already exists in B; a second create for B must be refused.
      seedTeam(name, tenantB);
      mvc.perform(
              post("/api/teams")
                  .header("X-Tenant-Ids", tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"team_name\":\"" + name + "\"}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
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

    @Test
    @DisplayName("upsert prefixed route (new document): attributed to the path tenant (control)")
    void documentUpsertPrefixedRouteAttributesToPathTenant() throws Exception {
      String id = uploadDocument(multipart("/api/tenants/{t}/documents/upsert", tenantB), null);
      assertEquals(
          tenantB,
          rowTenant("documents", "document_id", id),
          "the document upserted under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "upsert header route (new document): attributed to X-Tenant-Ids, not to the default")
    void documentUpsertHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id = uploadDocument(multipart("/api/documents/upsert"), tenantB);
      assertEquals(
          tenantB,
          rowTenant("documents", "document_id", id),
          "the new document upserted with X-Tenant-Ids: B must belong to B, not the default tenant");
    }

    @Test
    @DisplayName(
        "refused write scope: the object is not uploaded before the tenant is resolved (no orphan)")
    void documentRefusedScopeDoesNotUploadObject() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
      inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      MockMultipartFile filePart =
          new MockMultipartFile(
              "file",
              "refused-scope-" + UUID.randomUUID() + ".txt",
              MediaType.TEXT_PLAIN_VALUE,
              ("refused-scope-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
      // Two ids in X-Tenant-Ids is an ambiguous write scope: tenantForWrite refuses it with 400
      // from inside the handler, after argument resolution has succeeded. The upload must not have
      // run before that refusal, or a rolled-back transaction leaves an orphan object in storage.
      mvc.perform(
              multipart("/api/documents")
                  .part(inputPart)
                  .file(filePart)
                  .header("X-Tenant-Ids", tenantB + "," + DEFAULT_TENANT)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      // The new-document branch now uploads under the resolved write tenant (the three-argument
      // overload), so a refused scope must reach neither the ambient nor the explicit-tenant
      // upload.
      verify(fileService, never()).uploadFile(anyString(), any(MultipartFile.class));
      verify(fileService, never()).uploadFile(anyString(), anyString(), any(MultipartFile.class));
    }

    @Test
    @DisplayName(
        "header route: the uploaded object is retrievable through the prefixed B download route")
    void documentHeaderRouteObjectIsRetrievableFromPrefixedRoute() throws Exception {
      byte[] content = ("t014i-body-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String id = uploadDocumentWithContent(multipart("/api/documents"), tenantB, content);

      // The bytes were written under B on the header route; a later download on B's prefixed route
      // must find them. Before the fix the object landed under the default tenant's path and this
      // read returned 404.
      byte[] downloaded =
          mvc.perform(get("/api/tenants/{t}/documents/{id}/file", tenantB, id))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();
      assertArrayEquals(
          content,
          downloaded,
          "the document uploaded for B on the header route must be downloadable from B's path");
    }

    @Test
    @DisplayName(
        "download by id: a caller scoped to another tenant is refused a B document's bytes (both"
            + " routes)")
    void documentDownloadByIdIsRefusedForACallerScopedToAnotherTenant() throws Exception {
      // Seed the B document out of band (no controller call, so the test transaction's tenant scope
      // is set for the first time by the download below, on tenant A). The row is loaded by id,
      // which
      // is exempt from the tenant filter, so before the guard a caller scoped to A received B's
      // bytes; now the row's tenant is outside the request scope and the read is a 404.
      byte[] content = ("t014j-xtenant-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = UUID.randomUUID() + ".txt";
      String id = seedDocumentInTenant(tenantB, target, content);
      String tenantA = tenantHelper.createTenantWithCurrentUser("t014j-a").getId();

      mvc.perform(get("/api/tenants/{t}/documents/{id}/file", tenantA, id))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/documents/{id}/file", id).header("X-Tenant-Ids", tenantA))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("download by id: the owning tenant still receives its bytes (both routes)")
    void documentDownloadByIdSucceedsForTheOwningTenant() throws Exception {
      byte[] content = ("t014j-owner-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = UUID.randomUUID() + ".txt";
      String id = seedDocumentInTenant(tenantB, target, content);

      assertArrayEquals(
          content,
          mvc.perform(get("/api/tenants/{t}/documents/{id}/file", tenantB, id))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "tenant B must download its own document on the prefixed route");
      assertArrayEquals(
          content,
          mvc.perform(get("/api/documents/{id}/file", id).header("X-Tenant-Ids", tenantB))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray(),
          "tenant B must download its own document on the header route");
    }

    @Test
    @DisplayName(
        "delete on the header route: the B row and its object are both removed, not silently"
            + " dropped")
    void documentDeleteRemovesRowAndObjectForTheOwningTenant() throws Exception {
      byte[] content = ("t014j-del-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String id = uploadDocumentWithContent(multipart("/api/documents"), tenantB, content);
      String target = documentTarget(id);
      assertEquals(
          1,
          minioService.countObjects(tenantB + "/" + target),
          "the uploaded object must exist under tenant B before the delete");

      mvc.perform(delete("/api/documents/{id}", id).header("X-Tenant-Ids", tenantB).with(csrf()))
          .andExpect(status().isOk());

      // The row is removed by primary key (not a tenant-filtered derived delete, which matched no B
      // row on the header route and left both the row and its object behind), and the object is
      // removed under B's own prefix rather than the ambient default.
      assertEquals(
          0, documentRowCount(id), "the B document row must be removed on the header route");
      assertEquals(
          0,
          minioService.countObjects(tenantB + "/" + target),
          "deleting the B document must remove its object from B's prefix");
    }

    @Test
    @DisplayName(
        "delete by id: a caller scoped to another tenant leaves the B row and object intact")
    void documentDeleteIsRefusedForACallerScopedToAnotherTenant() throws Exception {
      byte[] content = ("t014j-del-xtenant-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
      String target = UUID.randomUUID() + ".txt";
      String id = seedDocumentInTenant(tenantB, target, content);
      String tenantA = tenantHelper.createTenantWithCurrentUser("t014j-del-a").getId();

      mvc.perform(delete("/api/documents/{id}", id).header("X-Tenant-Ids", tenantA).with(csrf()))
          .andExpect(status().isNotFound());

      // The unfiltered delete would otherwise remove any document by id: the request-scope guard
      // keeps a caller from deleting another tenant's row or object.
      assertEquals(
          1, documentRowCount(id), "the B document row must survive a cross-tenant delete");
      assertEquals(
          1,
          minioService.countObjects(tenantB + "/" + target),
          "the B object must survive a cross-tenant delete");
    }
  }

  // endregion

  // region attack patterns (AttackPatternApi.createAttackPattern, POST {/api/attack_patterns |
  // /api/tenants/{id}/attack_patterns})

  @Nested
  @DisplayName("POST /api/attack_patterns")
  class AttackPatterns {

    @Test
    @DisplayName("prefixed route: the attack pattern is attributed to the path tenant (control)")
    void attackPatternPrefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/attack_patterns", tenantB).with(csrf()),
              attackPatternBody(),
              "$.attack_pattern_id");
      assertEquals(
          tenantB,
          rowTenant("attack_patterns", "attack_pattern_id", id),
          "the attack pattern created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the attack pattern must be attributed to X-Tenant-Ids, not to the default")
    void attackPatternHeaderRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          postJson(
              post("/api/attack_patterns").header("X-Tenant-Ids", tenantB).with(csrf()),
              attackPatternBody(),
              "$.attack_pattern_id");
      assertEquals(
          tenantB,
          rowTenant("attack_patterns", "attack_pattern_id", id),
          "the attack pattern created with X-Tenant-Ids: B must belong to B, not the default tenant");
    }

    @Test
    @DisplayName(
        "no selector, caller of B and the default tenant: attributed to the default tenant (fallback)")
    void attackPatternNoSelectorMultiTenantWithDefaultAccessAttributesToDefault() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String id =
          postJson(
              post("/api/attack_patterns").with(csrf()),
              attackPatternBody(),
              "$.attack_pattern_id");
      assertEquals(
          DEFAULT_TENANT,
          rowTenant("attack_patterns", "attack_pattern_id", id),
          "a tenant-unaware attack pattern create by a caller of the default tenant lands in the default tenant");
    }

    @Test
    @DisplayName(
        "several ids in X-Tenant-Ids: the attack pattern create is refused (ambiguous write scope)")
    void attackPatternSeveralHeaderIdsAreRefused() throws Exception {
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      mvc.perform(
              post("/api/attack_patterns")
                  .header("X-Tenant-Ids", tenantB + "," + DEFAULT_TENANT)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(attackPatternBody())
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "no selector, caller of two non-default tenants: the attack pattern create is refused (no safe fallback)")
    void attackPatternNoSelectorMultiTenantWithoutDefaultAccessIsRefused() throws Exception {
      tenantHelper.createTenantWithCurrentUser("t014e-ap-c");
      mvc.perform(
              post("/api/attack_patterns")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(attackPatternBody())
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  // endregion

  // region attack patterns upsert (AttackPatternApi.upsertAttackPatterns, POST
  // {/api/attack_patterns/upsert | /api/tenants/{id}/attack_patterns/upsert})

  @Nested
  @DisplayName("POST /api/attack_patterns/upsert")
  class AttackPatternsUpsert {

    @Test
    @DisplayName("prefixed route: the created attack pattern is attributed to the path tenant")
    void prefixedRouteAttributesToPathTenant() throws Exception {
      String id =
          upsertAttackPattern(
              post("/api/tenants/{t}/attack_patterns/upsert", tenantB).with(csrf()));
      assertEquals(
          tenantB,
          rowTenant("attack_patterns", "attack_pattern_id", id),
          "the attack pattern upserted under tenant B's path must belong to B");
    }

    @Test
    @DisplayName(
        "header route: the created attack pattern must be attributed to X-Tenant-Ids, not to the default")
    void headerRouteMustAttributeToHeaderTenant() throws Exception {
      String id =
          upsertAttackPattern(
              post("/api/attack_patterns/upsert").header("X-Tenant-Ids", tenantB).with(csrf()));
      assertEquals(
          tenantB,
          rowTenant("attack_patterns", "attack_pattern_id", id),
          "the attack pattern upserted with X-Tenant-Ids: B must belong to B, not the default tenant");
    }
  }

  // endregion

  // region chaining (ChainingApi.createSimulation and createScenarioChaining, POST
  // /api/tenants/{id}/chaining/{simulations|scenarios}; Enterprise-Edition gated, prefixed route
  // only)

  @Nested
  @DisplayName("POST /api/tenants/{id}/chaining/...")
  class Chaining {

    @Test
    @DisplayName("simulation prefixed route: attributed to the path tenant")
    void simulationChainingAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/chaining/simulations", tenantB).with(csrf()),
              "{\"exercise_name\":\"t014f-chaining-sim\"}",
              "$.exercise_id");
      assertEquals(
          tenantB,
          exerciseTenant(id),
          "the chaining simulation created under tenant B's path must belong to B");
    }

    @Test
    @DisplayName("scenario prefixed route: attributed to the path tenant")
    void scenarioChainingAttributesToPathTenant() throws Exception {
      String id =
          postJson(
              post("/api/tenants/{t}/chaining/scenarios", tenantB).with(csrf()),
              "{\"scenario_name\":\"t014f-chaining-scn\"}",
              "$.scenario_id");
      assertEquals(
          tenantB,
          scenarioTenant(id),
          "the chaining scenario created under tenant B's path must belong to B");
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

  private String postScenarioWithContracts(MockHttpServletRequestBuilder request) throws Exception {
    // An empty search selects no injector contracts, so no injects are created: the endpoint still
    // creates and attributes the scenario, which is all this route-attribution test asserts.
    String body =
        "{\"locale\":\"en\",\"scenario_input\":{\"scenario_name\":\"t014h-wic\"},"
            + "\"injector_contract_search_pagination_input\":{}}";
    String response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }

  private String postScenarioSelectingContract(
      MockHttpServletRequestBuilder request, String injectorContractId) throws Exception {
    // A non-empty selection (the well-known email contract, resolved by JPA id, no Elastic search)
    // makes the endpoint generate one child inject, so the child-attribution path is exercised.
    String body =
        "{\"locale\":\"en\",\"scenario_input\":{\"scenario_name\":\"t014i-wic\"},"
            + "\"injector_contract_search_pagination_input\":{\"include_full_details\":true,"
            + "\"injector_contract_ids_to_process\":[\""
            + injectorContractId
            + "\"]}}";
    String response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.scenario_id");
  }

  private int injectCount(String scenarioId) {
    entityManager.flush();
    Number count =
        (Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM injects WHERE inject_scenario = ?1")
                .setParameter(1, scenarioId)
                .getSingleResult();
    return count.intValue();
  }

  private String injectScenarioTenant(String scenarioId) {
    entityManager.flush();
    // One distinct tenant across every inject of the scenario: a split would surface as more than
    // one row here (getSingleResult throws) or as the wrong value.
    return (String)
        entityManager
            .createNativeQuery("SELECT DISTINCT tenant_id FROM injects WHERE inject_scenario = ?1")
            .setParameter(1, scenarioId)
            .getSingleResult();
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
    // A unique payload per call so the content-hash lookup misses and the new-document branch runs,
    // rather than the dedup branch that reuses an existing row's tenant.
    return uploadDocumentWithContent(
        request, tenantHeader, ("t014-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
  }

  private String uploadDocumentWithContent(
      MockMultipartHttpServletRequestBuilder request, String tenantHeader, byte[] content)
      throws Exception {
    if (tenantHeader != null) {
      request.header("X-Tenant-Ids", tenantHeader);
    }
    MockPart inputPart = new MockPart("input", "{}".getBytes(StandardCharsets.UTF_8));
    inputPart.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    MockMultipartFile filePart =
        new MockMultipartFile(
            "file", "t014-" + UUID.randomUUID() + ".txt", MediaType.TEXT_PLAIN_VALUE, content);
    String response =
        mvc.perform(request.part(inputPart).file(filePart).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = JsonPath.read(response, "$.document_id");
    trackUploadedObject(id);
    return id;
  }

  /** Records the (tenant, target) of a just-uploaded document so teardown removes its object. */
  private void trackUploadedObject(String documentId) {
    uploadedObjects.add(
        new String[] {
          rowTenant("documents", "document_id", documentId), documentTarget(documentId)
        });
  }

  private String documentTarget(String documentId) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT document_target FROM documents WHERE document_id = ?1")
            .setParameter(1, documentId)
            .getSingleResult();
  }

  private long documentRowCount(String documentId) {
    entityManager.flush();
    return ((Number)
            entityManager
                .createNativeQuery("SELECT count(*) FROM documents WHERE document_id = ?1")
                .setParameter(1, documentId)
                .getSingleResult())
        .longValue();
  }

  /**
   * Seeds a document owned by {@code tenantId} without going through a controller, so the test
   * transaction's tenant scope is left unset for the request under test to define. The object is
   * written under the tenant's own prefix and the row carries that tenant explicitly, matching what
   * an upload attributed to that tenant produces.
   */
  private String seedDocumentInTenant(String tenantId, String target, byte[] content)
      throws Exception {
    minioService.uploadFileForTenant(
        tenantId,
        target,
        new ByteArrayInputStream(content),
        content.length,
        MediaType.TEXT_PLAIN_VALUE);
    uploadedObjects.add(new String[] {tenantId, target});
    Document document = new Document();
    document.setName("t014j-seed-" + UUID.randomUUID() + ".txt");
    document.setTarget(target);
    document.setType(MediaType.TEXT_PLAIN_VALUE);
    document.setTenant(new Tenant(tenantId));
    entityManager.persist(document);
    entityManager.flush();
    return document.getId();
  }

  private String upsertAttackPattern(MockHttpServletRequestBuilder request) throws Exception {
    // A unique external id so the upsert dedup lookup misses and a brand-new attack pattern is
    // created, which is the write-attribution path under test.
    String suffix = UUID.randomUUID().toString();
    String body =
        "{\"attack_patterns\":[{\"attack_pattern_name\":\"t014h-ap-"
            + suffix
            + "\",\"attack_pattern_external_id\":\"T-"
            + suffix
            + "\",\"attack_pattern_stix_id\":\"attack-pattern--"
            + suffix
            + "\"}],\"ignore_dependencies\":false}";
    String response =
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$[0].attack_pattern_id");
  }

  private String rowTenant(String table, String idColumn, String id) {
    entityManager.flush();
    return (String)
        entityManager
            .createNativeQuery("SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?1")
            .setParameter(1, id)
            .getSingleResult();
  }

  private String attackPatternBody() {
    // Unique external id and stix id so the (external_id, tenant_id) and (stix_id, tenant_id)
    // unique
    // indexes never collide with a pre-seeded row or across the tenants a single test touches.
    String suffix = UUID.randomUUID().toString();
    return "{\"attack_pattern_name\":\"t014e-ap-"
        + suffix
        + "\",\"attack_pattern_external_id\":\"T-"
        + suffix
        + "\",\"attack_pattern_stix_id\":\"attack-pattern--"
        + suffix
        + "\"}";
  }

  private String scenarioBody(String dashboardId) {
    if (dashboardId == null) {
      return "{\"scenario_name\":\"t010-scenario\"}";
    }
    return "{\"scenario_name\":\"t010-scenario\",\"scenario_custom_dashboard\":\""
        + dashboardId
        + "\"}";
  }

  private void seedTeam(String name, String tenantId) {
    entityManager
        .createNativeQuery(
            "INSERT INTO teams"
                + " (team_id, team_name, tenant_id, team_contextual,"
                + "  team_created_at, team_updated_at)"
                + " VALUES (?1, ?2, ?3, false, now(), now())")
        .setParameter(1, UUID.randomUUID().toString())
        .setParameter(2, name)
        .setParameter(3, tenantId)
        .executeUpdate();
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
