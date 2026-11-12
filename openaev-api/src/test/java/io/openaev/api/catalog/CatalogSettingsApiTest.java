package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogSettingsApi.CATALOG_SETTINGS_URI;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.catalog.dto.CatalogSettingsInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING,CATALOG")
@DisplayName("Catalogue settings")
class CatalogSettingsApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  @BeforeEach
  void attachToDefaultTenant() {
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT_UUID);
  }

  private String readSettings() throws Exception {
    return mvc.perform(get(CATALOG_SETTINGS_URI, DEFAULT_TENANT_UUID))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  @DisplayName("new payloads and scenarios install themselves by default, until an admin says no")
  void given_settingsTurnedOff_should_readThemBackOff() throws Exception {
    // Act
    String before = readSettings();
    mvc.perform(
            put(CATALOG_SETTINGS_URI, DEFAULT_TENANT_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CatalogSettingsInput(false, false)))
                .with(csrf()))
        .andExpect(status().isOk());
    entityManager.flush();
    String after = readSettings();

    // Assert
    assertThatJson(before)
        .isEqualTo("{\"payload_auto_install\":true,\"scenario_auto_install\":true}");
    assertThatJson(after)
        .isEqualTo("{\"payload_auto_install\":false,\"scenario_auto_install\":false}");
  }
}
