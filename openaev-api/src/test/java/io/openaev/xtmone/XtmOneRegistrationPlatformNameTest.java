package io.openaev.xtmone;

import static io.openaev.database.model.TenantSettingKeys.PLATFORM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The scheduled registration with XTM One runs with no request and no transaction: the platform
 * name a tenant gave itself is a tenant row of {@code parameters}, read here with the production
 * active tables.
 */
@WithoutTenantScope
@DisplayName("XTM One registration sends the name the default tenant gave the platform")
class XtmOneRegistrationPlatformNameTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private XtmOneService xtmOneService;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  @MockitoBean private XtmOneConfig config;
  @MockitoBean private XtmOneClient client;

  @AfterEach
  void cleanup() {
    tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () ->
            settingRepository
                .findByKeyAndTenantId(PLATFORM_NAME.key(), Tenant.DEFAULT_TENANT_UUID)
                .ifPresent(settingRepository::delete));
  }

  @Test
  @DisplayName("given_aDefaultTenantRename_should_registerWithTheRenamedPlatform")
  void given_aDefaultTenantRename_should_registerWithTheRenamedPlatform() {
    // Arrange
    tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Setting rename = new Setting(PLATFORM_NAME.key(), "Renamed by the default tenant");
          rename.setTenant(tenantRepository.findById(Tenant.DEFAULT_TENANT_UUID).orElseThrow());
          settingRepository.save(rename);
        });
    when(config.isConfigured()).thenReturn(true);
    when(client.register(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Map.of());

    // Act: the scheduled tick, with no scope
    xtmOneService.autoRegister();

    // Assert
    ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
    verify(client)
        .register(
            eq("openaev"),
            anyString(),
            name.capture(),
            any(),
            any(),
            any(),
            any(),
            eq("aev"),
            any());
    assertThat(name.getValue()).isEqualTo("Renamed by the default tenant");
  }
}
