package io.openaev.rest.custom_dashboard;

import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createCustomDashboardWithDefaultParams;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Widget;
import io.openaev.jsonapi.JsonApiDocument;
import io.openaev.jsonapi.ResourceObject;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.service.ZipJsonService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=custom_dashboards,widgets")
@WithMockUser(isAdmin = true)
@DisplayName("custom_dashboards stay isolated through the real HTTP endpoints once v2 is active")
class CustomDashboardHttpIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private ZipJsonService<CustomDashboard> zipJsonService;

  private String tenantA;
  private String tenantB;
  private String dashboardA;
  private String dashboardB;

  @BeforeEach
  void seedTwoTenantsWithOneDashboardEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("cd-http-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("cd-http-iso-b").getId();
    dashboardA = seedDashboardWithWidget(tenantA, "dashboard-a");
    dashboardB = seedDashboardWithWidget(tenantB, "dashboard-b");
  }

  @Test
  @DisplayName("under tenant A's path: the dashboard keeps its widget list during JSON rendering")
  void readOwnDashboardKeepsWidgetsInitialized() throws Exception {
    String body =
        mvc.perform(
                get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA) + "/" + dashboardA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals("dashboard-a", JsonPath.read(body, "$.custom_dashboard_name"));
    assertEquals(1, ((List<?>) JsonPath.read(body, "$.custom_dashboard_widgets")).size());
  }

  @Test
  @DisplayName("under tenant A's path: tenant B's dashboard is not found")
  void readOtherTenantDashboardIsNotFound() throws Exception {
    mvc.perform(get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA) + "/" + dashboardB))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's dashboard and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    SearchPaginationInput input = PaginationFixture.simpleTextSearch("dashboard");
    String body =
        mvc.perform(
                post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA) + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(body.contains(dashboardA), "A's dashboard must appear in A's search results");
    assertFalse(body.contains(dashboardB), "B's dashboard must not appear in A's search results");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header: search returns A's dashboard and not B's")
  void searchViaHeaderReturnsOnlyA() throws Exception {
    String body =
        mvc.perform(
                post(CUSTOM_DASHBOARDS_URI + "/search")
                    .header("X-Tenant-Ids", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(
                            PaginationFixture.getDefault().textSearch("dashboard").build()))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(
        body.contains(dashboardA), "A's dashboard must appear when A is selected via header");
    assertFalse(body.contains(dashboardB), "B's dashboard must not appear");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    CustomDashboardInput input = new CustomDashboardInput();
    input.setName("created-under-a");

    String body =
        mvc.perform(
                post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = JsonPath.read(body, "$.custom_dashboard_id");
    entityManager.flush();
    assertEquals(
        tenantA,
        rawSingle("SELECT tenant_id FROM custom_dashboards WHERE custom_dashboard_id = ?", id));
  }

  @Test
  @DisplayName("a create with no tenant selector is refused under a multi-tenant scope")
  void createWithoutSelectorIsRejected() throws Exception {
    CustomDashboardInput input = new CustomDashboardInput();
    input.setName("no-selector-" + UUID.randomUUID());

    mvc.perform(
            post(CUSTOM_DASHBOARDS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("updating tenant B's dashboard from tenant A is blocked and leaves it untouched")
  void updateOtherTenantDashboardIsBlocked() throws Exception {
    CustomDashboardInput input = new CustomDashboardInput();
    input.setName("hijacked");

    mvc.perform(
            put(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA) + "/" + dashboardB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());

    assertEquals(
        "dashboard-b",
        rawSingle(
            "SELECT custom_dashboard_name FROM custom_dashboards WHERE custom_dashboard_id = ?",
            dashboardB));
  }

  @Test
  @DisplayName("an import under tenant A's path is attributed to tenant A")
  void importUnderTenantAIsAttributedToA() throws Exception {
    MockMultipartFile file = createImportZipFile("imported-dashboard");

    String body =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantA) + "/import")
                    .file(file)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = JsonPath.read(body, "$.data.id");
    entityManager.flush();
    assertEquals(
        tenantA,
        rawSingle("SELECT tenant_id FROM custom_dashboards WHERE custom_dashboard_id = ?", id));
  }

  private String seedDashboardWithWidget(String tenantId, String name) {
    CustomDashboard dashboard = createCustomDashboardWithDefaultParams();
    dashboard.setName(name);
    dashboard.setTenant(new Tenant(tenantId));

    Widget widget = createDefaultWidget();
    widget.setTenant(new Tenant(tenantId));

    CustomDashboard persisted =
        customDashboardComposer
            .forCustomDashboard(dashboard)
            .withWidget(widgetComposer.forWidget(widget))
            .persist()
            .get();
    // Detach the seeded aggregate so the endpoint's findById goes to the database instead of
    // being served from this transaction's persistence context: a first-level cache hit emits no
    // SQL, so TenantStatementInspector never sees the read and cannot scope it.
    entityManager.flush();
    entityManager.detach(persisted);
    return persisted.getId();
  }

  private MockMultipartFile createImportZipFile(String dashboardName) throws Exception {
    JsonApiDocument<ResourceObject> document =
        new JsonApiDocument<>(
            new ResourceObject(
                null,
                "custom_dashboards",
                Map.of("custom_dashboard_name", dashboardName),
                Map.of()),
            List.of());
    byte[] zip = zipJsonService.writeZip(document, Map.of());
    return new MockMultipartFile("file", "dashboard.zip", "application/zip", zip);
  }

  private String rawSingle(String sql, String id) {
    entityManager.flush();
    return (String)
        entityManager
            .unwrap(org.hibernate.Session.class)
            .doReturningWork(
                connection -> {
                  try (java.sql.PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, id);
                    try (java.sql.ResultSet rs = stmt.executeQuery()) {
                      rs.next();
                      return rs.getString(1);
                    }
                  }
                });
  }
}
