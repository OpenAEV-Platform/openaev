package io.openbas.rest.custom_dashboard;

import static io.openbas.engine.api.WidgetType.VERTICAL_BAR_CHART;
import static io.openbas.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openbas.utils.fixtures.WidgetFixture.NAME;
import static io.openbas.utils.fixtures.WidgetFixture.createDefaultWidget;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openbas.IntegrationTest;
import io.openbas.database.model.CustomDashboard;
import io.openbas.database.model.Widget;
import io.openbas.database.model.WidgetLayout;
import io.openbas.database.repository.WidgetRepository;
import io.openbas.engine.api.DateHistogramWidget;
import io.openbas.engine.api.HistogramInterval;
import io.openbas.rest.custom_dashboard.form.WidgetInput;
import io.openbas.utils.CustomDashboardTimeRange;
import io.openbas.utils.fixtures.composers.CustomDashboardComposer;
import io.openbas.utils.fixtures.composers.WidgetComposer;
import io.openbas.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CustomDashboardWidgetApiTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private WidgetRepository repository;
  @Autowired private WidgetComposer widgetComposer;
  @Autowired private CustomDashboardComposer customDashboardComposer;

  WidgetComposer.Composer createWidgetComposer() {
    return this.widgetComposer
        .forWidget(createDefaultWidget())
        .withCustomDashboard(
            customDashboardComposer.forCustomDashboard(createDefaultCustomDashboard()))
        .persist();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_valid_widget_input_when_creating_widget_should_return_created_widget()
      throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    WidgetInput input = new WidgetInput();
    input.setType(VERTICAL_BAR_CHART);
    String name = "My new widget";
    DateHistogramWidget widgetConfig = new DateHistogramWidget();
    widgetConfig.setTitle(name);
    widgetConfig.setDateAttribute("base_updated_at");
    widgetConfig.setTimeRange(CustomDashboardTimeRange.CUSTOM);
    widgetConfig.setSeries(new ArrayList<>());
    widgetConfig.setInterval(HistogramInterval.day);
    widgetConfig.setStart("2012-12-21T10:45:23Z");
    widgetConfig.setEnd("2012-12-22T10:45:23Z");
    input.setWidgetConfiguration(widgetConfig);
    WidgetLayout widgetLayout = new WidgetLayout();
    input.setWidgetLayout(widgetLayout);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            post(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId() + "/widgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(name));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widgets_should_return_all_widgets() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(get(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId() + "/widgets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].widget_config.title").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widget_id_when_fetching_widget_should_return_widget() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            get(
                CUSTOM_DASHBOARDS_URI
                    + "/"
                    + customDashboard.getId()
                    + "/widgets/"
                    + composer.get().getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_updated_widget_input_when_updating_widget_should_return_updated_widget()
      throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    Widget widget = composer.get();
    WidgetLayout widgetLayout = new WidgetLayout();
    widgetLayout.setX(10);
    widgetLayout.setY(10);
    widget.setLayout(widgetLayout);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(CUSTOM_DASHBOARDS_URI
                    + "/"
                    + customDashboard.getId()
                    + "/widgets/"
                    + widget.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(widget)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.widget_config.title").value(NAME))
        .andExpect(jsonPath("$.widget_layout.widget_layout_x").value(10));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_widget_id_when_deleting_widget_should_return_no_content() throws Exception {
    // -- PREPARE --
    WidgetComposer.Composer composer = createWidgetComposer();
    CustomDashboard customDashboard = composer.get().getCustomDashboard();
    Widget widget = composer.get();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            delete(
                CUSTOM_DASHBOARDS_URI
                    + "/"
                    + customDashboard.getId()
                    + "/widgets/"
                    + widget.getId()))
        .andExpect(status().isNoContent());

    assertThat(repository.existsById(widget.getId())).isFalse();
  }
}
