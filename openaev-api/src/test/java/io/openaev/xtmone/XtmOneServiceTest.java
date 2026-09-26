package io.openaev.xtmone;

import static io.openaev.database.model.TenantSettingKeys.PLATFORM_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.ee.LicenseTypeEnum;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.settings.TenantSettingsService;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One Service tests")
class XtmOneServiceTest {

  @Mock private XtmOneConfig config;
  @Mock private XtmOneClient client;
  @Mock private PlatformSettingsService platformSettingsService;
  @Mock private TenantSettingsService tenantSettingsService;
  @Mock private EnterpriseEditionService eeService;
  @Mock private XtmOneEntitlementService entitlementService;
  @Mock private LicenseCacheManager licenseCacheManager;

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
    verifyNoInteractions(entitlementService);
  }

  @Test
  @DisplayName("Given a registration answer should hand it to the XTM One entitlement")
  void given_registrationAnswer_should_handItToTheEntitlement() {
    // -- ARRANGE --
    arrangeConfigured("OpenAEV");
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    Map<String, Object> answer = Map.of("ee_enabled", true);
    when(platformSettingsService.findInstanceCreationDate()).thenReturn(Optional.of(createdAt));
    when(client.register(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer);

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    verify(entitlementService)
        .onRegistrationAnswer(answer, "platform-instance-id", createdAt, false);
    verifyNoInteractions(licenseCacheManager);
  }

  @Test
  @DisplayName("Given the XTM license in force changed should refresh the Enterprise Edition")
  void given_xtmLicenseChanged_should_refreshEnterpriseEdition() {
    // -- ARRANGE --
    arrangeConfigured("OpenAEV");
    Map<String, Object> answer = Map.of("xtm_license_pem", "pem");
    when(client.register(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer);
    when(entitlementService.onRegistrationAnswer(eq(answer), anyString(), any(), eq(false)))
        .thenReturn(true);

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    verify(licenseCacheManager).refreshAndNotify();
  }

  @Test
  @DisplayName("Given a registration should run outside any transaction")
  void given_registration_should_runOutsideAnyTransaction() throws NoSuchMethodException {
    // -- ARRANGE --
    Method autoRegister = XtmOneService.class.getMethod("autoRegister");

    // -- ASSERT --
    // The HTTP call must not hold a database connection, and the LicenseRefreshedEvent listeners
    // write: a read-only transaction around the refresh silently drops what they write.
    assertFalse(AnnotatedElementUtils.hasAnnotation(autoRegister, Transactional.class));
    assertFalse(AnnotatedElementUtils.hasAnnotation(XtmOneService.class, Transactional.class));
  }

  @Test
  @DisplayName("Given a validated own license should tell the XTM One entitlement about it")
  void given_validatedOwnLicense_should_tellTheEntitlement() {
    // -- ARRANGE --
    arrangeConfigured("OpenAEV");
    License license = new License();
    license.setLicenseValidated(true);
    license.setType(LicenseTypeEnum.standard);
    when(eeService.getEnterpriseEditionInfo()).thenReturn(license);
    Map<String, Object> answer = Map.of("ee_enabled", true);
    when(client.register(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(answer);

    // -- ACT --
    xtmOneService.autoRegister();

    // -- ASSERT --
    verify(entitlementService).onRegistrationAnswer(answer, "platform-instance-id", null, true);
  }
}
