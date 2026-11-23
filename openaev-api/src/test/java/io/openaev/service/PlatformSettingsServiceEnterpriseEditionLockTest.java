package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.repository.SettingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.ee.License;
import io.openaev.ee.LicenseSource;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.settings.form.SettingsEnterpriseEditionUpdateInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformSettingsService: the Enterprise Edition granted by XTM One")
class PlatformSettingsServiceEnterpriseEditionLockTest {

  @Mock private SettingRepository settingRepository;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private LicenseCacheManager licenseCacheManager;

  @InjectMocks private PlatformSettingsService platformSettingsService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        platformSettingsService, "licenseCacheManager", licenseCacheManager);
    License xtmOneLicense = new License();
    xtmOneLicense.setSource(LicenseSource.xtm_one);
    lenient().when(licenseCacheManager.getEnterpriseEditionInfo()).thenReturn(xtmOneLicense);
  }

  private static SettingsEnterpriseEditionUpdateInput input(String pem) {
    SettingsEnterpriseEditionUpdateInput input = new SettingsEnterpriseEditionUpdateInput();
    input.setEnterpriseEdition(pem);
    return input;
  }

  @ParameterizedTest(name = "[{0}]")
  @NullSource
  @ValueSource(strings = {"", "  "})
  @DisplayName("Given no license should refuse to disable it and save nothing")
  void given_noLicense_should_refuseToDisable(String pem) {
    assertThatThrownBy(() -> platformSettingsService.updateSettingsEnterpriseEdition(input(pem)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("cannot be disabled in this platform");
    verify(settingRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Given an own license should still verify it for installation")
  void given_ownLicense_should_verifyIt() throws Exception {
    License invalid = new License();
    when(enterpriseEditionService.verifyCertificate(anyString())).thenReturn(invalid);

    assertThatThrownBy(() -> platformSettingsService.updateSettingsEnterpriseEdition(input("pem")))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid certificate");
    verify(enterpriseEditionService).verifyCertificate("pem");
  }
}
