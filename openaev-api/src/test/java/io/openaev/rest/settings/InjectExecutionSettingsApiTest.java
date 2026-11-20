package io.openaev.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.repository.SettingRepository;
import io.openaev.scheduler.jobs.InjectsExecutionJob;
import io.openaev.service.settings.InjectExecutionThresholdService;
import io.openaev.service.settings.RuntimeSetting;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("Runtime settings: the UI value over the configured default")
class InjectExecutionSettingsApiTest extends IntegrationTest {

  private static final String URI = "/api/settings/runtime";
  private static final String THRESHOLD = RuntimeSetting.INJECT_EXECUTION_THRESHOLD_MINUTES.key();

  @Autowired private MockMvc mvc;
  @Autowired private SettingRepository settingRepository;
  @Autowired private InjectExecutionThresholdService injectExecutionThresholdService;

  private static String body(String key, Object value) {
    return "{\"settings\": {\"" + key + "\": " + value + "}}";
  }

  private static String setting(String key, String field) {
    return "$.runtime_settings[?(@.key == '" + key + "')]." + field;
  }

  @BeforeEach
  void clearStoredValues() {
    for (RuntimeSetting runtimeSetting : RuntimeSetting.values()) {
      settingRepository
          .findByKeyAndTenantIsNull(runtimeSetting.key())
          .ifPresent(settingRepository::delete);
    }
    entityManager.flush();
  }

  @Nested
  @DisplayName("as a platform admin")
  class AsAdmin {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_nothingStored_should_returnTheDefaultWithItsSource")
    void given_nothingStored_should_returnTheDefaultWithItsSource() throws Exception {
      mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath(setting(THRESHOLD, "value"))
                  .value(
                      String.valueOf(
                          InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES)))
          .andExpect(jsonPath(setting(THRESHOLD, "source")).value("default"))
          .andExpect(jsonPath(setting(THRESHOLD, "locked")).value(false))
          .andExpect(
              jsonPath(setting(THRESHOLD, "env_var")).value("INJECT_EXECUTION_THRESHOLD_MINUTES"))
          .andExpect(
              jsonPath(setting(RuntimeSetting.AGENT_MAX_SIMULTANEOUS_JOBS.key(), "value"))
                  .value("5"))
          .andExpect(
              jsonPath(setting(RuntimeSetting.AGENT_QUEUE_THRESHOLD.key(), "value")).value("0"))
          .andExpect(
              jsonPath(setting(RuntimeSetting.AGENT_AUTO_UPDATE_ENABLED.key(), "type"))
                  .value("boolean"));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_validValues_should_storeThemAndApplyThemLive")
    void given_validValues_should_storeThemAndApplyThemLive() throws Exception {
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"settings\": {\""
                          + THRESHOLD
                          + "\": 60, \"agent_max_simultaneous_jobs\": 8,"
                          + " \"agent_auto_update_enabled\": false}}")
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(setting(THRESHOLD, "value")).value("60"))
          .andExpect(jsonPath(setting(THRESHOLD, "source")).value("ui"))
          .andExpect(jsonPath(setting("agent_max_simultaneous_jobs", "value")).value("8"))
          .andExpect(jsonPath(setting("agent_auto_update_enabled", "value")).value("false"));
      entityManager.flush();
      entityManager.clear();

      assertEquals(60, injectExecutionThresholdService.thresholdMinutes());
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_invalidValue_should_refuseTheWholeWrite")
    void given_invalidValue_should_refuseTheWholeWrite() throws Exception {
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(THRESHOLD, 0))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(THRESHOLD, 1441))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("agent_auto_update_enabled", "\"maybe\""))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"settings\": {\"" + THRESHOLD + "\": 30, \"agent_queue_threshold\": -1}}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("platform_name", "\"x\""))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      entityManager.flush();

      assertEquals(
          InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES,
          injectExecutionThresholdService.thresholdMinutes());
    }
  }

  @Nested
  @DisplayName("as a user without platform settings rights")
  class AsNonAdmin {

    @Test
    @WithMockUser
    @DisplayName("given_nonAdmin_should_beRefused")
    void given_nonAdmin_should_beRefused() throws Exception {
      mvc.perform(
              put(URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(THRESHOLD, 60))
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }
  }
}
