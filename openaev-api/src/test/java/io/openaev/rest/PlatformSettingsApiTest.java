package io.openaev.rest;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestInstance(PER_CLASS)
@DisplayName("Platform Settings API")
class PlatformSettingsApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  private static final List<String> PUBLIC_FIELDS =
      List.of(
          "platform_theme",
          "platform_lang",
          "auth_openid_enable",
          "auth_saml2_enable",
          "auth_local_enable",
          "platform_policies",
          "platform_light_theme",
          "platform_dark_theme",
          "enabled_dev_features",
          "platform_whitemark");

  private static final List<String> PRIVATE_FIELDS =
      List.of(
          // Platform identity
          "platform_id",
          "platform_name",
          "platform_base_url",
          // OpenCTI
          "xtm_opencti_enable",
          "xtm_opencti_url",
          // XTM Hub (config-driven, always present)
          "xtm_hub_enable",
          "xtm_hub_url",
          "xtm_hub_reachable",
          // AI config
          "platform_ai_enabled",
          "platform_ai_has_token",
          "platform_ai_type",
          "platform_ai_model",
          // Email config
          "default_mailer",
          "default_reply_to");

  private static void assertFieldsExist(ResultActions result, List<String> fields)
      throws Exception {
    for (String field : fields) {
      result.andExpect(jsonPath("$." + field).exists());
    }
  }

  private static void assertFieldsDoNotExist(ResultActions result, List<String> fields)
      throws Exception {
    for (String field : fields) {
      result.andExpect(jsonPath("$." + field).doesNotExist());
    }
  }

  @Nested
  @DisplayName("Public settings endpoint")
  class PublicSettingsEndpoint {

    @Test
    @DisplayName("Given unauthenticated user should return public settings")
    void given_unauthenticated_user_should_return_public_settings() throws Exception {
      // -- ARRANGE --

      // -- ACT --
      ResultActions result =
          mvc.perform(get("/api/settings/public").accept(MediaType.APPLICATION_JSON));

      // -- ASSERT --
      result.andExpect(status().isOk());
      assertFieldsExist(result, PUBLIC_FIELDS);
    }

    @Test
    @DisplayName("Given unauthenticated user should not return sensitive fields")
    void given_unauthenticated_user_should_not_return_sensitive_fields() throws Exception {
      // -- ARRANGE --

      // -- ACT --
      ResultActions result =
          mvc.perform(get("/api/settings/public").accept(MediaType.APPLICATION_JSON));

      // -- ASSERT --
      result.andExpect(status().isOk());
      assertFieldsDoNotExist(result, PRIVATE_FIELDS);
    }
  }

  @Nested
  @DisplayName("Private settings endpoint")
  class PrivateSettingsEndpoint {

    @Test
    @DisplayName("Given unauthenticated user should return 401")
    void given_unauthenticated_user_should_return_401() throws Exception {
      // -- ARRANGE --

      // -- ACT --
      mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON))
          // -- ASSERT --
          .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Given authenticated admin should return full settings")
    void given_authenticated_admin_should_return_full_settings() throws Exception {
      // -- ARRANGE --

      // -- ACT --
      ResultActions result = mvc.perform(get("/api/settings").accept(MediaType.APPLICATION_JSON));

      // -- ASSERT --
      result.andExpect(status().isOk());
      assertFieldsExist(result, PUBLIC_FIELDS);
      assertFieldsExist(result, PRIVATE_FIELDS);
    }
  }
}
