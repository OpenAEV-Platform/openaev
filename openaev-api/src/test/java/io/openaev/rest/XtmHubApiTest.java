package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.xtmhub.XtmHubApi;
import io.openaev.api.xtmhub.XtmHubContactUsInput;
import io.openaev.api.xtmhub.XtmHubRegisterInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantExtension;
import io.openaev.xtmhub.XtmHubClient;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@AutoConfigureMockMvc
@ExtendWith({MockitoExtension.class, DefaultTenantExtension.class})
@DisplayName("XTM Hub API tests")
public class XtmHubApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;
  @Autowired private TenantComposer tenantComposer;

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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(JsonPath.read(response, "$.tenant_xtmhub_registration_id"));
    assertEquals(token, (String) JsonPath.read(response, "$.tenant_xtmhub_registration_token"));
    assertEquals(
        XtmHubRegistrationStatus.REGISTERED.name(),
        (String) JsonPath.read(response, "$.tenant_xtmhub_registration_status"));
    assertEquals(
        testUserHolder.get().getId(),
        (String) JsonPath.read(response, "$.tenant_xtmhub_registration_user_id"));
    assertEquals(
        testUserHolder.get().getName(),
        (String) JsonPath.read(response, "$.tenant_xtmhub_registration_user_name"));
    assertNotNull(JsonPath.read(response, "$.tenant_xtmhub_registration_date"));
    assertNotNull(JsonPath.read(response, "$.tenant_xtmhub_registration_last_connectivity_check"));

    // Verify the entity was actually persisted in the database for the correct tenant
    TenantXtmHubRegistration saved =
        tenantXtmHubRegistrationRepository
            .findByTenantId(Tenant.DEFAULT_TENANT_UUID)
            .orElseThrow(() -> new AssertionError("No TenantXtmHubRegistration found in database"));
    assertEquals(token, saved.getToken());
    assertEquals(XtmHubRegistrationStatus.REGISTERED, saved.getRegistrationStatus());
    assertEquals(testUserHolder.get().getId(), saved.getRegistrationUserId());
    assertEquals(testUserHolder.get().getName(), saved.getRegistrationUserName());
    assertNotNull(saved.getRegistrationDate());
    assertNotNull(saved.getLastConnectivityCheck());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Should delete registration data")
  public void whenUnregisterDeleteRegistrationData() throws Exception {
    // Setup: register first so there is something to delete
    XtmHubRegisterInput registerInput = new XtmHubRegisterInput();
    registerInput.setToken("token-to-delete");
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/register")
                .with(csrf())
                .content(asJsonString(registerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    assertTrue(
        tenantXtmHubRegistrationRepository.findByTenantId(Tenant.DEFAULT_TENANT_UUID).isPresent(),
        "Registration should exist before unregister");

    // When
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/unregister")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // Then: entity must be gone
    assertFalse(
        tenantXtmHubRegistrationRepository.findByTenantId(Tenant.DEFAULT_TENANT_UUID).isPresent(),
        "Registration should have been deleted after unregister");
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Should return registration data when registered")
  public void whenGetRegistrationAndRegistered_ShouldReturnRegistration() throws Exception {
    // Setup: register first
    XtmHubRegisterInput registerInput = new XtmHubRegisterInput();
    registerInput.setToken("token-get");
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/register")
                .content(asJsonString(registerInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // When
    String response =
        mvc.perform(get(XtmHubApi.XTMHUB_URI + "/registration").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    assertNotNull(JsonPath.read(response, "$.tenant_xtmhub_registration_id"));
    assertEquals(
        "token-get", (String) JsonPath.read(response, "$.tenant_xtmhub_registration_token"));
    assertEquals(
        XtmHubRegistrationStatus.REGISTERED.name(),
        (String) JsonPath.read(response, "$.tenant_xtmhub_registration_status"));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Should return 204 when not registered")
  public void whenGetRegistrationAndNotRegistered_ShouldReturn204() throws Exception {
    mvc.perform(get(XtmHubApi.XTMHUB_URI + "/registration").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("Should scope registration to the correct tenant and not leak to other tenants")
  public void whenRegisterForCustomTenant_ShouldSaveForThatTenantOnlyAndIsolateFromOthers()
      throws Exception {
    // Setup: create a second tenant and switch context to it
    Tenant customTenant = tenantComposer.forTenant(TenantFixture.getTenant()).persist().get();
    entityManager.flush();
    TenantContext.setCurrentTenant(customTenant.getId());

    XtmHubRegisterInput input = new XtmHubRegisterInput();
    input.setToken("custom-tenant-token");

    // When: register under the custom tenant
    mvc.perform(
            put(XtmHubApi.XTMHUB_URI + "/register")
                .with(csrf())
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // Then: registration exists for the custom tenant
    assertTrue(
        tenantXtmHubRegistrationRepository.findByTenantId(customTenant.getId()).isPresent(),
        "Registration should exist for the custom tenant");

    // And: registration does NOT leak into the default tenant
    assertFalse(
        tenantXtmHubRegistrationRepository.findByTenantId(Tenant.DEFAULT_TENANT_UUID).isPresent(),
        "Registration should not be visible under the default tenant");
  }

  @MockitoBean private XtmHubClient xtmHubClient;

  @Test
  @WithMockUser()
  @DisplayName("Should successfully send contact message")
  public void whenContactUsSendMessage() throws Exception {
    String message = "I would like to get more information about your services";
    XtmHubContactUsInput input = new XtmHubContactUsInput();
    input.setMessage(message);

    when(xtmHubClient.contactUs(any(), any(), any())).thenReturn(true);

    String response =
        mvc.perform(
                post(XtmHubApi.XTMHUB_URI + "/contact-us")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Boolean result = JsonPath.read(response, "$");
    assertTrue(result);
  }
}
