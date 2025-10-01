package io.openaev.api.custom_dashboard;

import static io.openaev.database.model.CustomDashboardParameters.CustomDashboardParameterType.simulation;
import static io.openaev.engine.api.WidgetType.VERTICAL_BAR_CHART;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.fixtures.CustomDashboardFixture.NAME;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.CustomDashboardParameterFixture.createSimulationCustomDashboardParameter;
import static io.openaev.utils.fixtures.WidgetFixture.createDefaultWidget;
import static io.openaev.utilstest.ZipUtils.convertToJson;
import static io.openaev.utilstest.ZipUtils.extractAllFilesFromZip;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.CustomDashboardParameterComposer;
import io.openaev.utils.fixtures.composers.WidgetComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
            .perform(get(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId() + "/export"))
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
}
