package io.openaev.api.custom_dashboard;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.simulation;
import static io.openaev.engine.api.WidgetType.VERTICAL_BAR_CHART;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.TENANT_CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.NAME;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.CustomDashboardParameterFixture.createSimulationCustomDashboardParameter;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static io.openaev.utilstest.ZipUtils.convertToJson;
import static io.openaev.utilstest.ZipUtils.extractAllFilesFromZip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.CustomDashboardParameterComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Custom dashboard api exporter tests")
class CustomDashboardApiExporterTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private CustomDashboardParameterComposer customDashboardParameterComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  CustomDashboardComposer.Composer createCustomDashboardComposer() {
    CustomDashboardParameterComposer.Composer paramWrapper =
        customDashboardParameterComposer.forCustomDashboardParameter(
            createSimulationCustomDashboardParameter());
    WidgetComposer.Composer widgetWrapper = widgetComposer.forWidget(createDefaultWidget());
    return this.customDashboardComposer
        .forCustomDashboard(createDefaultCustomDashboard())
        .withCustomDashboardParameter(paramWrapper)
        .withWidget(widgetWrapper)
        .persist();
  }

  @Test
  @DisplayName("Export a custom dashboard returns entity")
  void export_custom_dashboard_with_include_returns_custom_dashboard_with_relationship()
      throws Exception {
    // -- PREPARE --
    CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

    // -- EXECUTE --
    byte[] response =
        mockMvc
            .perform(
                get(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId() + "/export").with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    // -- ASSERT --
    assertNotNull(response);
    Map<String, byte[]> files = extractAllFilesFromZip(response);
    Map<String, String> jsonFiles = convertToJson(files);

    // Custom dashboard
    String customDashboardString =
        jsonFiles.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("custom"))
            .map(Map.Entry::getValue)
            .findFirst()
            .get();
    JsonNode json = new ObjectMapper().readTree(customDashboardString);
    assertEquals("custom_dashboards", json.at("/data/type").asText());
    assertEquals(NAME, json.at("/data/attributes/custom_dashboard_name").asText());
    assertEquals(2, json.at("/data/relationships").size());

    // Params
    boolean hasSimulationParam =
        StreamSupport.stream(json.at("/included").spliterator(), false)
            .anyMatch(
                node ->
                    "custom_dashboards_parameters".equals(node.get("type").asText())
                        && simulation
                            .name()
                            .equals(
                                node.at("/attributes/custom_dashboards_parameter_type").asText()));

    assertTrue(hasSimulationParam);

    // Widget
    boolean hasVerticalBarChart =
        StreamSupport.stream(json.at("/included").spliterator(), false)
            .anyMatch(
                node ->
                    "widgets".equals(node.get("type").asText())
                        && VERTICAL_BAR_CHART.type.equals(
                            node.at("/attributes/widget_type").asText()));

    assertTrue(hasVerticalBarChart);
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @DisplayName("Custom dashboard created in tenant X should NOT be exportable from tenant Y")
    void given_customDashboardInTenantX_should_notBeExportableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_DASHBOARDS));

      // Seeded directly (native insert), not through the create endpoint: creating under tenant
      // X's path would set the tenant scope (TxCtx) to X on this test's wrapping transaction, and
      // the export call below sets it to Y - the aspect refuses a scope change within one
      // transaction (see TenantScopeTransactionAspect). Seeding bypasses that entirely.
      String dashboardId = UUID.randomUUID().toString();
      entityManager
          .createNativeQuery(
              "INSERT INTO custom_dashboards (custom_dashboard_id, custom_dashboard_name,"
                  + " tenant_id) VALUES (CAST(:id AS uuid), :name, CAST(:tenant AS uuid))")
          .setParameter("id", dashboardId)
          .setParameter("name", "Export Isolation Test Dashboard")
          .setParameter("tenant", tenantX.getId())
          .executeUpdate();

      entityManager.flush();
      entityManager.clear();

      // -------- Act — export from tenant Y (expect 404) --------
      int responseStatus =
          mockMvc
              .perform(
                  get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantY.getId())
                          + "/"
                          + dashboardId
                          + "/export")
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Custom dashboard created in tenant X should be exportable from tenant X")
    void given_customDashboardInTenantX_should_beExportableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_DASHBOARDS, Capability.ACCESS_DASHBOARDS));

      CustomDashboardInput input = new CustomDashboardInput();
      input.setName("Export Same Tenant Dashboard");

      String createResponse =
          mockMvc
              .perform(
                  post(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantX.getId()))
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String dashboardId = JsonPath.read(createResponse, "$.custom_dashboard_id");

      entityManager.flush();
      entityManager.clear();

      // -------- Act & Assert — export from same tenant should succeed --------
      byte[] response =
          mockMvc
              .perform(
                  get(TENANT_CUSTOM_DASHBOARDS_URI.replace("{tenantId}", tenantX.getId())
                          + "/"
                          + dashboardId
                          + "/export")
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsByteArray();

      assertThat(response).isNotEmpty();
    }
  }
}
