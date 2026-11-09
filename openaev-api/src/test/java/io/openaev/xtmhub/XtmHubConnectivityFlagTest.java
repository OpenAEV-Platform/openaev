package io.openaev.xtmhub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.SettingKeys;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantExtension;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The scheduled connectivity refresh runs with no request and no transaction: it reads the platform
 * settings and persists the "send the connectivity email" flag, a platform row of {@code
 * parameters}, with the production active tables.
 */
@WithoutTenantScope
@WithMockUser(isAdmin = true)
@ExtendWith(DefaultTenantExtension.class)
@DisplayName("XtmHubService connectivity flag with parameters active")
class XtmHubConnectivityFlagTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String FLAG = SettingKeys.XTM_HUB_SHOULD_SEND_CONNECTIVITY_EMAIL.key();

  @Autowired private XtmHubService xtmHubService;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private TenantComposer tenantComposer;

  @MockitoBean private XtmHubClient xtmHubClient;

  private Tenant tenant;
  private Optional<String> previousFlag;

  @AfterEach
  void cleanup() {
    if (tenant != null) {
      registrationRepository.deleteByTenantId(tenant.getId());
      tenantRepository.deleteById(tenant.getId());
    }
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Optional<Setting> current = settingRepository.findByKeyAndTenantIsNull(FLAG);
          if (previousFlag.isPresent()) {
            Setting setting = current.orElseGet(() -> new Setting(FLAG, previousFlag.get()));
            setting.setValue(previousFlag.get());
            settingRepository.save(setting);
          } else {
            current.ifPresent(settingRepository::delete);
          }
        });
  }

  @Test
  @DisplayName("given_hubReachableAgain_should_persistTheFlagAsAPlatformRow")
  void given_hubReachableAgain_should_persistTheFlagAsAPlatformRow() {
    // Arrange: the flag was switched off by an earlier connectivity loss
    previousFlag = platformSettingsService.setting(FLAG).map(Setting::getValue);
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Setting setting =
              settingRepository
                  .findByKeyAndTenantIsNull(FLAG)
                  .orElseGet(() -> new Setting(FLAG, ""));
          setting.setValue("false");
          settingRepository.save(setting);
        });
    tenant = TenantFixture.getTenant("xtm-hub-flag");
    tenantComposer.forTenant(tenant).persist();
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token-flag");
    registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
    registration.setConnectivityEmailEligible(true);
    registration.setTenant(tenant);
    registrationRepository.save(registration);
    when(xtmHubClient.refreshRegistrationStatusAllTenants(any(), any(), any(), any()))
        .thenReturn(Map.of(tenant.getId(), XtmHubConnectivityStatus.ACTIVE));

    // Act: the scheduled run, with no request and no transaction around it
    xtmHubService.refreshConnectivityAllTenants();

    // Assert
    assertThat(platformSettingsService.setting(FLAG).map(Setting::getValue)).contains("true");
  }
}
