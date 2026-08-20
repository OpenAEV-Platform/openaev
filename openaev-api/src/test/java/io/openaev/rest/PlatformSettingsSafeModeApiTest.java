package io.openaev.rest;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.BannerMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@TestPropertySource(properties = "openaev.run-mode=safe")
@DisplayName("Platform Settings API (safe mode)")
class PlatformSettingsSafeModeApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Nested
  @DisplayName("Public settings endpoint")
  class PublicSettingsEndpoint {

    @Test
    @DisplayName("Given safe run mode should expose safe banner and mode")
    void given_safe_run_mode_should_expose_safe_banner_and_mode() throws Exception {
      // -- ARRANGE --

      // -- ACT --
      var result =
          mvc.perform(get("/api/settings/public").with(csrf()).accept(MediaType.APPLICATION_JSON));

      // -- ASSERT --
      result
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.platform_run_mode").value("safe"))
          .andExpect(
              jsonPath("$.platform_banner_by_level.warn")
                  .value(hasItem(BannerMessage.BANNER_KEYS.SAFE_MODE_ENABLED.message())));
    }
  }
}
