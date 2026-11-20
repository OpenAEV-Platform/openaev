package io.openaev.rest.custom_dashboard;

import static io.openaev.database.model.TenantSettingKeys.*;
import static io.openaev.rest.custom_dashboard.CustomDashboardApi.CUSTOM_DASHBOARDS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CustomDashboardFixture.NAME;
import static io.openaev.utils.fixtures.CustomDashboardFixture.createDefaultCustomDashboard;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultIncidentResponseScenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.rest.custom_dashboard.form.CustomDashboardInput;
import io.openaev.utils.fixtures.composers.CustomDashboardComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;

  // The starter pack the tests see: classpath:starterpack/dashboards/*.zip resolves to the test
  // fixtures (src/test/resources/starterpack/dashboards), named Test 1, Test 2 and Test 3.
  private static final List<String> STARTER_PACK_DASHBOARD_NAMES =
      List.of("Test 1", "Test 2", "Test 3");

  private void setTenantSetting(String key, String value) {
    String tenantId = TenantContext.getCurrentTenant();
    Setting setting =
        settingRepository
            .findByKeyAndTenantId(key, tenantId)
            .orElseGet(
                () -> {
                  Setting created = new Setting();
                  created.setKey(key);
                  created.setTenant(new Tenant(tenantId));
                  return created;
                });
    setting.setValue(value);
    settingRepository.save(setting);
  }

  private String tenantSettingValue(String key) {
    return settingRepository
        .findByKeyAndTenantId(key, TenantContext.getCurrentTenant())
        .map(Setting::getValue)
        .orElse("");
  }

  CustomDashboardComposer.Composer createCustomDashboardComposer() {
    return this.customDashboardComposer
        .forCustomDashboard(createDefaultCustomDashboard())
        .persist();
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
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
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(name));

    assertThat(repository.findByName(name)).isPresent();
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_dashboards_should_return_all_dashboards() throws Exception {
    // -- PREPARE --
    createCustomDashboardComposer();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(get(CUSTOM_DASHBOARDS_URI).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].custom_dashboard_name").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  void given_dashboard_id_when_fetching_dashboard_should_return_dashboard() throws Exception {
    // -- PREPARE --
    CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

    // -- EXECUTE & ASSERT --
    mockMvc
        .perform(get(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(NAME));
  }

  @Test
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
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
                .content(asJsonString(customDashboard))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.custom_dashboard_name").value(NAME))
        .andExpect(jsonPath("$.custom_dashboard_description").value(customDashboardDescription));

    Optional<CustomDashboard> updatedDashboard = repository.findById(customDashboard.getId());
    assertThat(updatedDashboard).isPresent();
    assertThat(updatedDashboard.get().getDescription()).isEqualTo(customDashboardDescription);
  }

  @Nested
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("Deleting Custom Dashboard")
  class Delete {
    @Test
    void given_dashboard_id_when_deleting_dashboard_should_return_no_content() throws Exception {
      // -- PREPARE --
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      // -- EXECUTE & ASSERT --
      mockMvc
          .perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()).with(csrf()))
          .andExpect(status().isNoContent());

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
    }

    @Test
    void given_default_home_dashboard_id_when_deleting_should_throw_error() throws Exception {
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      String tenantId = TenantContext.getCurrentTenant();
      Setting defaultDashboardSetting =
          settingRepository
              .findByKeyAndTenantId(TENANT_HOME_DASHBOARD.key(), tenantId)
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(TENANT_HOME_DASHBOARD.key());
                    setting.setTenant(new Tenant(tenantId));
                    return setting;
                  });
      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);

      mockMvc
          .perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()).with(csrf()))
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

      String tenantId = TenantContext.getCurrentTenant();
      Setting defaultDashboardSetting =
          settingRepository
              .findByKeyAndTenantId(TENANT_SCENARIO_DASHBOARD.key(), tenantId)
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(TENANT_SCENARIO_DASHBOARD.key());
                    setting.setTenant(new Tenant(tenantId));
                    return setting;
                  });

      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);
      mockMvc.perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()).with(csrf()));

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
      Setting defaultScenarioDashboardSetting =
          settingRepository
              .findByKeyAndTenantId(TENANT_SCENARIO_DASHBOARD.key(), tenantId)
              .orElseThrow();
      assertThat(defaultScenarioDashboardSetting.getValue()).isEmpty();
    }

    @Test
    void given_default_simulation_dashboard_id_when_deleting_should_reset_settings()
        throws Exception {
      CustomDashboardComposer.Composer wrapper = createCustomDashboardComposer();

      String tenantId = TenantContext.getCurrentTenant();
      Setting defaultDashboardSetting =
          settingRepository
              .findByKeyAndTenantId(TENANT_SIMULATION_DASHBOARD.key(), tenantId)
              .orElseGet(
                  () -> {
                    Setting setting = new Setting();
                    setting.setKey(TENANT_SIMULATION_DASHBOARD.key());
                    setting.setTenant(new Tenant(tenantId));
                    return setting;
                  });

      defaultDashboardSetting.setValue(wrapper.get().getId());
      settingRepository.save(defaultDashboardSetting);
      mockMvc.perform(delete(CUSTOM_DASHBOARDS_URI + "/" + wrapper.get().getId()).with(csrf()));

      assertThat(repository.existsById(wrapper.get().getId())).isFalse();
      Setting defaultSimulationDashboardSetting =
          settingRepository
              .findByKeyAndTenantId(TENANT_SIMULATION_DASHBOARD.key(), tenantId)
              .orElseThrow();
      assertThat(defaultSimulationDashboardSetting.getValue()).isEmpty();
    }
  }

  @Nested
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("Custom dashboard usage")
  class Usage {
    @Test
    void given_dashboard_used_by_scenarios_simulations_and_defaults_should_count_each()
        throws Exception {
      // -- PREPARE --
      CustomDashboard dashboard = createCustomDashboardComposer().get();
      Tenant tenant = new Tenant(TenantContext.getCurrentTenant());
      for (int i = 0; i < 2; i++) {
        Scenario scenario = createDefaultIncidentResponseScenario();
        scenario.setTenant(tenant);
        scenario.setCustomDashboard(dashboard);
        scenarioComposer.forScenario(scenario).persist();
      }
      Exercise exercise = createDefaultExercise();
      exercise.setTenant(tenant);
      exercise.setCustomDashboard(dashboard);
      exerciseComposer.forExercise(exercise).persist();
      setTenantSetting(TENANT_SIMULATION_DASHBOARD.key(), dashboard.getId());

      // -- EXECUTE & ASSERT --
      mockMvc
          .perform(get(CUSTOM_DASHBOARDS_URI + "/" + dashboard.getId() + "/usage").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.custom_dashboard_id").value(dashboard.getId()))
          .andExpect(jsonPath("$.scenarios_count").value(2))
          .andExpect(jsonPath("$.simulations_count").value(1))
          .andExpect(jsonPath("$.default_home_dashboard").value(false))
          .andExpect(jsonPath("$.default_scenario_dashboard").value(false))
          .andExpect(jsonPath("$.default_simulation_dashboard").value(true));
    }

    @Test
    void given_unused_dashboard_should_report_no_usage() throws Exception {
      // -- PREPARE --
      CustomDashboard dashboard = createCustomDashboardComposer().get();

      // -- EXECUTE & ASSERT --
      mockMvc
          .perform(get(CUSTOM_DASHBOARDS_URI + "/" + dashboard.getId() + "/usage").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.scenarios_count").value(0))
          .andExpect(jsonPath("$.simulations_count").value(0))
          .andExpect(jsonPath("$.default_home_dashboard").value(false))
          .andExpect(jsonPath("$.default_scenario_dashboard").value(false))
          .andExpect(jsonPath("$.default_simulation_dashboard").value(false));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
  @DisplayName("Restoring the starter-pack dashboards")
  class RestoreStarterPack {
    @Test
    void given_starter_pack_dashboards_deleted_should_restore_them_once_with_their_names()
        throws Exception {
      // -- PREPARE: none of the starter-pack dashboards exists, defaults are empty --
      STARTER_PACK_DASHBOARD_NAMES.forEach(
          name -> repository.findByName(name).ifPresent(repository::delete));
      setTenantSetting(TENANT_HOME_DASHBOARD.key(), "");
      setTenantSetting(TENANT_SCENARIO_DASHBOARD.key(), "");
      setTenantSetting(TENANT_SIMULATION_DASHBOARD.key(), "");

      // -- EXECUTE & ASSERT: the first run creates the three dashboards and sets the defaults --
      mockMvc
          .perform(post(CUSTOM_DASHBOARDS_URI + "/starter-pack/restore").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.restored_dashboards.length()").value(3))
          .andExpect(jsonPath("$.existing_dashboards.length()").value(0))
          .andExpect(jsonPath("$.defaults_set.length()").value(3));

      STARTER_PACK_DASHBOARD_NAMES.forEach(
          name -> assertThat(repository.findByName(name)).as(name).isPresent());
      assertThat(tenantSettingValue(TENANT_HOME_DASHBOARD.key()))
          .isEqualTo(repository.findByName("Test 1").orElseThrow().getId());
      assertThat(tenantSettingValue(TENANT_SCENARIO_DASHBOARD.key()))
          .isEqualTo(repository.findByName("Test 2").orElseThrow().getId());
      assertThat(tenantSettingValue(TENANT_SIMULATION_DASHBOARD.key()))
          .isEqualTo(repository.findByName("Test 3").orElseThrow().getId());

      // -- EXECUTE & ASSERT: a second run creates nothing and changes no default --
      long dashboardsAfterFirstRun = repository.count();
      mockMvc
          .perform(post(CUSTOM_DASHBOARDS_URI + "/starter-pack/restore").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.restored_dashboards.length()").value(0))
          .andExpect(jsonPath("$.existing_dashboards.length()").value(3))
          .andExpect(jsonPath("$.defaults_set.length()").value(0));
      assertThat(repository.count()).isEqualTo(dashboardsAfterFirstRun);
    }

    @Test
    void given_defaults_already_set_should_keep_them() throws Exception {
      // -- PREPARE --
      CustomDashboard custom = createCustomDashboardComposer().get();
      setTenantSetting(TENANT_HOME_DASHBOARD.key(), custom.getId());
      setTenantSetting(TENANT_SCENARIO_DASHBOARD.key(), custom.getId());
      setTenantSetting(TENANT_SIMULATION_DASHBOARD.key(), custom.getId());

      // -- EXECUTE --
      mockMvc
          .perform(post(CUSTOM_DASHBOARDS_URI + "/starter-pack/restore").with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.defaults_set.length()").value(0));

      // -- ASSERT --
      assertThat(tenantSettingValue(TENANT_HOME_DASHBOARD.key())).isEqualTo(custom.getId());
      assertThat(tenantSettingValue(TENANT_SCENARIO_DASHBOARD.key())).isEqualTo(custom.getId());
      assertThat(tenantSettingValue(TENANT_SIMULATION_DASHBOARD.key())).isEqualTo(custom.getId());
    }
  }
}
