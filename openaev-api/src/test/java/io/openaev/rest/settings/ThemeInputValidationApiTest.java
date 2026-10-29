package io.openaev.rest.settings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.rest.settings.form.ThemeInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Theme colors validation")
class ThemeInputValidationApiTest extends IntegrationTest {

  private static final String PLATFORM_THEME_URI = "/api/settings/theme/dark";
  private static final String TENANT_THEME_URI = TENANT_PREFIX + "/tenant-settings/theme/dark";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private static ThemeInput themeWith(Consumer<ThemeInput> customizer) {
    ThemeInput input = new ThemeInput();
    customizer.accept(input);
    return input;
  }

  private ResultActions putTheme(String uri, ThemeInput input) throws Exception {
    return mvc.perform(
        put(uri)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  @Nested
  @DisplayName("Platform theme endpoint")
  class PlatformTheme {

    @Test
    @DisplayName("Given a value that is not a color should return 400")
    void given_not_a_color_should_return_400() throws Exception {
      putTheme(PLATFORM_THEME_URI, themeWith(i -> i.setPrimaryColor("notacolor")))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given a short hex color should return 400")
    void given_short_hex_color_should_return_400() throws Exception {
      putTheme(PLATFORM_THEME_URI, themeWith(i -> i.setBackgroundColor("#abc")))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given an invalid login aside gradient color should return 400")
    void given_invalid_login_aside_gradient_should_return_400() throws Exception {
      putTheme(PLATFORM_THEME_URI, themeWith(i -> i.setLoginAsideGradientStart("notacolor")))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given valid hex colors should return 200")
    void given_valid_hex_colors_should_return_200() throws Exception {
      putTheme(
              PLATFORM_THEME_URI,
              themeWith(
                  i -> {
                    i.setPrimaryColor("#AABBCC");
                    i.setTextColor("#aabbcc");
                  }))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given empty colors should return 200")
    void given_empty_colors_should_return_200() throws Exception {
      putTheme(
              PLATFORM_THEME_URI,
              themeWith(
                  i -> {
                    i.setPrimaryColor("");
                    i.setAccentColor("");
                  }))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Tenant theme endpoint")
  class TenantTheme {

    private String tenantThemeUri() throws Exception {
      String tenantId = tenantHelper.createTenantWithCurrentUser("theme-validation").getId();
      return TENANT_THEME_URI.replace("{tenantId}", tenantId);
    }

    @Test
    @DisplayName("Given a value that is not a color should return 400")
    void given_not_a_color_should_return_400() throws Exception {
      putTheme(tenantThemeUri(), themeWith(i -> i.setPrimaryColor("notacolor")))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Given valid hex colors should return 200")
    void given_valid_hex_colors_should_return_200() throws Exception {
      putTheme(tenantThemeUri(), themeWith(i -> i.setPrimaryColor("#AABBCC")))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Given empty colors should return 200")
    void given_empty_colors_should_return_200() throws Exception {
      putTheme(tenantThemeUri(), themeWith(i -> i.setPrimaryColor(""))).andExpect(status().isOk());
    }
  }
}
