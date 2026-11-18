package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.TENANT_XTMCOMPOSER_URI;
import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_ID;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_LAST_CONNECTIVITY_CHECK;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_NAME;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_PUBLIC_KEY;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_VERSION;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The XTM Composer connector-instance endpoints (the read of the instances managed by a composer,
 * and the status / logs / health-check callbacks) drive the composer, which operates above any one
 * tenant. They therefore need the same platform capability as {@code register} and {@code
 * refresh-connectivity}: the tenant-scoped catalog right a tenant settings identity holds does not
 * reach them, on either route, while a platform settings manager does.
 */
@Transactional
@DisplayName("XTM Composer connector-instance endpoints need a platform capability")
class XtmComposerConnectorInstancesAuthorizationTest extends IntegrationTest {

  private static final String REGISTERED_ID = "registered-composer";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @BeforeEach
  void seedRegistration() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put(XTM_COMPOSER_ID.key(), REGISTERED_ID);
    settings.put(XTM_COMPOSER_NAME.key(), "registered composer");
    settings.put(XTM_COMPOSER_VERSION.key(), "registered-version");
    settings.put(XTM_COMPOSER_PUBLIC_KEY.key(), "registered-public-key");
    settings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), "2026-01-01T00:00:00Z");
    tenantScopedTransaction.setScopeOnCurrentTransaction(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
    tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    platformSettingsService.saveSettings(settings);
    entityManager.flush();
    entityManager.clear();
  }

  private String readUri(String base) {
    return base.replace("{tenantId}", Tenant.DEFAULT_TENANT_UUID)
        + "/"
        + REGISTERED_ID
        + "/connector-instances";
  }

  private String statusUri(String base) {
    return base.replace("{tenantId}", Tenant.DEFAULT_TENANT_UUID)
        + "/"
        + REGISTERED_ID
        + "/connector-instances/any-instance/status";
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.ACCESS_TENANT_SETTINGS})
  @DisplayName("given_tenantSettingsReader_should_notReachTheInstancesRead")
  void given_tenantSettingsReader_should_notReachTheInstancesRead() throws Exception {
    mvc.perform(get(readUri(XTMCOMPOSER_URI))).andExpect(status().isForbidden());
    mvc.perform(get(readUri(TENANT_XTMCOMPOSER_URI))).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
  @DisplayName("given_tenantSettingsManager_should_notReachTheStatusWrite")
  void given_tenantSettingsManager_should_notReachTheStatusWrite() throws Exception {
    mvc.perform(
            put(statusUri(XTMCOMPOSER_URI))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connector_instance_current_status\":\"started\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            put(statusUri(TENANT_XTMCOMPOSER_URI))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"connector_instance_current_status\":\"started\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_SETTINGS})
  @DisplayName("given_platformSettingsManager_should_reachTheInstancesRead")
  void given_platformSettingsManager_should_reachTheInstancesRead() throws Exception {
    mvc.perform(get(readUri(XTMCOMPOSER_URI))).andExpect(status().isOk());
  }
}
