package io.openaev.xtmone;

import static io.openaev.database.model.TenantSettingKeys.PLATFORM_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.settings.TenantSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Service tests")
class XtmOneServiceTest {

  @Mock private XtmOneConfig config;
  @Mock private XtmOneClient client;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private TenantSettingsService tenantSettingsService;
  @Mock private EnterpriseEditionService eeService;

  @InjectMocks private XtmOneService xtmOneService;

  private PlatformSettings stubPlatformSettings() {
    PlatformSettings settings = new PlatformSettings();
    settings.setPlatformBaseUrl("https://openaev.example.com");
    settings.setPlatformId("platform-instance-id");
    // Stale platform-level (tenant-null) name the bug used to send; it must never be used now.
    settings.setPlatformName("Stale platform-level name (must be ignored)");
    return settings;
  }

  private void arrangeConfigured(String resolvedName) {
    when(config.isConfigured()).thenReturn(true);
    when(platformSettingsService.findSettings()).thenReturn(stubPlatformSettings());
    when(platformSettingsService.getPlatformVersion()).thenReturn("1.0.0");
    when(tenantSettingsService.resolveSettingValue(anyString(), eq(PLATFORM_NAME)))
        .thenReturn(resolvedName);
  }

  @Test
  @DisplayName("Given a tenant-scoped rename should register with the renamed platform name")
  void given_tenantScopedRename_should_registerWithRenamedName() {
    // -- ARRANGE --
    String renamed = "Filigran Adversarial Exposure Validation Platform";
    arrangeConfigured(renamed);

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
    verify(client)
        .register(
            eq("openaev"),
            anyString(),
            nameCaptor.capture(),
            anyString(),
            anyString(),
            any(),
            any(),
            eq("aev"),
            any());
    assertEquals(renamed, nameCaptor.getValue());
    // The tenant-aware resolution is scoped to the default tenant on the background tick.
    verify(tenantSettingsService).resolveSettingValue(Tenant.DEFAULT_TENANT_UUID, PLATFORM_NAME);
  }

  @Test
  @DisplayName("Given a blank resolved name should fall back to a sensible default")
  void given_blankResolvedName_should_useFallback() {
    // -- ARRANGE --
    arrangeConfigured("  ");

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
    verify(client)
        .register(
            eq("openaev"),
            anyString(),
            nameCaptor.capture(),
            anyString(),
            anyString(),
            any(),
            any(),
            eq("aev"),
            any());
    assertEquals(PLATFORM_NAME.defaultValue(), nameCaptor.getValue());
  }

  @Test
  @DisplayName("Given XTM One is not configured should not register")
  void given_notConfigured_should_notRegister() {
    // -- ARRANGE --
    when(config.isConfigured()).thenReturn(false);

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    verifyNoInteractions(client);
  }
}
