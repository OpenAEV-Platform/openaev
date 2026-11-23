package io.openaev.rest.settings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.rest.settings.form.AssetFreshnessSettingsInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Tenant asset freshness setting: GET and PUT")
class AssetFreshnessSettingsApiTest extends IntegrationTest {

  private static final String ASSET_FRESHNESS_URI =
      TENANT_PREFIX + "/tenant-settings/asset-freshness";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String uriOfNewTenant(String name) throws Exception {
    String tenantId = tenantHelper.createTenantWithCurrentUser(name).getId();
    return ASSET_FRESHNESS_URI.replace("{tenantId}", tenantId);
  }

  private ResultActions putSettings(String uri, AssetFreshnessSettingsInput input)
      throws Exception {
    return mvc.perform(
        put(uri)
            .content(asJsonString(input))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf()));
  }

  @Test
  @DisplayName("given no tenant value should read the defaults: check off, no discovery tag")
  void given_noTenantValue_should_readTheDefaults() throws Exception {
    // Arrange
    String uri = uriOfNewTenant("asset-freshness-defaults");

    // Act + Assert
    mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_stale_after_days").value(0))
        .andExpect(jsonPath("$.asset_discovery_tag").value(false));
  }

  @Test
  @DisplayName("given a threshold and the discovery tag should store both and read them back")
  void given_thresholdAndTag_should_storeAndReadBack() throws Exception {
    // Arrange
    String uri = uriOfNewTenant("asset-freshness-update");

    // Act
    putSettings(uri, new AssetFreshnessSettingsInput(30, true))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_stale_after_days").value(30))
        .andExpect(jsonPath("$.asset_discovery_tag").value(true));

    // Act: a null tag keeps the stored one, the threshold moves
    putSettings(uri, new AssetFreshnessSettingsInput(7, null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_stale_after_days").value(7))
        .andExpect(jsonPath("$.asset_discovery_tag").value(true));

    // Assert
    mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asset_stale_after_days").value(7))
        .andExpect(jsonPath("$.asset_discovery_tag").value(true));
  }

  @Test
  @DisplayName("given a negative threshold should return 400")
  void given_negativeThreshold_should_return400() throws Exception {
    String uri = uriOfNewTenant("asset-freshness-invalid");
    putSettings(uri, new AssetFreshnessSettingsInput(-1, null)).andExpect(status().isBadRequest());
  }
}
