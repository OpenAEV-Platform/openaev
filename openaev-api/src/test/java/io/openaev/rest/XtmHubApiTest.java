package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.xtmhub.XtmHubApi;
import io.openaev.api.xtmhub.XtmHubRegisterInput;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
  @WithMockUser(isAdmin = true)
  @DisplayName("Should save registration data")
  public void whenRegisterUpdateRegistrationData() throws Exception {
    String token = "token";
    XtmHubRegisterInput input = new XtmHubRegisterInput();
    input.setToken(token);
    String response =
        mvc.perform(
                put(XtmHubApi.XTMHUB_URI + "/register")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String responseToken = JsonPath.read(response, "$.xtm_hub_token");
    String responseStatus = JsonPath.read(response, "$.xtm_hub_registration_status");
    String responseUserId = JsonPath.read(response, "$.xtm_hub_registration_user_id");
    String responseUserName = JsonPath.read(response, "$.xtm_hub_registration_user_name");
    String responseRegistrationDate = JsonPath.read(response, "$.xtm_hub_registration_date");
    String responseLastConnectivityCheck =
        JsonPath.read(response, "$.xtm_hub_last_connectivity_check");
    String responseShouldSendConnectivityEmail =
        JsonPath.read(response, "$.xtm_hub_should_send_connectivity_email");
    assertEquals(responseToken, token);
    assertEquals(responseStatus, XtmHubRegistrationStatus.REGISTERED.label);
    assertEquals(responseUserId, testUserHolder.get().getId());
    assertEquals(responseUserName, testUserHolder.get().getName());
    assertNotNull(responseRegistrationDate);
    assertNotNull(responseLastConnectivityCheck);
    assertEquals(responseShouldSendConnectivityEmail, "true");

    PlatformSettings settings = platformSettingsService.findSettings();
    assertEquals(settings.getXtmHubToken(), token);
    assertEquals(settings.getXtmHubRegistrationStatus(), XtmHubRegistrationStatus.REGISTERED.label);
    assertEquals(settings.getXtmHubRegistrationUserId(), testUserHolder.get().getId());
    assertEquals(settings.getXtmHubRegistrationUserName(), testUserHolder.get().getName());
    assertNotNull(settings.getXtmHubRegistrationDate());
    assertNotNull(responseLastConnectivityCheck);
    assertEquals(responseShouldSendConnectivityEmail, "true");
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Should delete registration data")
  public void whenUnregisterDeleteRegistrationData() throws Exception {
    String response =
        mvc.perform(
                put(XtmHubApi.XTMHUB_URI + "/unregister")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String responseToken = JsonPath.read(response, "$.xtm_hub_token");
    String responseStatus = JsonPath.read(response, "$.xtm_hub_registration_status");
    String responseUserId = JsonPath.read(response, "$.xtm_hub_registration_user_id");
    String responseUserName = JsonPath.read(response, "$.xtm_hub_registration_user_name");
    String responseRegistrationDate = JsonPath.read(response, "$.xtm_hub_registration_date");
    String responseLastConnectivityCheck =
        JsonPath.read(response, "$.xtm_hub_last_connectivity_check");
    String responseShouldSendConnectivityEmail =
        JsonPath.read(response, "$.xtm_hub_should_send_connectivity_email");
    assertNull(responseToken);
    assertNull(responseStatus);
    assertNull(responseUserId);
    assertNull(responseUserName);
    assertNull(responseRegistrationDate);
    assertNull(responseLastConnectivityCheck);
    assertNull(responseShouldSendConnectivityEmail);

    PlatformSettings settings = platformSettingsService.findSettings();
    assertNull(settings.getXtmHubToken());
    assertNull(settings.getXtmHubRegistrationStatus());
    assertNull(settings.getXtmHubRegistrationUserId());
    assertNull(settings.getXtmHubRegistrationUserName());
    assertNull(settings.getXtmHubRegistrationDate());
    assertNull(settings.getXtmHubLastConnectivityCheck());
    assertNull(settings.getXtmHubShouldSendConnectivityEmail());
  }
}
