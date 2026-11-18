package io.openaev.rest.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.scheduler.jobs.InjectsExecutionJob;
import io.openaev.service.settings.InjectExecutionThresholdService;
import io.openaev.utils.mockUser.WithMockUser;
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
@DisplayName("Inject execution threshold setting")
class InjectExecutionSettingsApiTest extends IntegrationTest {

  private static final String URI = "/api/settings/inject-execution";

  @Autowired private MockMvc mvc;
  @Autowired private SettingRepository settingRepository;
  @Autowired private InjectExecutionThresholdService injectExecutionThresholdService;

  private static String body(Object minutes) {
    return "{\"inject_execution_threshold_minutes\": " + minutes + "}";
  }

  @Nested
  @DisplayName("as a platform admin")
  class AsAdmin {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_settingUnset_should_returnTheConfiguredThreshold")
    void given_settingUnset_should_returnTheConfiguredThreshold() throws Exception {
      settingRepository
          .findByKeyAndTenantIsNull(SettingKeys.INJECT_EXECUTION_THRESHOLD_MINUTES.key())
          .ifPresent(settingRepository::delete);
      entityManager.flush();

      mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.inject_execution_threshold_minutes")
                  .value(InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES))
          .andExpect(
              jsonPath("$.inject_execution_threshold_minutes_configured")
                  .value(InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_validThreshold_should_persistItAndReturnTheLiveValue")
    void given_validThreshold_should_persistItAndReturnTheLiveValue() throws Exception {
      mvc.perform(put(URI).contentType(MediaType.APPLICATION_JSON).content(body(60)).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_execution_threshold_minutes").value(60));
      entityManager.flush();
      entityManager.clear();

      assertEquals(60, injectExecutionThresholdService.thresholdMinutes());
      mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.inject_execution_threshold_minutes").value(60));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_thresholdOutOfRange_should_refuseIt")
    void given_thresholdOutOfRange_should_refuseIt() throws Exception {
      mvc.perform(put(URI).contentType(MediaType.APPLICATION_JSON).content(body(0)).with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(put(URI).contentType(MediaType.APPLICATION_JSON).content(body(1441)).with(csrf()))
          .andExpect(status().isBadRequest());
      mvc.perform(
              put(URI).contentType(MediaType.APPLICATION_JSON).content(body("null")).with(csrf()))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("as a user without platform settings rights")
  class AsNonAdmin {

    @Test
    @WithMockUser
    @DisplayName("given_nonAdmin_should_beRefused")
    void given_nonAdmin_should_beRefused() throws Exception {
      mvc.perform(put(URI).contentType(MediaType.APPLICATION_JSON).content(body(60)).with(csrf()))
          .andExpect(status().isForbidden());
    }
  }
}
