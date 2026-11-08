package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.TENANT_XTMCOMPOSER_URI;
import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_ID;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_LAST_CONNECTIVITY_CHECK;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_NAME;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_PUBLIC_KEY;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_VERSION;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Capability;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The composer registration is one platform row of {@code parameters} that every tenant reads. Its
 * two writes, {@code register} and {@code refresh-connectivity}, need a platform capability: the
 * tenant-scoped catalog write that a tenant administrator holds does not reach them, on either
 * route.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("XTM Composer registration writes need a platform capability")
class XtmComposerRegistrationAuthorizationTest extends IntegrationTest {

  private static final String REGISTERED_ID = "registered-composer";
  private static final String REGISTERED_KEY = "registered-public-key";
  private static final String REGISTERED_CHECK = "2026-01-01T00:00:00Z";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private OpenAEVConfig openAEVConfig;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  @BeforeAll
  void setUp() {
    openAEVConfig.setVersion("test-version");
  }

  /** The registration the platform already holds, written the way only a platform write can. */
  private void seedRegistration() {
    Map<String, String> settings = new LinkedHashMap<>();
    settings.put(XTM_COMPOSER_ID.key(), REGISTERED_ID);
    settings.put(XTM_COMPOSER_NAME.key(), "registered composer");
    settings.put(XTM_COMPOSER_VERSION.key(), "registered-version");
    settings.put(XTM_COMPOSER_PUBLIC_KEY.key(), REGISTERED_KEY);
    settings.put(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key(), REGISTERED_CHECK);
    tenantScopedTransaction.setScopeOnCurrentTransaction(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID));
    tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    platformSettingsService.saveSettings(settings);
    entityManager.flush();
    entityManager.clear();
  }

  private String stored(String key) {
    entityManager.flush();
    entityManager.clear();
    return platformSettingsService.setting(key).map(Setting::getValue).orElse(null);
  }

  private static String registerBody(String id, String publicKey) throws Exception {
    XtmComposerRegisterInput input = new XtmComposerRegisterInput();
    input.setName("composer");
    input.setId(id);
    input.setPublicKey(publicKey);
    return asJsonString(input);
  }

  private void assertRegistrationUntouched() {
    assertEquals(REGISTERED_ID, stored(XTM_COMPOSER_ID.key()));
    assertEquals(REGISTERED_KEY, stored(XTM_COMPOSER_PUBLIC_KEY.key()));
    assertEquals(REGISTERED_CHECK, stored(XTM_COMPOSER_LAST_CONNECTIVITY_CHECK.key()));
  }

  @Nested
  @DisplayName("a tenant administrator")
  class TenantAdministrator {

    @Test
    @WithMockUser
    @DisplayName("given_tenantAdministratorOfAnotherTenant_should_refuseRegisterOnTheTenantRoute")
    void given_tenantAdministratorOfAnotherTenant_should_refuseRegisterOnTheTenantRoute()
        throws Exception {
      // Arrange: a caller who administers tenant B only, and the registration of the platform
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "composer-b", Set.of(Capability.MANAGE_TENANT_SETTINGS));
      seedRegistration();

      // Act + Assert
      mvc.perform(
              post(TENANT_XTMCOMPOSER_URI.replace("{tenantId}", tenantB.getId()) + "/register")
                  .content(registerBody("hijacked-composer", "hijacked-public-key"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
      assertRegistrationUntouched();
    }

    @Test
    @WithMockUser
    @DisplayName(
        "given_tenantAdministratorOfAnotherTenant_should_refuseRefreshConnectivityOnTheTenantRoute")
    void given_tenantAdministratorOfAnotherTenant_should_refuseRefreshConnectivityOnTheTenantRoute()
        throws Exception {
      // Arrange
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCapabilities(
              "composer-b", Set.of(Capability.MANAGE_TENANT_SETTINGS));
      seedRegistration();

      // Act + Assert: the caller knows the composer id, which the endpoint compares
      mvc.perform(
              put(TENANT_XTMCOMPOSER_URI.replace("{tenantId}", tenantB.getId())
                      + "/"
                      + REGISTERED_ID
                      + "/refresh-connectivity")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
      assertRegistrationUntouched();
    }

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName(
        "given_tenantAdministratorOfTheDefaultTenant_should_refuseRegisterOnThePlatformRoute")
    void given_tenantAdministratorOfTheDefaultTenant_should_refuseRegisterOnThePlatformRoute()
        throws Exception {
      // Arrange
      seedRegistration();

      // Act + Assert
      mvc.perform(
              post(XTMCOMPOSER_URI + "/register")
                  .content(registerBody("hijacked-composer", "hijacked-public-key"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
      mvc.perform(
              put(XTMCOMPOSER_URI + "/" + REGISTERED_ID + "/refresh-connectivity")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isForbidden());
      assertRegistrationUntouched();
    }
  }

  @Nested
  @DisplayName("an identity that holds the catalog write right and nothing platform-wide")
  class CatalogOnlyIdentity {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_TENANT_SETTINGS})
    @DisplayName("given_aCatalogWriteIdentityWithNoMembership_should_refuseBothWritesOnBothRoutes")
    void given_aCatalogWriteIdentityWithNoMembership_should_refuseBothWritesOnBothRoutes()
        throws Exception {
      // Arrange: the one capability that carries the catalog write right, no platform right, and
      // no tenant membership, which is the shape of a token user made for the composer
      assertEquals(
          Capability.MANAGE_TENANT_SETTINGS,
          Capability.of(ResourceType.CATALOG, Action.WRITE).orElseThrow());
      assertNotEquals(
          Capability.MANAGE_TENANT_SETTINGS,
          Capability.of(ResourceType.PLATFORM_SETTING, Action.WRITE).orElseThrow());
      seedRegistration();
      String tenantRoute = TENANT_XTMCOMPOSER_URI.replace("{tenantId}", Tenant.DEFAULT_TENANT_UUID);

      // Act + Assert: the decision is the platform setting write right; a caller who only holds
      // the catalog write right must be given it to register or refresh
      for (String base : new String[] {XTMCOMPOSER_URI, tenantRoute}) {
        mvc.perform(
                post(base + "/register")
                    .content(registerBody("hijacked-composer", "hijacked-public-key"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(
                put(base + "/" + REGISTERED_ID + "/refresh-connectivity")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isForbidden());
      }
      assertRegistrationUntouched();
    }
  }

  @Nested
  @DisplayName("a platform caller")
  class PlatformCaller {

    @Test
    @WithMockUser(withCapabilities = {Capability.MANAGE_PLATFORM_SETTINGS})
    @DisplayName("given_platformSettingsManager_should_registerAndRefreshOnThePlatformRoute")
    void given_platformSettingsManager_should_registerAndRefreshOnThePlatformRoute()
        throws Exception {
      // Arrange
      seedRegistration();

      // Act: the composer's machine identity belongs to no tenant
      mvc.perform(
              post(XTMCOMPOSER_URI + "/register")
                  .content(registerBody("new-composer", "new-public-key"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
      mvc.perform(
              put(XTMCOMPOSER_URI + "/new-composer/refresh-connectivity")
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("new-composer", stored(XTM_COMPOSER_ID.key()));
      assertEquals("new-public-key", stored(XTM_COMPOSER_PUBLIC_KEY.key()));
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given_administrator_should_registerOnTheTenantRoute")
    void given_administrator_should_registerOnTheTenantRoute() throws Exception {
      // Arrange
      seedRegistration();

      // Act
      mvc.perform(
              post(TENANT_XTMCOMPOSER_URI.replace("{tenantId}", Tenant.DEFAULT_TENANT_UUID)
                      + "/register")
                  .content(registerBody("new-composer", "new-public-key"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("new-composer", stored(XTM_COMPOSER_ID.key()));
    }
  }
}
