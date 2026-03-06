package io.openaev.rest.custom_dashboard;

import static io.openaev.database.model.SettingKeys.*;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.NAME;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CustomDashboardApiTest extends IntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CustomDashboardRepository repository;
  @Autowired private CustomDashboardComposer customDashboardComposer;
  @Autowired private SettingRepository settingRepository;

  CustomDashboardComposer.Composer createCustomDashboardComposer() {
    return this.customDashboardComposer
        .forCustomDashboard(createDefaultCustomDashboard())
        .persist();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_valid_dashboard_input_when_creating_dashboard_should_return_created_dashboard()
      throws Exception {
    // -- PREPARE --
    CustomDashboardInput input = new CustomDashboardInput();
    String name = "New Dashboard";
    input.setName(name);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            post(CUSTOM_DASHBOARDS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(name));

    assertThat(repository.findByName(name)).isPresent();
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_dashboards_should_return_all_dashboards() throws Exception {
    // -- PREPARE --
    createCustomDashboardComposer();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(get(CUSTOM_DASHBOARDS_URI))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].custom_dashboard_name").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_dashboard_id_when_fetching_dashboard_should_return_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(get(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true)
  void given_updated_dashboard_input_when_updating_dashboard_should_return_updated_dashboard()
      throws Exception {
    // -- PREPARE --
    CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();
    CustomDashboard customDashboard = wrapper.get();
    String customDashboardDescription = "description";
    customDashboard.setDescription(customDashboardDescription);

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(
            put(CUSTOM_DASHBOARDS_URI + "/" + customDashboard.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(customDashboard)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(NAME))
        .andExpect(jsonPath("$.custom_dashboard_description").value(customDashboardDescription));

    Optional<CustomDashboard> updatedDashboard = repository.findById(customDashboard.getId());
    assertThat(updatedDashboard).isPresent();
    assertThat(updatedDashboard.get().getDescription()).isEqualTo(customDashboardDescription);
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Deleting Custom Dashboard")
  class Delete {
    @Test
    void given_dashboard_id_when_deleting_dashboard_should_return_no_content() throws Exception {
      // -- PREPARE --
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      // -- EXECUTE & ASSERT --
      mockMvc
          .perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()))
          .andExpect(status().isNoContent());

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
    }

    @Test
    void given_default_home_dashboard_id_when_deleting_should_throw_error() throws Exception {
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      Setting defaultDashboardSetting =
          settingRepository
              .findByKey(DEFAULT_HOME_DASHBOARD.key())
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(DEFAULT_HOME_DASHBOARD.key());
                    return setting;
                  });
      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);

      mockMvc
          .perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()))
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> {
                String errorMessage = result.getResolvedException().getMessage();
                assertTrue(
                    errorMessage.contains("Default home custom dashboard can not be deleted"));
              });
    }

    @Test
    void given_default_scenario_dashboard_id_when_deleting_should_reset_settings()
        throws Exception {
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      Setting defaultDashboardSetting =
          settingRepository
              .findByKey(DEFAULT_SCENARIO_DASHBOARD.key())
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(DEFAULT_SCENARIO_DASHBOARD.key());
                    return setting;
                  });

      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);
      mockMvc.perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()));

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
      Setting defaultScenarioDashboardSetting =
          settingRepository.findByKey(DEFAULT_SCENARIO_DASHBOARD.key()).orElseThrow();
      assertThat(defaultScenarioDashboardSetting.getValue()).isEmpty();
    }

    @Test
    void given_default_simulation_dashboard_id_when_deleting_should_reset_settings()
        throws Exception {
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      Setting defaultDashboardSetting =
          settingRepository
              .findByKey(DEFAULT_SIMULATION_DASHBOARD.key())
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(DEFAULT_SIMULATION_DASHBOARD.key());
                    return setting;
                  });

      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);
      mockMvc.perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()));

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
      Setting defaultScenarioDashboardSetting =
          settingRepository.findByKey(DEFAULT_SIMULATION_DASHBOARD.key()).orElseThrow();
      assertThat(defaultScenarioDashboardSetting.getValue()).isEmpty();
    }
  }
}
