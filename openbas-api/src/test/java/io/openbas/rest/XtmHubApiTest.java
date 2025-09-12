package io.openbas.rest;

import static io.openbas.database.model.User.*;
import static io.openbas.utils.JsonUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.repository.ExerciseRepository;
import io.openbas.database.repository.SettingRepository;
import io.openbas.rest.settings.response.PlatformSettings;
import io.openbas.rest.xtmhub.XtmHubApi;
import io.openbas.rest.xtmhub.XtmHubRegisterInput;
import io.openbas.service.PlatformSettingsService;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.xtmhub.XtmHubRegistrationStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("XTM Hub API tests")
public class XtmHubApiTest extends IntegrationTest {
  @Autowired private MockMvc mvc;

  @Autowired private PlatformSettingsService platformSettingsService;

  @Test
  @WithMockAdminUser
  @DisplayName("Should save registration data")
  public void whenRegisterUpdateRegistrationData() throws Exception {
    String token = "token";
    XtmHubRegisterInput input = new XtmHubRegisterInput();
    input.setToken(token);
    String response = mvc.perform(
      put(XtmHubApi.ENDPOINT_URI + "/register")
        .content(asJsonString(input))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
      )
      .andExpect(status().is2xxSuccessful())
      .andReturn()
      .getResponse()
      .getContentAsString();
    String responseToken = JsonPath.read(response, "$.xtm_hub_token");
    String responseStatus = JsonPath.read(response, "$.xtm_hub_registration_status");
    String responseUserId = JsonPath.read(response, "$.xtm_hub_registration_user_id");
    String responseUserName = JsonPath.read(response, "$.xtm_hub_registration_user_name");
    String responseRegistrationDate = JsonPath.read(response, "$.xtm_hub_registration_date");
    assertEquals(responseToken, token);
    assertEquals(responseStatus, XtmHubRegistrationStatus.REGISTERED.label);
    assertEquals(responseUserId, ADMIN_UUID);
    assertEquals(responseUserName, ADMIN_FIRSTNAME + " " + ADMIN_LASTNAME);
    assertNotNull(responseRegistrationDate);

    PlatformSettings settings = platformSettingsService.findSettings();
    assertEquals(settings.getXtmHubToken(), token);
    assertEquals(settings.getXtmHubRegistrationStatus(), XtmHubRegistrationStatus.REGISTERED.label);
    assertEquals(settings.getXtmHubRegistrationUserId(), ADMIN_UUID);
    assertEquals(settings.getXtmHubRegistrationUserName(), ADMIN_FIRSTNAME + " " + ADMIN_LASTNAME);
    assertNotNull(settings.getXtmHubRegistrationDate());
  }

  @Test
  @WithMockAdminUser
  @DisplayName("Should delete registration data")
  public void whenUnregisterDeleteRegistrationData() throws Exception {
    String response = mvc.perform(
                    put(XtmHubApi.ENDPOINT_URI + "/unregister")
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String responseToken = JsonPath.read(response, "$.xtm_hub_token");
    String responseStatus = JsonPath.read(response, "$.xtm_hub_registration_status");
    String responseUserId = JsonPath.read(response, "$.xtm_hub_registration_user_id");
    String responseUserName = JsonPath.read(response, "$.xtm_hub_registration_user_name");
    String responseRegistrationDate = JsonPath.read(response, "$.xtm_hub_registration_date");
    assertNull(responseToken);
    assertEquals(responseStatus, XtmHubRegistrationStatus.UNREGISTERED.label);
    assertNull(responseUserId);
    assertNull(responseUserName);
    assertNull(responseRegistrationDate);

    PlatformSettings settings = platformSettingsService.findSettings();
    assertNull(settings.getXtmHubToken());
    assertEquals(settings.getXtmHubRegistrationStatus(), XtmHubRegistrationStatus.UNREGISTERED.label);
    assertNull(settings.getXtmHubRegistrationUserId());
    assertNull(settings.getXtmHubRegistrationUserName());
    assertNull(settings.getXtmHubRegistrationDate());
  }
}
